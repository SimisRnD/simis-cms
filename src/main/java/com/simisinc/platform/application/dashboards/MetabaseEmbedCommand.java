/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application.dashboards;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;

/**
 * Builds a signed static-embed iframe URL for a Metabase dashboard.
 * <p>
 * Unlike Superset's guest-token flow (a login call, then a separate guest-token call, both
 * against Superset's own API), Metabase's static embedding needs no round trip to Metabase at
 * all: the embedding secret key signs a JWT locally, and the token is dropped straight into the
 * iframe URL. See https://www.metabase.com/docs/latest/embedding/static-embedding.
 *
 * @author elizabeth houser
 */
public class MetabaseEmbedCommand {

  private static Log LOG = LogFactory.getLog(MetabaseEmbedCommand.class);

  private static final String JWT_HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
  private static final long TOKEN_TTL_SECONDS = 600; // matches Metabase's own sample apps

  public static String generateDashboardIframeUrl(String dashboardId, String hashParameters) {
    if (StringUtils.isBlank(dashboardId)) {
      LOG.debug("Missing dashboardId");
      return null;
    }

    boolean enabled = LoadSitePropertyCommand.loadByNameAsBoolean("bi.metabase.enabled");
    if (!enabled) {
      LOG.debug("Metabase BI is not enabled");
      return null;
    }

    String serverUrl = LoadSitePropertyCommand.loadByName("bi.metabase.url");
    String secret = LoadSitePropertyCommand.loadByName("bi.metabase.secret");
    if (StringUtils.isAnyBlank(serverUrl, secret)) {
      LOG.error("Metabase url/secret is not configured");
      return null;
    }

    String token = sign(dashboardId, secret);
    if (token == null) {
      return null;
    }

    String url = StringUtils.stripEnd(serverUrl, "/") + "/embed/dashboard/" + token;
    if (StringUtils.isNotBlank(hashParameters)) {
      url += "#" + hashParameters;
    }
    return url;
  }

  private static String sign(String dashboardId, String secret) {
    try {
      Map<String, Object> resource = new HashMap<>();
      resource.put("dashboard", dashboardId);

      Map<String, Object> payload = new HashMap<>();
      payload.put("resource", resource);
      payload.put("params", Collections.emptyMap());
      payload.put("exp", (System.currentTimeMillis() / 1000) + TOKEN_TTL_SECONDS);
      String payloadJson = JsonCommand.createJsonNode(payload).toString();

      String signingInput = base64UrlEncode(JWT_HEADER_JSON) + "." + base64UrlEncode(payloadJson);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));

      return signingInput + "." + base64UrlEncode(signature);
    } catch (Exception e) {
      LOG.error("Could not sign the Metabase embed token", e);
      return null;
    }
  }

  private static String base64UrlEncode(String value) {
    return base64UrlEncode(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String base64UrlEncode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
