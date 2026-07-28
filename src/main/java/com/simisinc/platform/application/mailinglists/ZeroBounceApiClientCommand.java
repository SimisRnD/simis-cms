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

package com.simisinc.platform.application.mailinglists;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;

/**
 * Real-time, single-address email deliverability validation with ZeroBounce (v2 API).
 * <p>
 * On a successful call the result is persisted via {@link EmailRepository#markValidated}, so
 * callers do not need to handle storage themselves -- they only need the returned JsonNode if
 * they want to inspect the full ZeroBounce response (free_email, did_you_mean, etc.).
 *
 * @author SimIS Inc.
 * @created 7/28/26
 */
public class ZeroBounceApiClientCommand {

  private static Log LOG = LogFactory.getLog(ZeroBounceApiClientCommand.class);

  private static String BASE_URL = "https://api.zerobounce.net/v2/validate";

  /**
   * Validates a single email address with ZeroBounce and records the classification on the
   * emails record (validation_status/validation_sub_status/validated_at).
   *
   * @param emailAddress the email to validate; must already be persisted (a valid id is required
   *                      to store the result)
   * @return the ZeroBounce response as JSON, or null if the API is not configured, the address is
   *         invalid, or the call failed for any reason (never throws)
   */
  public static JsonNode validateEmail(Email emailAddress) {
    if (emailAddress == null || StringUtils.isBlank(emailAddress.getEmail()) || emailAddress.getId() == -1) {
      return null;
    }

    String apiKey = getApiKey();
    if (apiKey == null) {
      return null;
    }

    try {
      // GET https://api.zerobounce.net/v2/validate?email=...&api_key=...&ip_address=...
      StringBuilder url = new StringBuilder(BASE_URL)
          .append("?email=").append(URLEncoder.encode(emailAddress.getEmail().trim(), StandardCharsets.UTF_8))
          .append("&api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
      if (StringUtils.isNotBlank(emailAddress.getIpAddress())) {
        url.append("&ip_address=").append(URLEncoder.encode(emailAddress.getIpAddress().trim(), StandardCharsets.UTF_8));
      }

      Map<String, String> headers = new HashMap<>();
      headers.put("Accept", "application/json");

      String remoteContent = HttpGetCommand.execute(url.toString(), headers);

      // Check for content
      if (StringUtils.isBlank(remoteContent)) {
        LOG.debug("HttpGet Remote content is empty");
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      JsonNode json = JsonLoader.fromString(remoteContent);

      // Errors have no "status" field at all: {"error": "Invalid API Key or your account ran out of credits"}
      if (json.has("error")) {
        LOG.warn("ZeroBounce returned an error: " + json.get("error").asText());
        return null;
      }
      if (!json.has("status")) {
        LOG.warn("ZeroBounce response did not include a status");
        return null;
      }

      // Persist the classification result. Guard against JsonNode's well-known asText() gotcha:
      // a JSON null value (as opposed to an absent key) is a NullNode, and NullNode.asText()
      // returns the literal string "null" rather than a real null -- checked explicitly here so
      // an absent, blank, or JSON-null sub_status all consistently persist as a real SQL NULL.
      String status = json.get("status").asText();
      JsonNode subStatusNode = json.get("sub_status");
      String subStatus = (subStatusNode != null && !subStatusNode.isNull()) ? subStatusNode.asText() : null;
      EmailRepository.markValidated(emailAddress, status, subStatus);

      return json;
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad key, communication issue
      LOG.warn("ZeroBounce validateEmail issue: " + e.getMessage());
    }
    return null;
  }

  private static String getApiKey() {
    String apiKey = LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey");
    if (StringUtils.isBlank(apiKey)) {
      LOG.debug("ZeroBounce API is not configured");
      return null;
    }
    return apiKey;
  }
}
