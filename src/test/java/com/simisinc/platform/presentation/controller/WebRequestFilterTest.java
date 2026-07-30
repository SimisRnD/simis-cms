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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DoNotTrackCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.BlockedIPListCommand;
import com.simisinc.platform.application.cms.HostnameCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.LoadRedirectsCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.domain.model.User;

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
  void sslRedirectIsSkippedWhenSiteUrlIsBlankAndHostnameIsNotAllowListed() throws Exception {
    // When site.url is blank and the hostname is not on the allow-list the filter cannot safely determine the
    // canonical redirect target, so it passes the request through rather than echoing the untrusted Host header.
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn("");

      HttpServletRequest request = httpRequestOverPlainHttp("www.example.com");
      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      filter.doFilter(request, response, chain);

      verify(response, never()).setHeader(anyString(), anyString());
      verify(chain).doFilter(request, response);
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

  // --- Per-request re-verification of an already-authenticated session ---
  // (the stayLoggedIn=false force-logout bug: LoginWidget.finalizeLogin() always sets a USER_TOKEN
  // cookie, even when stayLoggedIn is false, but only ever persists a matching UserToken row when it is
  // true -- so re-verifying a live session via that cookie always fails for such a session.)

  private WebRequestFilter filterWithoutSSL(MockedStatic<LoadSitePropertyCommand> siteProperties) throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getAttribute(ContextConstants.STARTUP_SUCCESSFUL)).thenReturn("true");
    FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getServletContext()).thenReturn(servletContext);

    WebRequestFilter filter = new WebRequestFilter();
    filter.init(filterConfig);
    return filter;
  }

  private HttpServletRequest loggedInRequest(HttpSession session, Cookie[] cookies) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI()).thenReturn("/my-page");
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    when(request.getServerName()).thenReturn("www.example.com");
    when(request.getSession()).thenReturn(session);
    when(request.getCookies()).thenReturn(cookies);
    // Only consulted when TRACE logging is enabled, but stub it regardless so this test's outcome
    // doesn't depend on the runner's logging configuration
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    return request;
  }

  @Test
  void aSessionWithoutStayLoggedInIsNotForceLoggedOutOnTheNextRequest() throws Exception {
    User user = new User();
    user.setId(21L);

    UserSession userSession = new UserSession();
    userSession.login(user);
    Assertions.assertTrue(userSession.isLoggedIn());

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    // The cookie LoginWidget still sets even when stayLoggedIn is false -- it has no matching UserToken
    // row, so looking it up must fail
    Cookie staleCookie = new Cookie(CookieConstants.USER_TOKEN, "no-matching-db-row");
    HttpServletRequest request = loggedInRequest(session, new Cookie[] { staleCookie });
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<MfaEnforcementCommand> mfa = mockStatic(MfaEnforcementCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      // The stale remember-me token never resolves -- exactly the buggy lookup
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser("no-matching-db-row")).thenReturn(null);
      // But the CURRENT session's own user id is still a live, enabled account
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(21L)).thenReturn(user);
      mfa.when(() -> MfaEnforcementCommand.requiresEnrollment(userSession, user)).thenReturn(false);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      // The request proceeds and the user is never logged out
      verify(chain).doFilter(request, response);
      logout.verify(() -> LogoutCommand.logout(request, response), never());
      verify(response, never()).setHeader("Location", "/login");
    }
  }

  @Test
  void aDisabledUserIsStillForceLoggedOutMidSession() throws Exception {
    // The security property the re-verification block exists for must survive the fix: an account an
    // admin suspends (or deletes) mid-session is still force-logged-out on its very next request.
    User user = new User();
    user.setId(22L);

    UserSession userSession = new UserSession();
    userSession.login(user);

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    HttpServletRequest request = loggedInRequest(session, null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      // The account was suspended (or deleted) after the session was established
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(22L)).thenReturn(null);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      logout.verify(() -> LogoutCommand.logout(request, response));
      verify(response).setHeader("Location", "/login");
      verify(chain, never()).doFilter(request, response);
    }
  }

  // --- /logout CSRF token check (GH-359) ---
  // MenuWidget renders the "Log Out" link (see MenuWidgetTest) with "?token=" + the session's
  // formToken; these prove the filter side of that contract: the exact link the page now renders
  // actually logs the user out, and a request missing/forging that token does not.

  private HttpServletRequest logoutRequest(HttpSession session, String token) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getScheme()).thenReturn("http");
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI()).thenReturn("/logout");
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    when(request.getServerName()).thenReturn("www.example.com");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://www.example.com/logout"));
    when(request.getSession(false)).thenReturn(session);
    when(request.getParameter("token")).thenReturn(token);
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    return request;
  }

  @Test
  void logoutWithTheTokenTheRenderedLinkCarriesActuallyInvalidatesTheSession() throws Exception {
    User user = new User();
    user.setId(30L);
    UserSession userSession = new UserSession();
    userSession.login(user);
    // This is exactly what MenuWidget now appends to the rendered "Log Out" link
    String renderedToken = userSession.getFormToken();

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    HttpServletRequest request = logoutRequest(session, renderedToken);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      // Force an early return right after the logout call (the SSL redirect below it) so this
      // test doesn't have to stub the rest of the per-request pipeline (cookies, visitor
      // tracking, etc.) that has nothing to do with the CSRF token check being verified here.
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn(SITE_URL);

      WebRequestFilter filter = filterRequiringSSL(siteProperties);
      filter.doFilter(request, response, chain);

      logout.verify(() -> LogoutCommand.logout(request, response));
    }
  }

  @Test
  void logoutWithAMissingOrWrongTokenDoesNotInvalidateTheSession() throws Exception {
    // The behavior this whole check exists to prevent: a forged/tokenless /logout (the CSRF
    // attack GH-359 fixed) must NOT log the user out -- it should just bounce to "/" and leave
    // the session alone.
    User user = new User();
    user.setId(31L);
    UserSession userSession = new UserSession();
    userSession.login(user);

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    HttpServletRequest request = logoutRequest(session, null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      logout.verify(() -> LogoutCommand.logout(any(), any()), never());
      verify(response).setHeader("Location", "/");
      verify(chain, never()).doFilter(request, response);
    }
  }
}
