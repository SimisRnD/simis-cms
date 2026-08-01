/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.infrastructure.cache;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.domain.model.cms.WebPage;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handles page publish/unpublish events to trigger Azure Front Door cache purge.
 *
 * When a page is published, modified, or deleted, this handler:
 * 1. Collects affected URLs (the page itself)
 * 2. Calls the AFD purge REST API with the URL list
 * 3. Logs purge status
 *
 * Configuration (environment variables) -- all four are required, matching how the rest of this
 * milestone's Azure config is injected (e.g. APPLICATIONINSIGHTS_CONNECTION_STRING): plain env
 * vars, resolved from Key Vault by the App Service platform before the JVM ever starts, not read
 * by this application code:
 *   AZURE_FRONTDOOR_PROFILE_NAME   -- AFD profile name (infra/modules/frontdoor.bicep: afd-&lt;prefix&gt;)
 *   AZURE_FRONTDOOR_RESOURCE_GROUP -- Azure resource group
 *   AZURE_FRONTDOOR_ENDPOINT_NAME  -- AFD endpoint name (infra/modules/frontdoor.bicep: fde-&lt;prefix&gt;;
 *                                     NOT derivable from the profile name -- both are independently
 *                                     derived from the same namePrefix, so it needs its own var)
 *   AZURE_SUBSCRIPTION_ID          -- Subscription containing the AFD profile
 *
 * If any are missing, purge is skipped as a no-op (logged once at INFO, then DEBUG per call) --
 * exactly like the Application Insights Java agent self-disabling with no connection string set.
 * There is no live Azure subscription for this milestone yet, so that is the path every
 * deployment takes today; this class is written to become live the moment the four env vars are
 * set, with no code changes.
 *
 * Authentication uses azure-identity's DefaultAzureCredential (managed identity in Azure App
 * Service, falling back through the standard chain locally) -- the same trust model already
 * established for this milestone's Key Vault references (infra/modules/rbac.bicep grants the
 * App Service's system-assigned identity access to the vault and the registry). A CDN/Front Door
 * purge-capable role for that identity is NOT yet in rbac.bicep -- it must be added before a
 * purge call can succeed against a live subscription; see the #420 PR description.
 *
 * The purge call itself is a plain REST POST (Microsoft.Cdn profiles/afdEndpoints/purge), reusing
 * this class's own HTTP handling rather than the azure-resourcemanager-cdn fluent SDK -- that
 * management-plane client pulls in a disproportionate dependency tree (azure-core-management,
 * azure-resourcemanager-resources, etc.) for what is a single, stable, well-documented action
 * endpoint. azure-identity is used only for the bearer token.
 *
 * The call runs synchronously on the caller's thread (the publish/update/delete request), fully
 * isolated by try/catch so it can never fail or block that request. Today that is free: unless
 * AFD is configured, the skip path returns before this class touches the Azure SDK or the network
 * at all. If AFD purge is later enabled in production and its latency on the publish path becomes
 * noticeable, moving this call onto a background job (mirroring WorkflowEngineJob) is the
 * follow-up -- not built here to avoid introducing this codebase's first JobRunr-dependent test
 * for a path that cannot be exercised against a real endpoint yet.
 *
 * @created 7/26/26
 */
public class PublishEventCachePurgeHandler {

  private static Log LOG = LogFactory.getLog(PublishEventCachePurgeHandler.class);

  static final String PROFILE_NAME_ENV = "AZURE_FRONTDOOR_PROFILE_NAME";
  static final String RESOURCE_GROUP_ENV = "AZURE_FRONTDOOR_RESOURCE_GROUP";
  static final String ENDPOINT_NAME_ENV = "AZURE_FRONTDOOR_ENDPOINT_NAME";
  static final String SUBSCRIPTION_ID_ENV = "AZURE_SUBSCRIPTION_ID";

  private static final String ARM_SCOPE = "https://management.azure.com/.default";
  private static final String AFD_API_VERSION = "2024-02-01";
  private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

  // Built lazily, and only once all four env vars are confirmed present, so an unconfigured
  // deployment (every deployment today) never constructs a credential or touches the SDK.
  private static volatile TokenCredential credential;

  private static volatile boolean loggedNotConfigured = false;

  /**
   * Trigger cache purge when a page is published.
   *
   * @param webPage The page being published
   */
  public static void onPagePublished(WebPage webPage) {
    if (webPage == null || webPage.getLink() == null) {
      return;
    }

    String pageUrl = "/" + webPage.getLink().replaceAll("^/+", "");
    LOG.info("Page published: " + pageUrl + "; triggering cache purge...");

    purgeUrls(new String[]{pageUrl});
  }

  /**
   * Trigger cache purge when a page is updated (republished with changes).
   *
   * @param webPage The page being updated
   */
  public static void onPageUpdated(WebPage webPage) {
    if (webPage == null || webPage.getLink() == null) {
      return;
    }

    String pageUrl = "/" + webPage.getLink().replaceAll("^/+", "");
    LOG.info("Page updated: " + pageUrl + "; triggering cache purge...");

    purgeUrls(new String[]{pageUrl});
  }

  /**
   * Trigger cache purge when a page is deleted or unpublished.
   *
   * @param webPageLink The page URL path
   */
  public static void onPageDeleted(String webPageLink) {
    if (webPageLink == null || webPageLink.isEmpty()) {
      return;
    }

    String pageUrl = "/" + webPageLink.replaceAll("^/+", "");
    LOG.info("Page deleted: " + pageUrl + "; triggering cache purge...");

    purgeUrls(new String[]{pageUrl});
  }

  /**
   * Purge a list of URLs from the AFD cache. Never throws -- a purge failure or an unconfigured
   * AFD must never fail or block the publish/update/delete that triggered it.
   *
   * @param urls Array of URL paths to purge
   */
  static void purgeUrls(String[] urls) {
    if (urls == null || urls.length == 0) {
      return;
    }

    String profileName = StringUtils.trimToNull(System.getenv(PROFILE_NAME_ENV));
    String resourceGroup = StringUtils.trimToNull(System.getenv(RESOURCE_GROUP_ENV));
    String endpointName = StringUtils.trimToNull(System.getenv(ENDPOINT_NAME_ENV));
    String subscriptionId = StringUtils.trimToNull(System.getenv(SUBSCRIPTION_ID_ENV));

    if (profileName == null || resourceGroup == null || endpointName == null || subscriptionId == null) {
      logNotConfiguredOnce();
      return;
    }

    try {
      String accessToken = acquireAccessToken();
      if (accessToken == null) {
        LOG.warn("AFD cache purge skipped: could not acquire an Azure access token");
        return;
      }

      String requestUri = "https://management.azure.com/subscriptions/" + subscriptionId
          + "/resourceGroups/" + resourceGroup
          + "/providers/Microsoft.Cdn/profiles/" + profileName
          + "/afdEndpoints/" + endpointName
          + "/purge?api-version=" + AFD_API_VERSION;
      String requestBody = new ObjectMapper().writeValueAsString(Collections.singletonMap("contentPaths", urls));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(requestUri))
          .timeout(HTTP_TIMEOUT)
          .header("Authorization", "Bearer " + accessToken)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();
      HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        LOG.info("AFD cache purge accepted (status " + status + ") for: " + String.join(", ", urls));
      } else {
        LOG.warn("AFD cache purge request failed (status " + status + "): " + response.body());
      }
    } catch (Exception e) {
      // A purge failure must never fail or block the publish that triggered it.
      LOG.warn("AFD cache purge failed, continuing without purging: " + e.getMessage(), e);
    }
  }

  private static void logNotConfiguredOnce() {
    if (!loggedNotConfigured) {
      loggedNotConfigured = true;
      LOG.info("AFD purge is not configured (" + PROFILE_NAME_ENV + ", " + RESOURCE_GROUP_ENV + ", "
          + ENDPOINT_NAME_ENV + ", and " + SUBSCRIPTION_ID_ENV + " env vars are all required); "
          + "cache purge on publish is disabled. Published pages rely on CacheStrategy's natural "
          + "cache expiry (max-age) instead, until AFD is configured.");
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("AFD purge not configured; skipping purge");
    }
  }

  /**
   * Acquire a management-plane bearer token via DefaultAzureCredential. Returns null (never
   * throws) if a credential cannot be built or a token cannot be acquired within the timeout --
   * the caller treats that exactly like a failed purge call.
   */
  private static String acquireAccessToken() {
    try {
      TokenCredential cred = credential;
      if (cred == null) {
        synchronized (PublishEventCachePurgeHandler.class) {
          cred = credential;
          if (cred == null) {
            cred = new DefaultAzureCredentialBuilder().build();
            credential = cred;
          }
        }
      }
      TokenRequestContext context = new TokenRequestContext().addScopes(ARM_SCOPE);
      AccessToken accessToken = cred.getToken(context).block(TOKEN_TIMEOUT);
      return accessToken != null ? accessToken.getToken() : null;
    } catch (Exception e) {
      LOG.warn("Could not acquire an Azure access token for AFD purge: " + e.getMessage());
      return null;
    }
  }
}
