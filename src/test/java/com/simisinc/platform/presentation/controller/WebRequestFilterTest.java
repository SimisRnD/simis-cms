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

import static com.simisinc.platform.application.cms.HostnameCommand.HOSTNAME_ALLOW_LIST;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.BlockedIPListCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.LoadRedirectsCommand;

/**
 * Verifies that the http to https redirect targets the configured site, and not the client-supplied Host header
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class WebRequestFilterTest {

  private static final String SITE_URL = "https://www.example.com";

  // HostnameCommand caches the allow list in static state shared by every test in the JVM, so it is reset both
  // before and after each test to keep the non-empty list used below from leaking into other tests
  @BeforeEach
  @AfterEach
  void resetHostnameAllowList() {
    // The shipped configuration has no hostname-allow-list.csv, so the allow list is empty by default
    HostnameCommand.setList(HOSTNAME_ALLOW_LIST, new ArrayList<>());
  }

  private HttpServletRequest httpRequestOverPlainHttp(String hostHeader) {
    return httpRequestOverPlainHttp(hostHeader, "/about");
  }

  private HttpServletRequest httpRequestOverPlainHttp(String hostHeader, String requestURI) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getScheme()).thenReturn("http");
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI()).thenReturn(requestURI);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    // getServerName() and getRequestURL() are both derived from the Host header by the container
    when(request.getServerName()).thenReturn(hostHeader);
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://" + hostHeader + requestURI));
    return request;
  }

  private WebRequestFilter filterRequiringSSL(MockedStatic<LoadSitePropertyCommand> siteProperties) throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getAttribute(ContextConstants.STARTUP_SUCCESSFUL)).thenReturn("true");
    FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getServletContext()).thenReturn(servletContext);

    siteProperties.when(() -> LoadSitePropertyCommand.loadByName("system.ssl")).thenReturn("true");

    WebRequestFilter filter = new WebRequestFilter();
    filter.init(filterConfig);
    return filter;
  }

  @Test
  void sslRedirectUsesTheConfiguredSiteUrlRatherThanTheHostHeader() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn(SITE_URL);

      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      filter.doFilter(httpRequestOverPlainHttp("evil.example.net"), response, chain);

      verify(response).setHeader("Location", SITE_URL + "/about");
      verify(response, never()).setHeader("Location", "https://evil.example.net/about");
    }
  }

  @Test
  void sslRedirectFallsBackToTheRequestUrlWhenSiteUrlIsNotConfigured() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn("");

      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      filter.doFilter(httpRequestOverPlainHttp("www.example.com"), response, chain);

      verify(response).setHeader("Location", "https://www.example.com/about");
    }
  }

  @Test
  void sslRedirectUsesTheRequestUrlWhenTheHostIsOnTheAllowList() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    List<String> allowList = new ArrayList<>();
    allowList.add("intranet.example.com");
    HostnameCommand.setList(HOSTNAME_ALLOW_LIST, allowList);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn(SITE_URL);

      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      filter.doFilter(httpRequestOverPlainHttp("intranet.example.com"), response, chain);

      verify(response).setHeader("Location", "https://intranet.example.com/about");
    }
  }

  @Test
  void sslRedirectCollapsesAProtocolRelativePathToTheSiteRoot() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn(SITE_URL);

      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      // A protocol-relative path would otherwise produce https://www.example.com//evil.example.net
      filter.doFilter(httpRequestOverPlainHttp("evil.example.net", "//evil.example.net/path"), response, chain);

      verify(response).setHeader("Location", SITE_URL + "/");
    }
  }

  @Test
  void safeRedirectPathAllowsAPlainAbsolutePath() {
    Assertions.assertEquals("/about", WebRequestFilter.safeRedirectPath("/about"));
    Assertions.assertEquals("/a/b/c.html", WebRequestFilter.safeRedirectPath("/a/b/c.html"));
  }

  @Test
  void safeRedirectPathRejectsHostChangingAndSplittingPaths() {
    // Protocol-relative and backslash variants a browser would read as a host
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath("//evil.example.net"));
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath("/\\evil.example.net"));
    // Not an absolute path
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath("evil"));
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath(null));
    // Embedded CR/LF that could split the response header
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath("/a\r\nSet-Cookie: x=y"));
    Assertions.assertEquals("/", WebRequestFilter.safeRedirectPath("/a\nb"));
  }
}
