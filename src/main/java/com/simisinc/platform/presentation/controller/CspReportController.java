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

package com.simisinc.platform.presentation.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.CspPolicyCommand;
import com.simisinc.platform.application.cms.CspViolationReportCommand;
import com.simisinc.platform.infrastructure.persistence.cms.CspViolationRepository;

/**
 * Receives Content-Security-Policy violation reports from browsers.
 *
 * <p>
 * This endpoint is necessarily unauthenticated: a browser posts a violation report on its own, with
 * no credentials and no session, and there is no way to ask it for any. Everything here is built
 * around that. The body is size-capped before it is read, the payload is reduced to a (directive,
 * host) pair so repeated posts count up instead of adding rows, the repository caps how many
 * distinct pairs can ever exist, and nothing is accepted at all unless an administrator has
 * configured a candidate policy to test.
 * </p>
 *
 * <p>
 * It always answers 204, whatever happened. A violation report is fire-and-forget -- no browser
 * reads the response or retries -- so a status code here communicates only to whoever is probing
 * the endpoint. Answering the same way regardless means it cannot be used to find out whether
 * reporting is switched on, what the cap is, or whether a particular host is already known.
 * </p>
 *
 * @author SimIS Inc.
 */
@WebServlet(name = "CspReport", urlPatterns = "/csp-report")
public class CspReportController extends HttpServlet {

  private static Log LOG = LogFactory.getLog(CspReportController.class);
  private static ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Always 204, decided before anything else can change it
    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    try {
      if (!CspPolicyCommand.isReportingEnabled()) {
        return;
      }
      // Check the declared length before reading, then cap the read itself: Content-Length is a
      // claim by the caller, and a chunked request has none at all
      int declaredLength = request.getContentLength();
      if (declaredLength > CspViolationReportCommand.MAX_REPORT_BYTES) {
        LOG.debug("Ignoring an oversized CSP report: " + declaredLength + " bytes declared");
        return;
      }
      String body = readCapped(request);
      if (body == null || body.isEmpty()) {
        return;
      }
      JsonNode payload = objectMapper.readTree(body);
      List<CspViolationReportCommand.Violation> violations = CspViolationReportCommand.parse(payload);
      for (CspViolationReportCommand.Violation violation : violations) {
        CspViolationRepository.save(violation.getEffectiveDirective(), violation.getBlockedHost(),
            violation.getDocumentPath());
      }
    } catch (Exception e) {
      // A malformed or hostile body is expected on an open endpoint and is not worth an error line
      LOG.debug("Could not process a CSP report: " + e.getMessage());
    }
  }

  /** Reads at most MAX_REPORT_BYTES, whatever the caller claimed the length was. */
  private String readCapped(HttpServletRequest request) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      char[] buffer = new char[4096];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        if (sb.length() + read > CspViolationReportCommand.MAX_REPORT_BYTES) {
          LOG.debug("Ignoring a CSP report that exceeded the size cap while being read");
          return null;
        }
        sb.append(buffer, 0, read);
      }
    }
    return sb.toString();
  }
}
