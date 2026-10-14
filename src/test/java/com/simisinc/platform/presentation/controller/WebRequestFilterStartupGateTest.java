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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.LoadRedirectsCommand;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.rest.controller.RestRequestFilter;
import com.simisinc.platform.rest.controller.RestServlet;

/**
 * While startup waits for the database (see DeferredStartup), the site must answer 503 on every
 * path, and nothing Tomcat starts with the context may fail it. Each of these used to throw from
 * init() when startup had not completed, and one failure was enough for Tomcat to mark the ROOT
 * context failed -- after which it answered 404 everywhere, /healthz included, until a restart.
 */
class WebRequestFilterStartupGateTest {

  private final ServletContext servletContext = mock(ServletContext.class);

  @Test
  void answers503OnEveryPathWithoutTouchingTheDatabaseUntilStartupCompletes() throws Exception {
    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<DB> db = mockStatic(DB.class)) {

      WebRequestFilter filter = new WebRequestFilter();
      assertDoesNotThrow(() -> filter.init(filterConfig()));

      for (String path : new String[] { "/", "/about", "/api/oauth/token", "/css/platform.css" }) {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(path), response, chain);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(response).setHeader("Retry-After", WebRequestFilter.STARTUP_RETRY_AFTER_SECONDS);
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response, never()).sendError(anyInt());
        verify(chain, never()).doFilter(any(), any());
        assertEquals("Service temporarily unavailable", body.toString());
      }

      // The health probe keeps its JSON contract (runbooks pipe it to jq), and reports DOWN from
      // the startup flag alone
      HttpServletResponse healthResponse = mock(HttpServletResponse.class);
      StringWriter healthBody = new StringWriter();
      when(healthResponse.getWriter()).thenReturn(new PrintWriter(healthBody));
      FilterChain healthChain = mock(FilterChain.class);
      filter.doFilter(request("/healthz"), healthResponse, healthChain);
      verify(healthResponse).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      verify(healthChain, never()).doFilter(any(), any());
      assertEquals("{\"status\":\"DOWN\"}", healthBody.toString());

      // Everything the gate stands in front of can reach the database it is waiting for
      siteProperties.verifyNoInteractions();
      redirects.verifyNoInteractions();
      blockedIPList.verifyNoInteractions();
      db.verifyNoInteractions();
    }
  }

  @Test
  void finishesStartingUpOnTheFirstRequestOnceStartupCompletes() throws Exception {
    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<HealthCommand> health = mockStatic(HealthCommand.class)) {

      WebRequestFilter filter = new WebRequestFilter();
      filter.init(filterConfig());
      redirects.verifyNoInteractions();

      // ContextListener's deferred startup completes
      when(servletContext.getAttribute(ContextConstants.STARTUP_SUCCESSFUL)).thenReturn("true");
      health.when(() -> HealthCommand.isReady(any())).thenReturn(true);

      for (int i = 0; i < 2; i++) {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilter(request("/healthz"), response, mock(FilterChain.class));
        verify(response).setStatus(200);
        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      }

      // The deferred half of init() ran, and only once
      redirects.verify(LoadRedirectsCommand::load, times(1));
      blockedIPList.verify(LoadBlockedIPListCommand::retrieveCachedIpAddressList, times(1));
    }
  }

  @Test
  void theOtherStartupComponentsDoNotFailTheContextWhileStartupWaits() {
    ServletConfig servletConfig = mock(ServletConfig.class);
    when(servletConfig.getServletContext()).thenReturn(servletContext);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class)) {
      assertDoesNotThrow(() -> new RestRequestFilter().init(filterConfig()));
      assertDoesNotThrow(() -> new PageServlet().init(servletConfig));
      assertDoesNotThrow(() -> new RestServlet().init(servletConfig));
      siteProperties.verifyNoInteractions();
    }
  }

  private FilterConfig filterConfig() {
    FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getServletContext()).thenReturn(servletContext);
    return filterConfig;
  }

  private HttpServletRequest request(String path) {
    ServletContext requestContext = mock(ServletContext.class);
    when(requestContext.getContextPath()).thenReturn("");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getScheme()).thenReturn("https");
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletContext()).thenReturn(requestContext);
    when(request.getRequestURI()).thenReturn(path);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    return request;
  }
}
