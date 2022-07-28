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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.granule.utils.HttpHeaders;
import com.simisinc.platform.application.cms.LoadStylesheetCommand;
import com.simisinc.platform.domain.model.cms.Stylesheet;

/**
 * Handles all web browser page requests
 *
 * @author matt rajkowski
 * @created 4/6/18 9:22 AM
 */

public class StylesheetServlet extends HttpServlet {

  private static final long serialVersionUID = -371092409070142705L;
  private static Log LOG = LogFactory.getLog(StylesheetServlet.class);
  private static String cssPathPart = "/css/custom/stylesheet";

  public void init(ServletConfig config) throws ServletException {
    LOG.info("StylesheetServlet starting up...");
  }

  public void destroy() {

  }

  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String contextPath = request.getServletContext().getContextPath();
    String requestURI = request.getRequestURI();
    String pagePath = requestURI.substring(contextPath.length());
    LOG.debug("Using resource: " + pagePath);

    // /css/stylesheet.css?v=test
    // /css/stylesheet123.css?v=test

    int startIdx = pagePath.indexOf(cssPathPart);
    int endIdx = pagePath.indexOf(".css", startIdx);
    if (startIdx == -1 || endIdx == -1) {
      response.setStatus(404);
      return;
    }

    long webPageId = -1L;
    if (endIdx > startIdx + cssPathPart.length()) {
      String webPageIdValue = pagePath.substring(startIdx + cssPathPart.length(), endIdx);
      LOG.debug("Using webPageIdValue: " + webPageIdValue);
      if (StringUtils.isNumeric(webPageIdValue)) {
        webPageId = Long.parseLong(webPageIdValue);
      } else {
        response.setStatus(404);
        return;
      }
    }

    // Use the latest stylesheet info... (either use invalidating cache, or must load each time)
    Stylesheet stylesheet = LoadStylesheetCommand.loadStylesheetByWebPageId(webPageId);
    if (stylesheet == null) {
      LOG.debug("Stylesheet not found for pagePath: " + pagePath);
      response.setStatus(404);
      return;
    }
    String css = stylesheet.getCss();

    // Check for a last-modified header and return 304 if possible
    long lastModified = stylesheet.getModified().getTime();
    if (lastModified > 0) {
      long headerValue = request.getDateHeader("If-Modified-Since");
      if (lastModified <= headerValue + 1000) {
        LOG.debug("Stylesheet not modified, use cache");
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return;
      }
    }

    // Set headers and send the content
    LOG.debug("Sending stylesheet for pagePath: " + pagePath + " [" + webPageId + "]");
    response.setHeader("Content-Type", "text/css; charset=utf-8");
    if (lastModified > 0) {
      response.setDateHeader("Last-Modified", lastModified);
    } else {
      HttpHeaders.setCacheExpireDate(response, 6048000);
    }
    LOG.debug("CSS original length: " + css.length());
    if (gzipSupported(request)) {
      try (OutputStream os = response.getOutputStream()) {
        byte[] gzipCss = gzip(css);
        response.setHeader("ETag", DigestUtils.md5Hex(gzipCss));
        response.setHeader("Content-Encoding", "gzip");
        os.write(gzipCss);
        os.flush();
        LOG.debug("Sent as gzip... gzip length: " + gzipCss.length);
      }
    } else {
      response.setHeader("ETag", DigestUtils.md5Hex(css));
      response.getWriter().print(css);
    }
  }

  private static boolean gzipSupported(HttpServletRequest request) {
    String acceptEncoding = request.getHeader("Accept-Encoding");
    if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
      return false;
    }
    String userAgent = request.getHeader("user-agent");
    if (userAgent == null) {
      return false;
    }
    if (userAgent.contains("MSIE 6") && !userAgent.contains("SV1")) {
      return false;
    }
    return true;
  }

  private static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (
        GZIPOutputStream gzipStream = new GZIPOutputStream(bos)) {
      gzipStream.write(text.getBytes("UTF-8"));
      gzipStream.flush();
    }
    return bos.toByteArray();
  }
}
