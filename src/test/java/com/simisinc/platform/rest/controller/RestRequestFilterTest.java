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

package com.simisinc.platform.rest.controller;

import static com.simisinc.platform.application.cms.HostnameCommand.HOSTNAME_ALLOW_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.CreateSessionCommand;
import com.simisinc.platform.application.LoadAppCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.rest.controller.RestServlet;

/**
 * Verifies the fix for issue #974: a request with a valid app key but no (or an invalid) Bearer
 * token previously demoted to a guest {@link User} regardless of HTTP method -- fine for a read,
 * but the filter had no per-endpoint knowledge of which write needed which role, so any write
 * endpoint had to defensively reject a guest caller on its own. This asserts the filter itself now
 * rejects a guest write outright, while a guest read and the two pre-existing unauthenticated
 * write branches ({@code /api/session}, checked here; {@code /api/oauth2/authorize}, structurally
 * unaffected since it returns before this check regardless of method) are unaffected.
 *
 * @author SimIS Inc.
 */
class RestRequestFilterTest {

  // HostnameCommand caches its allow list in static state shared by every test in the JVM (see
  // WebRequestFilterTest's identical reset) -- reset so an empty list (this repo's shipped
  // configuration) is what passesCheck() sees, regardless of test execution order.
  @BeforeEach
  @AfterEach
  void resetHostnameAllowList() {
    HostnameCommand.setList(HOSTNAME_ALLOW_LIST, new ArrayList<>());
  }

  private HttpServletRequest requestFor(String method, String requestURI, String authorizationHeader) {
    // "localhost" makes isLocal true, short-circuiting both RateLimitCommand checks in the method
    // without needing to mock that class at all.
    return requestFor(method, requestURI, authorizationHeader, "localhost", "127.0.0.1");
  }

  private HttpServletRequest requestFor(String method, String requestURI, String authorizationHeader, String serverName,
      String remoteAddr) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getScheme()).thenReturn("https");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI()).thenReturn(requestURI);
    when(request.getServerName()).thenReturn(serverName);
    when(request.getRemoteAddr()).thenReturn(remoteAddr);
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("Authorization")).thenReturn(authorizationHeader);
    return request;
  }

  private App enabledApp() {
    App app = new App();
    app.setEnabled(true);
    return app;
  }

  /**
   * CORS. The preflight echoes an allowed origin, and what it echoes is the configured site.url --
   * never a wildcard. Nothing pinned this, so a change to "*" (the usual shortcut when an
   * integration will not connect) would have made every origin able to read authenticated API
   * responses, and no test would have objected.
   */
  @Test
  void thePreflightEchoesTheConfiguredSiteUrlAndNeverAWildcard() throws Exception {
    HttpServletRequest request = requestFor("OPTIONS", "/api/collections", null);
    when(request.getHeader("Origin")).thenReturn("https://example.org");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn("https://www.example.com");
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      ArgumentCaptor<String> origin = ArgumentCaptor.forClass(String.class);
      verify(response).addHeader(eq("Access-Control-Allow-Origin"), origin.capture());
      assertEquals("https://www.example.com", origin.getValue());
      assertNotEquals("*", origin.getValue(), "a wildcard would let any origin read authenticated responses");
    }
  }

  /**
   * With no site.url configured there is no origin to allow, so the header must be omitted rather
   * than sent empty or wildcarded -- an absent header is the safe default, and the browser then
   * blocks the cross-origin read on its own.
   */
  @Test
  void thePreflightSendsNoAllowOriginWhenNoSiteUrlIsConfigured() throws Exception {
    HttpServletRequest request = requestFor("OPTIONS", "/api/collections", null);
    when(request.getHeader("Origin")).thenReturn("https://example.org");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn(null);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      verify(response, never()).addHeader(eq("Access-Control-Allow-Origin"), anyString());
    }
  }

  @Test
  void aGuestGetRequestIsAllowedThroughAsAGuest() throws Exception {
    HttpServletRequest request = requestFor("GET", "/api/collections", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      verify(chain, times(1)).doFilter(request, response);
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(request).setAttribute(eq(RequestConstants.REST_USER), userCaptor.capture());
      assertEquals(UserSession.GUEST_ID, userCaptor.getValue().getId());
    }
  }

  @Test
  void aGuestHeadRequestIsAllowedThroughAsAGuest() throws Exception {
    HttpServletRequest request = requestFor("HEAD", "/api/collections", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      verify(chain, times(1)).doFilter(request, response);
    }
  }

  @Test
  void aGuestPostRequestIsRejectedRatherThanDemoted() throws Exception {
    HttpServletRequest request = requestFor("POST", "/api/content/homepage-intro", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), times(1));
      verify(chain, never()).doFilter(any(), any());
      verify(request, never()).setAttribute(eq(RequestConstants.REST_USER), any());
    }
  }

  @Test
  void aGuestPutRequestIsRejected() throws Exception {
    HttpServletRequest request = requestFor("PUT", "/api/content/homepage-intro", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), times(1));
      verify(chain, never()).doFilter(any(), any());
    }
  }

  @Test
  void aGuestDeleteRequestIsRejected() throws Exception {
    HttpServletRequest request = requestFor("DELETE", "/api/content/homepage-intro", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), times(1));
      verify(chain, never()).doFilter(any(), any());
    }
  }

  @Test
  void aGuestWriteIsRejectedEvenWhenTheSiteIsOnline() throws Exception {
    // Guards against a regression that only rejects writes by piggybacking on the pre-existing
    // "site not online" branch -- the write rejection must fire independently of site.online.
    HttpServletRequest request = requestFor("POST", "/api/content/homepage-intro", null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());

      new RestRequestFilter().doFilter(request, response, chain);

      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), times(1));
    }
  }

  @Test
  void apiSessionPostWithNoTokenIsUnaffectedByTheWriteRejection() throws Exception {
    // /api/session is itself an unauthenticated POST (establishing a session is how a client gets
    // one in the first place) -- it is handled entirely before the new write-rejection check, so a
    // guest POSTing here must still succeed, not be caught by the new guest-write rejection.
    HttpServletRequest request = requestFor("POST", "/api/session", null);
    when(request.getHeader("USER-AGENT")).thenReturn("test-agent");
    jakarta.servlet.http.HttpSession httpSession = mock(jakarta.servlet.http.HttpSession.class);
    when(httpSession.getId()).thenReturn("test-session-id");
    when(request.getSession()).thenReturn(httpSession);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new java.io.PrintWriter(new java.io.StringWriter()));
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<CreateSessionCommand> createSession = mockStatic(CreateSessionCommand.class);
        MockedStatic<SaveSessionCommand> saveSession = mockStatic(SaveSessionCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());
      createSession.when(() -> CreateSessionCommand.createSession(anyString(), anyString(), anyString(), any(), any()))
          .thenReturn(new UserSession());

      new RestRequestFilter().doFilter(request, response, chain);

      createSession.verify(() -> CreateSessionCommand.createSession(anyString(), anyString(), anyString(), any(), any()),
          times(1));
      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), never());
    }
  }

  @Test
  void aNonLocalRequestWithAValidKeyConsultsTheIsolatedApiRateLimitBucket() throws Exception {
    // Verifies the actual production wiring behind RateLimitCommand.isApiIpAllowedRightNow's
    // isolated-bucket contract: every other test here stubs getServerName() to "localhost", which
    // makes isLocal true and short-circuits both call sites in RestRequestFilter without ever
    // exercising them. A non-local caller is required to prove the filter really calls the
    // API-only method (and not the shared web isIpAllowedRightNow) at the pre-app-key check.
    HttpServletRequest request = requestFor("GET", "/api/collections", null, "example.com", "203.0.113.5");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(enabledApp());
      rateLimit.when(() -> RateLimitCommand.isApiIpAllowedRightNow(anyString(), eq(false))).thenReturn(true);
      // Unrelated per-app-per-minute bucket also gates this non-local path further down (line 250)
      // -- stub it separately so this test isolates the isolated-IP-bucket call site under test.
      rateLimit.when(() -> RateLimitCommand.isAppAllowedRightNow(any())).thenReturn(true);

      new RestRequestFilter().doFilter(request, response, chain);

      rateLimit.verify(() -> RateLimitCommand.isApiIpAllowedRightNow(eq("203.0.113.5"), eq(false)), times(1));
      verify(chain, times(1)).doFilter(request, response);
    }
  }

  @Test
  void aNonLocalRequestThatFailsTheIsolatedApiRateLimitCheckIsRejectedWith429() throws Exception {
    HttpServletRequest request = requestFor("GET", "/api/collections", null, "example.com", "203.0.113.5");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      rateLimit.when(() -> RateLimitCommand.isApiIpAllowedRightNow(anyString(), eq(false))).thenReturn(false);

      new RestRequestFilter().doFilter(request, response, chain);

      rateLimit.verify(() -> RateLimitCommand.isApiIpAllowedRightNow(eq("203.0.113.5"), eq(false)), times(1));
      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(429), anyString()), times(1));
      verify(chain, never()).doFilter(any(), any());
    }
  }

  @Test
  void aNonLocalRequestWithAnInvalidKeyConsultsTheIsolatedApiRateLimitBucket() throws Exception {
    // Covers the second call site (the invalid-key attempt-throttling branch), which passes a
    // different second argument (true) than the pre-app-key check (false) -- both must resolve to
    // the isolated API bucket, not the shared web bucket.
    HttpServletRequest request = requestFor("GET", "/api/collections", null, "example.com", "203.0.113.5");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadAppCommand> loadApp = mockStatic(LoadAppCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<RestServlet> restServlet = mockStatic(RestServlet.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.api")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      loadApp.when(() -> LoadAppCommand.loadAppByPublicKey("test-key")).thenReturn(null);
      rateLimit.when(() -> RateLimitCommand.isApiIpAllowedRightNow(anyString(), eq(false))).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isApiIpAllowedRightNow(anyString(), eq(true))).thenReturn(true);

      new RestRequestFilter().doFilter(request, response, chain);

      rateLimit.verify(() -> RateLimitCommand.isApiIpAllowedRightNow(eq("203.0.113.5"), eq(true)), times(1));
      restServlet.verify(() -> RestServlet.sendError(eq(response), eq(401), anyString()), times(1));
    }
  }
}
