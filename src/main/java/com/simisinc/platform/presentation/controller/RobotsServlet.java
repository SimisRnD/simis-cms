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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class RobotsServlet extends HttpServlet {

  private static final Log LOG = LogFactory.getLog(RobotsServlet.class);

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/plain;charset=UTF-8");
    response.setHeader("Cache-Control", "public, max-age=86400");

    try {
      String robotsContent = loadRobotsTxt();
      if (StringUtils.isBlank(robotsContent)) {
        robotsContent = generateDefaultRobotsTxt();
      }

      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().print(robotsContent);
    } catch (Exception e) {
      LOG.error("Error serving robots.txt: " + e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().print("# Error generating robots.txt\n");
    }
  }

  private String loadRobotsTxt() {
    try {
      String configPath = System.getProperty("cms.path");
      if (StringUtils.isBlank(configPath)) {
        configPath = System.getProperty("user.dir");
      }

      File robotsFile = new File(configPath, "config/cms/robots.txt");
      if (robotsFile.exists() && robotsFile.canRead()) {
        LOG.debug("Loading custom robots.txt from: " + robotsFile.getAbsolutePath());
        return new String(Files.readAllBytes(robotsFile.toPath()));
      }
    } catch (Exception e) {
      LOG.warn("Error loading custom robots.txt file: " + e.getMessage());
    }
    return null;
  }

  private String generateDefaultRobotsTxt() {
    StringBuilder sb = new StringBuilder();

    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    Map<String, String> robotsPropertyMap = LoadSitePropertyCommand.loadAsMap("robots");

    // Default rules for all crawlers
    sb.append("User-Agent: *\n");
    sb.append("Allow: /\n");
    sb.append("Disallow: /admin/\n");
    sb.append("Disallow: /action/\n");
    sb.append("Disallow: /admin\n");

    // AI training crawler opt-outs (configurable via site properties)
    addAiCrawlerRules(sb, robotsPropertyMap, "gptbot", "GPTBot");
    addAiCrawlerRules(sb, robotsPropertyMap, "claudebot", "ClaudeBot");
    addAiCrawlerRules(sb, robotsPropertyMap, "google-extended", "Google-Extended");
    addAiCrawlerRules(sb, robotsPropertyMap, "perplexitybot", "PerplexityBot");
    addAiCrawlerRules(sb, robotsPropertyMap, "ccbot", "CCBot");

    sb.append("\n");

    // Sitemap reference
    if (StringUtils.isNotBlank(siteUrl)) {
      sb.append("Sitemap: ").append(siteUrl).append("/sitemap.xml\n");
    }

    return sb.toString();
  }

  private void addAiCrawlerRules(StringBuilder sb, Map<String, String> sitePropertyMap,
                                  String propertyKey, String userAgent) {
    String propKey = "robots.ai." + propertyKey;
    String allowValue = sitePropertyMap.get(propKey);

    // Default: allow all crawlers (consistent with current behavior)
    if (StringUtils.isNotBlank(allowValue) && "false".equalsIgnoreCase(allowValue.trim())) {
      sb.append("\nUser-Agent: ").append(userAgent).append("\n");
      sb.append("Disallow: /\n");
    }
  }
}
