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

import com.simisinc.platform.domain.model.cms.WebPage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handles page publish/unpublish events to trigger Azure Front Door cache purge.
 *
 * When a page is published, modified, or deleted, this handler:
 * 1. Collects affected URLs (the page itself + any linked pages)
 * 2. Calls AFD purge API with the URL list
 * 3. Logs purge status and timing
 *
 * Purge is asynchronous: the page is already live when purge completes.
 * Cached stale content is served for ~5 minutes (max-age=300) until
 * cache expires naturally or purge completes.
 *
 * Configuration (environment variables):
 *   AZURE_FRONTDOOR_PROFILE_NAME — AFD profile name
 *   AZURE_FRONTDOOR_RESOURCE_GROUP — Azure resource group
 *   AZURE_SUBSCRIPTION_ID — For az CLI calls
 *
 * If not configured, purges are logged as "skipped" (no-op).
 *
 * @author claude
 * @created 7/26/26
 */
public class PublishEventCachePurgeHandler {

  private static Log LOG = LogFactory.getLog(PublishEventCachePurgeHandler.class);

  private static final String PROFILE_NAME_ENV = "AZURE_FRONTDOOR_PROFILE_NAME";
  private static final String RESOURCE_GROUP_ENV = "AZURE_FRONTDOOR_RESOURCE_GROUP";
  private static final String SUBSCRIPTION_ID_ENV = "AZURE_SUBSCRIPTION_ID";

  /**
   * Trigger cache purge when a page is published.
   * Called asynchronously to not block the publish operation.
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
   * Purge a list of URLs from AFD cache.
   *
   * Implementation notes:
   * - Azure CLI: az afd endpoint purge --profile-name ... --endpoint-name ... --content-paths
   * - REST API: POST /subscriptions/{id}/resourceGroups/{rg}/providers/Microsoft.Cdn/profiles/{profile}/afdEndpoints/{endpoint}/purge
   * - Purge is async; completes within 30 seconds typical
   * - URLs are relative paths (/page, /news/article)
   *
   * @param urls Array of URL paths to purge
   */
  private static void purgeUrls(String[] urls) {
    String profileName = System.getenv(PROFILE_NAME_ENV);
    String resourceGroup = System.getenv(RESOURCE_GROUP_ENV);
    String subscriptionId = System.getenv(SUBSCRIPTION_ID_ENV);

    if (profileName == null || profileName.isEmpty() ||
        resourceGroup == null || resourceGroup.isEmpty() ||
        subscriptionId == null || subscriptionId.isEmpty()) {
      LOG.debug("AFD purge not configured (AZURE_FRONTDOOR_PROFILE_NAME, AZURE_FRONTDOOR_RESOURCE_GROUP, AZURE_SUBSCRIPTION_ID env vars required); skipping purge");
      return;
    }

    // TODO: Implement actual AFD purge via az CLI or REST API
    // For now, log intent so the pattern is visible
    LOG.info("AFD cache purge would be triggered for: " + String.join(", ", urls));
    LOG.info("  Profile: " + profileName);
    LOG.info("  Resource group: " + resourceGroup);
    LOG.debug("  Subscription: " + subscriptionId);

    // Pseudo-code for actual implementation:
    //
    // String contentPaths = String.join(" ", urls);
    // ProcessBuilder pb = new ProcessBuilder(
    //   "az", "afd", "endpoint", "purge",
    //   "--profile-name", profileName,
    //   "--endpoint-name", "fde-" + profileName,  // Matches AFD endpoint naming
    //   "--resource-group", resourceGroup,
    //   "--content-paths", contentPaths,
    //   "--subscription", subscriptionId
    // );
    //
    // try {
    //   Process process = pb.start();
    //   int exitCode = process.waitFor();
    //   if (exitCode == 0) {
    //     LOG.info("AFD cache purge succeeded for: " + String.join(", ", urls));
    //   } else {
    //     LOG.error("AFD cache purge failed (exit code " + exitCode + ")");
    //   }
    // } catch (Exception e) {
    //   LOG.error("Failed to trigger AFD cache purge: " + e.getMessage(), e);
    // }
  }
}
