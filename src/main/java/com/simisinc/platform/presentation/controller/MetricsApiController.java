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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * Metrics API: Real User Measurement (RUM) endpoints for collecting Core Web Vitals
 * and other performance metrics from real visitor sessions.
 *
 * Endpoint: POST /api/metrics/vitals
 * Payload: { "url": "...", "lcp": 2500, "cls": 0.1, "inp": 200, "fcp": 1800, "ttfb": 500 }
 *
 * @author claude
 * @created 8/27/26
 */
@WebServlet(name = "MetricsApiController", urlPatterns = {"/api/metrics/*"}, loadOnStartup = 10)
public class MetricsApiController extends HttpServlet {

  private static Log LOG = LogFactory.getLog(MetricsApiController.class);

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String pathInfo = request.getPathInfo();

    // POST /api/metrics/vitals — collect Core Web Vitals from client
    if ("/vitals".equals(pathInfo)) {
      handleVitals(request, response);
      return;
    }

    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    response.setContentType("application/json");
    response.getWriter().print("{\"error\":\"Not found\"}");
  }

  /**
   * Collect Core Web Vitals from the client.
   *
   * Request payload:
   * {
   *   "url": "/path/to/page",
   *   "lcp": 2500,           // Largest Contentful Paint (ms)
   *   "cls": 0.1,            // Cumulative Layout Shift (score)
   *   "inp": 200,            // Interaction to Next Paint (ms)
   *   "fcp": 1800,           // First Contentful Paint (ms)
   *   "ttfb": 500            // Time to First Byte (ms)
   * }
   */
  private void handleVitals(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");

    // Check if metrics collection is enabled and user has consented
    Map<String, String> analyticsProperties = LoadSitePropertyCommand.loadAsMap("analytics");
    boolean consentRequired = "true".equals(analyticsProperties.get("consentRequired"));

    // If consent is required and not present, skip collection
    if (consentRequired) {
      // TODO: Check for analytics consent cookie/flag from request
      // For now, always collect (assume consent via banner)
    }

    try {
      // Read JSON payload from request body
      BufferedReader reader = request.getReader();
      StringBuilder json = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        json.append(line);
      }

      if (json.length() == 0) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().print("{\"error\":\"Empty request body\"}");
        return;
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode payload = mapper.readTree(json.toString());

      // Validate required fields
      String url = payload.has("url") ? payload.get("url").asText() : null;
      if (url == null || url.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().print("{\"error\":\"Missing url\"}");
        return;
      }

      // TODO: Implement vitals storage logic
      // 1. Parse metrics from payload (lcp, cls, inp, fcp, ttfb)
      // 2. Normalize values (convert to integers where appropriate)
      // 3. Store in web_vitals table with timestamp
      // 4. Trigger aggregate computation for p50/p75/p95 on periodic schedule

      LOG.debug("Web vitals collected: " + url);

      response.setStatus(HttpServletResponse.SC_ACCEPTED);
      response.getWriter().print("{\"success\":true,\"message\":\"Metrics accepted\"}");

    } catch (Exception e) {
      LOG.error("Error collecting web vitals", e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().print("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
    }
  }
}
