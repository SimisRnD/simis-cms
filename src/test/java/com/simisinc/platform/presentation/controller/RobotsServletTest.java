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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author SimIS Inc.
 */
class RobotsServletTest {

  private final String originalCmsPath = System.getProperty("cms.path");

  @AfterEach
  void restoreCmsPath() {
    if (originalCmsPath == null) {
      System.clearProperty("cms.path");
    } else {
      System.setProperty("cms.path", originalCmsPath);
    }
  }

  private String runDoGet(Map<String, String> siteProperties) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("robots")).thenReturn(siteProperties);
      new RobotsServlet().doGet(request, response);
    }

    return body.toString();
  }

  @Test
  void doGetReturns200WithPlainTextContentType() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("robots")).thenReturn(new HashMap<>());
      new RobotsServlet().doGet(request, response);
    }

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_OK);
    org.mockito.Mockito.verify(response).setContentType("text/plain;charset=UTF-8");
  }

  @Test
  void doGetDisallowsAdminByDefaultAndAllowsEveryCrawler() throws Exception {
    String body = runDoGet(new HashMap<>());

    assertTrue(body.contains("Disallow: /admin/"));
    assertFalse(body.contains("User-Agent: GPTBot"));
    assertFalse(body.contains("User-Agent: OAI-SearchBot"));
    assertFalse(body.contains("User-Agent: ChatGPT-User"));
    assertFalse(body.contains("User-Agent: ClaudeBot"));
    assertFalse(body.contains("User-Agent: Claude-SearchBot"));
    assertFalse(body.contains("User-Agent: Claude-User"));
    assertFalse(body.contains("User-Agent: Google-Extended"));
    assertFalse(body.contains("User-Agent: PerplexityBot"));
    assertFalse(body.contains("User-Agent: Perplexity-User"));
    assertFalse(body.contains("User-Agent: CCBot"));
  }

  @Test
  void doGetAddsADisallowBlockForACrawlerDisabledViaSiteProperty() throws Exception {
    Map<String, String> siteProperties = new HashMap<>();
    siteProperties.put("robots.ai.claudebot", "false");

    String body = runDoGet(siteProperties);

    assertTrue(body.contains("User-Agent: ClaudeBot\nDisallow: /\n"));
    // Only the one crawler named false should be blocked
    assertFalse(body.contains("User-Agent: GPTBot"));
  }

  @Test
  void doGetControlsTrainingAndCitationBotsIndependentlyPerVendor() throws Exception {
    // Disallowing a vendor's training crawler must not also disallow its separate
    // citation/retrieval crawler, and vice versa -- each vendor's bots are controlled
    // independently, matching how the vendors themselves document these as distinct crawlers.
    Map<String, String> siteProperties = new HashMap<>();
    siteProperties.put("robots.ai.gptbot", "false");
    siteProperties.put("robots.ai.claude-user", "false");
    siteProperties.put("robots.ai.perplexity-user", "false");

    String body = runDoGet(siteProperties);

    assertTrue(body.contains("User-Agent: GPTBot\nDisallow: /\n"));
    assertTrue(body.contains("User-Agent: Claude-User\nDisallow: /\n"));
    assertTrue(body.contains("User-Agent: Perplexity-User\nDisallow: /\n"));
    // The sibling bots for each vendor were not disallowed, so they must not appear
    assertFalse(body.contains("User-Agent: OAI-SearchBot"));
    assertFalse(body.contains("User-Agent: ChatGPT-User"));
    assertFalse(body.contains("User-Agent: ClaudeBot"));
    assertFalse(body.contains("User-Agent: Claude-SearchBot"));
    assertFalse(body.contains("User-Agent: PerplexityBot"));
  }

  @Test
  void doGetLeavesACrawlerAllowedWhenItsPropertyIsAnythingOtherThanFalse() throws Exception {
    Map<String, String> siteProperties = new HashMap<>();
    siteProperties.put("robots.ai.gptbot", "true");

    String body = runDoGet(siteProperties);

    assertFalse(body.contains("User-Agent: GPTBot"));
  }

  @Test
  void doGetServesACustomFileVerbatimInsteadOfGeneratedDefaults() throws Exception {
    File tempCmsPath = Files.createTempDirectory("robots-test").toFile();
    File configDir = new File(tempCmsPath, "config/cms");
    configDir.mkdirs();
    File customRobotsTxt = new File(configDir, "robots.txt");
    String customContent = "User-agent: *\nDisallow: /private/\n";
    Files.write(customRobotsTxt.toPath(), customContent.getBytes());
    System.setProperty("cms.path", tempCmsPath.getAbsolutePath());

    String body = runDoGet(new HashMap<>());

    assertEquals(customContent, body);
    assertFalse(body.contains("Disallow: /admin/"));
  }

  @Test
  void doGetReturns500AndDoesNotThrowWhenSitePropertiesFail() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("robots")).thenThrow(new RuntimeException("db unavailable"));

      new RobotsServlet().doGet(request, response);
    }

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    // A 500 error response must not be cached -- a transient failure would otherwise get replayed
    // to every visitor/CDN for up to a day instead of retried on the next request.
    verify(response, never()).setHeader(eq("Cache-Control"), any());
  }
}
