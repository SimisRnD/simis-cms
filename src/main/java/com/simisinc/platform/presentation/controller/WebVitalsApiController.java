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

package com.simisinc.platform.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.WebVitalsCollector;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.BufferedReader;

/**
 * API endpoint for receiving Core Web Vitals metrics from client-side collectors.
 *
 * Accepts POST requests with JSON payload containing real user metrics:
 *   {
 *     "url": "/news/article",
 *     "metrics": {
 *       "LCP": {"value": 2500, "rating": "good"},
 *       "CLS": {"value": 0.1, "rating": "good"},
 *       "INP": {"value": 150, "rating": "good"},
 *       "FCP": {"value": 1200, "rating": "good"},
 *       "TTFB": {"value": 400, "rating": "good"}
 *     }
 *   }
 *
 * Metrics that are undefined (user left page before metric finalized) are omitted.
 * Stores metrics server-side for trend analysis and admin dashboard display.
 *
 * @author claude
 * @created 7/26/26
 */
@WebServlet(name = "WebVitalsApi", urlPatterns = "/api/metrics/vitals")
public class WebVitalsApiController extends HttpServlet {

  private static Log LOG = LogFactory.getLog(WebVitalsApiController.class);
  private static ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    // CORS headers (allow cross-origin requests from the same site)
    response.setHeader("Access-Control-Allow-Origin", "same-origin");
    response.setHeader("Access-Control-Allow-Methods", "POST");
    response.setContentType("application/json");

    try {
      // Parse JSON payload from request body
      BufferedReader reader = request.getReader();
      JsonNode payload = objectMapper.readTree(reader);

      String url = payload.path("url").asText(null);
      JsonNode metricsNode = payload.path("metrics");

      if (url == null || url.isEmpty() || metricsNode.isMissingNode()) {
        response.setStatus(400);
        response.getWriter().write("{\"error\":\"missing url or metrics\"}");
        return;
      }

      // Extract and store each metric
      String sessionId = request.getSession(false) != null ?
          request.getSession().getId() : null;

      // Resolve the CMS page this metric belongs to, if the URL matches one
      String path = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
      WebPage page = WebPageRepository.findByLink(path);
      Long webPageId = page != null ? page.getId() : null;

      // Capture request context (no raw user agent is stored, only its hash).
      // Viewport/connection come from the JSON body, not headers: the client uses
      // navigator.sendBeacon() for reliable delivery, which cannot set custom headers.
      String userAgentRaw = request.getHeader("User-Agent");
      String userAgentHash = userAgentRaw != null ? DigestUtils.sha256Hex(userAgentRaw) : null;
      Integer viewportWidth = payload.path("viewportWidth").isMissingNode() || payload.path("viewportWidth").isNull()
          ? null : payload.path("viewportWidth").asInt();
      String connectionType = payload.path("connectionType").isNull() ? null : payload.path("connectionType").asText(null);

      WebVitalsCollector.collectMetrics(url, metricsNode, sessionId, webPageId, userAgentHash, viewportWidth, connectionType);

      LOG.debug("Stored web vitals for: " + url);

      // Return 204 No Content (success, no response body)
      response.setStatus(204);

    } catch (Exception e) {
      LOG.error("Error processing web vitals: " + e.getMessage(), e);
      response.setStatus(500);
      response.getWriter().write("{\"error\":\"failed to store metrics\"}");
    }
  }

  @Override
  protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
    // Pre-flight CORS request
    response.setHeader("Access-Control-Allow-Origin", "same-origin");
    response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    response.setStatus(200);
  }
}
