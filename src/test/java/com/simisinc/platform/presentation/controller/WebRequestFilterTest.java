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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.simisinc.platform.application.cms.LoadWebRedirectCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;

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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<MfaEnforcementCommand> mfa = mockStatic(MfaEnforcementCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class);
        MockedStatic<UserLoginRepository> userLogins = mockStatic(UserLoginRepository.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
      // The stale remember-me token never resolves -- exactly the buggy lookup
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser("no-matching-db-row")).thenReturn(null);
      // But the CURRENT session's own user id is still a live, enabled account
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(21L)).thenReturn(user);
      mfa.when(() -> MfaEnforcementCommand.requiresEnrollment(userSession, user)).thenReturn(false);
      // This request reaches the daily-activity tracking added for the DAU/MAU fix, which needs a
      // resolvable site timezone (see FormatDateCommand.getSiteZoneId()) -- not this test's concern,
      // so pin it to a fixed zone rather than leave it unstubbed (null)
      siteProperties.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), anyString())).thenReturn("UTC");

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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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

  // --- Daily/Monthly Active Users tracking (DAU/MAU undercount fix) ---
  // UserLoginRepository.findUniqueDailyLogins/findUniqueMonthlyLogins (the SiteStatsWidget
  // Daily/Monthly Active Users tiles) COUNT(DISTINCT user_id) from user_logins. A row used to be
  // written only once per fresh HttpSession -- at the moment it first became authenticated -- so a
  // continuously-active user whose session survives past midnight (60-minute timeout, refreshed on
  // activity; see web.xml) was never counted again after their first day. trackDailyLogin() now
  // writes a row once per calendar day per session instead, using an in-memory tracked date on
  // UserSession rather than a database read.

  private static final ZoneId TEST_ZONE = ZoneId.of("UTC");

  /** Stubs the pieces of the per-request pipeline that trackDailyLogin's placement in doFilter runs after. */
  private void stubDailyLoginPrerequisites(User user, MockedStatic<LoadSitePropertyCommand> siteProperties,
      MockedStatic<LoadRedirectsCommand> redirects, MockedStatic<LoadWebRedirectCommand> webRedirects,
      MockedStatic<BlockedIPListCommand> blockedIPs, MockedStatic<AuthenticateLoginCommand> auth,
      MockedStatic<MfaEnforcementCommand> mfa) {
    redirects.when(LoadRedirectsCommand::load).thenReturn(null);
    webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
    blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);
    // getSiteZoneId() falls back to its passed-in default when unconfigured -- pin it to a known zone
    siteProperties.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), anyString())).thenReturn(TEST_ZONE.getId());
    auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(user.getId())).thenReturn(user);
    mfa.when(() -> MfaEnforcementCommand.requiresEnrollment(any(), eq(user))).thenReturn(false);
  }

  @Test
  void aSessionNotYetTrackedTodayWritesAUserLoginsRowAndStampsTheDate() throws Exception {
    User user = new User();
    user.setId(50L);

    UserSession userSession = new UserSession();
    userSession.login(user);
    // Simulate a session whose HttpSession survived past midnight: it was last tracked yesterday,
    // with no fresh authentication event in between
    LocalDate today = LocalDate.now(TEST_ZONE);
    userSession.setLastLoginTrackedDate(today.minusDays(1));

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    HttpServletRequest request = loggedInRequest(session, null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<MfaEnforcementCommand> mfa = mockStatic(MfaEnforcementCommand.class);
        MockedStatic<UserLoginRepository> userLogins = mockStatic(UserLoginRepository.class)) {

      stubDailyLoginPrerequisites(user, siteProperties, redirects, webRedirects, blockedIPs, auth, mfa);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      // A new calendar day since the last tracked activity -> exactly one row written
      userLogins.verify(() -> UserLoginRepository.save(any()), times(1));
      Assertions.assertEquals(today, userSession.getLastLoginTrackedDate());
    }
  }

  @Test
  void aSessionAlreadyTrackedTodayDoesNotWriteASecondRowOnTheNextRequest() throws Exception {
    User user = new User();
    user.setId(51L);

    UserSession userSession = new UserSession();
    userSession.login(user);
    // Already recorded activity today (e.g. from an earlier request this same day)
    LocalDate today = LocalDate.now(TEST_ZONE);
    userSession.setLastLoginTrackedDate(today);

    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    HttpServletRequest request = loggedInRequest(session, null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<DoNotTrackCommand> doNotTrack = mockStatic(DoNotTrackCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<MfaEnforcementCommand> mfa = mockStatic(MfaEnforcementCommand.class);
        MockedStatic<UserLoginRepository> userLogins = mockStatic(UserLoginRepository.class)) {

      stubDailyLoginPrerequisites(user, siteProperties, redirects, webRedirects, blockedIPs, auth, mfa);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      // Same calendar day as the last tracked activity -> no write
      userLogins.verify(() -> UserLoginRepository.save(any()), never());
      Assertions.assertEquals(today, userSession.getLastLoginTrackedDate());
    }
  }

  @Test
  void trackDailyLoginWritesOnceOnADayBoundaryAndSkipsASecondCallTheSameDay() {
    // Directly exercises the helper (matching the safeRedirectPath() static-helper pattern already
    // used in this file) across two distinct simulated calendar days, without needing to mock
    // LocalDate.now() itself.
    User user = new User();
    user.setId(52L);
    UserSession userSession = new UserSession();
    userSession.login(user);

    LocalDate day1 = LocalDate.of(2026, 3, 1);
    LocalDate day2 = LocalDate.of(2026, 3, 2);

    try (MockedStatic<UserLoginRepository> userLogins = mockStatic(UserLoginRepository.class)) {
      // Day 1, first request: never tracked before -> writes a row
      boolean wroteDay1First = WebRequestFilter.trackDailyLogin(userSession, day1, "203.0.113.9", "test-agent");
      Assertions.assertTrue(wroteDay1First);
      Assertions.assertEquals(day1, userSession.getLastLoginTrackedDate());

      // Day 1, second request (same day): already tracked -> no additional row
      boolean wroteDay1Second = WebRequestFilter.trackDailyLogin(userSession, day1, "203.0.113.9", "test-agent");
      Assertions.assertFalse(wroteDay1Second);

      // Day 2: the tracked date changed -> writes a second, separate row
      boolean wroteDay2 = WebRequestFilter.trackDailyLogin(userSession, day2, "203.0.113.9", "test-agent");
      Assertions.assertTrue(wroteDay2);
      Assertions.assertEquals(day2, userSession.getLastLoginTrackedDate());

      // Exactly two rows total: one per distinct calendar day, none for the repeated same-day request
      userLogins.verify(() -> UserLoginRepository.save(any()), times(2));
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
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
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class);
        MockedStatic<LogoutCommand> logout = mockStatic(LogoutCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(request, response, chain);

      logout.verify(() -> LogoutCommand.logout(any(), any()), never());
      verify(response).setHeader("Location", "/");
      verify(chain, never()).doFilter(request, response);
    }
  }

  // --- Database-backed redirects (issue #408) ---
  // WebRequestFilter now checks the admin-managed web_redirects table (via LoadWebRedirectCommand,
  // through CacheManager.WEB_REDIRECT_CACHE) before falling back to the legacy redirects.csv map: a
  // database match takes precedence when both define a rule for the same from_path, and the CSV
  // lookup still works as a fallback for paths only the legacy file knows about during the
  // migration/transition period.

  private HttpServletRequest requestForResource(String requestURI) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getScheme()).thenReturn("https");
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI()).thenReturn(requestURI);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    when(request.getServerName()).thenReturn("www.example.com");
    // Only consulted when TRACE logging is enabled, but stub it regardless so this test's outcome
    // doesn't depend on the runner's logging configuration
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    return request;
  }

  private WebRedirect dbRedirect(String fromPath, String toUrl, int statusCode) {
    WebRedirect redirect = new WebRedirect();
    redirect.setFromPath(fromPath);
    redirect.setToUrl(toUrl);
    redirect.setStatusCode(statusCode);
    redirect.setEnabled(true);
    return redirect;
  }

  private WebRedirect disabledDbRedirect(String fromPath, String toUrl) {
    WebRedirect redirect = dbRedirect(fromPath, toUrl, WebRedirect.PERMANENT);
    redirect.setEnabled(false);
    return redirect;
  }

  // Issue #408 review: a database row -- even a disabled one -- must be the final word on its
  // from_path. Falling through to the legacy CSV fallback for a disabled row would silently
  // resurrect a redirect an admin just turned off via the "disable" toggle, through the exact
  // legacy mechanism the database-backed feature was meant to replace.
  //
  // Uses a /css/... resource so that, once neither redirect fires, the filter reaches the "allow
  // some browser resources" bypass and calls chain.doFilter() a few lines later -- without that,
  // asserting the request falls all the way through would need the full session/cookie/login mock
  // rig other tests in this file build for that (e.g. loggedInRequest()), for no benefit here.
  @Test
  void aDisabledDbRedirectDoesNotFallThroughToTheCsvFallbackForTheSamePath() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    Map<String, String> csvRedirects = new HashMap<>();
    csvRedirects.put("/css/shared-path.css", "/csv-target");

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(csvRedirects);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/css/shared-path.css"))
          .thenReturn(disabledDbRedirect("/css/shared-path.css", "/db-target"));
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/css/shared-path.css"), response, chain);

      verify(response, never()).setHeader(anyString(), anyString());
      verify(chain).doFilter(any(), any());
    }
  }

  // Issue #408 review: deleting a database-backed redirect whose from_path also happens to be
  // defined in the legacy redirects.csv file must actually stop the redirect for the rest of this
  // server's uptime, not just remove the database row while the CSV-backed fallback (loaded once at
  // filter startup) keeps serving it -- see WebRedirectListWidget.remove() /
  // WebRequestFilter.purgeCsvFallback(). Uses a /css/... resource for the same reason as the test
  // above.
  @Test
  void purgeCsvFallbackStopsTheCsvFallbackFromServingADeletedPath() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    Map<String, String> csvRedirects = new HashMap<>();
    csvRedirects.put("/css/deleted-path.css", "/csv-target");

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(csvRedirects);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);

      // Before the "delete", the CSV fallback still serves the shared path (baseline)
      filter.doFilter(requestForResource("/css/deleted-path.css"), response, chain);
      verify(response).setHeader("Location", "/csv-target");

      // Simulate WebRedirectListWidget.remove() purging the deleted from_path
      WebRequestFilter.purgeCsvFallback("/css/deleted-path.css");

      HttpServletResponse secondResponse = mock(HttpServletResponse.class);
      filter.doFilter(requestForResource("/css/deleted-path.css"), secondResponse, chain);
      verify(secondResponse, never()).setHeader(anyString(), anyString());
      verify(chain).doFilter(any(), any());
    }
  }

  @Test
  void dbBackedRedirectIsServedWhenPresent() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/old-db-page"))
          .thenReturn(dbRedirect("/old-db-page", "/new-db-page", WebRedirect.PERMANENT));
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/old-db-page"), response, chain);

      verify(response).setHeader("Location", "/new-db-page");
      verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      verify(chain, never()).doFilter(any(), any());
    }
  }

  @Test
  void dbBackedRedirectTakesPrecedenceOverACsvRedirectForTheSamePath() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    Map<String, String> csvRedirects = new HashMap<>();
    csvRedirects.put("/shared-path", "/csv-target");

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(csvRedirects);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/shared-path"))
          .thenReturn(dbRedirect("/shared-path", "/db-target", WebRedirect.PERMANENT));
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/shared-path"), response, chain);

      verify(response).setHeader("Location", "/db-target");
      verify(response, never()).setHeader("Location", "/csv-target");
    }
  }

  @Test
  void csvRedirectIsStillServedAsAFallbackWhenNoDbRedirectMatches() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    Map<String, String> csvRedirects = new HashMap<>();
    csvRedirects.put("/legacy-only-path", "/legacy-target");

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(csvRedirects);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath(anyString())).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/legacy-only-path"), response, chain);

      verify(response).setHeader("Location", "/legacy-target");
      verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    }
  }

  @Test
  void dbAndCsvRedirectsCoexistWhenTheyCoverDifferentPaths() throws Exception {
    HttpServletResponse dbResponse = mock(HttpServletResponse.class);
    HttpServletResponse csvResponse = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    Map<String, String> csvRedirects = new HashMap<>();
    csvRedirects.put("/csv-only-path", "/csv-target");

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(csvRedirects);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/db-only-path"))
          .thenReturn(dbRedirect("/db-only-path", "/db-target", WebRedirect.PERMANENT));
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/csv-only-path")).thenReturn(null);
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/db-only-path"), dbResponse, chain);
      filter.doFilter(requestForResource("/csv-only-path"), csvResponse, chain);

      verify(dbResponse).setHeader("Location", "/db-target");
      verify(csvResponse).setHeader("Location", "/csv-target");
    }
  }

  @Test
  void dbBackedRedirectHonorsA302StatusCode() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<LoadSitePropertyCommand> siteProperties = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadRedirectsCommand> redirects = mockStatic(LoadRedirectsCommand.class);
        MockedStatic<LoadWebRedirectCommand> webRedirects = mockStatic(LoadWebRedirectCommand.class);
        MockedStatic<LoadBlockedIPListCommand> blockedIPList = mockStatic(LoadBlockedIPListCommand.class);
        MockedStatic<BlockedIPListCommand> blockedIPs = mockStatic(BlockedIPListCommand.class)) {

      redirects.when(LoadRedirectsCommand::load).thenReturn(null);
      webRedirects.when(() -> LoadWebRedirectCommand.matchByFromPath("/temp-redirect"))
          .thenReturn(dbRedirect("/temp-redirect", "/temp-target", WebRedirect.TEMPORARY));
      blockedIPs.when(() -> BlockedIPListCommand.passesCheck(anyString(), anyString())).thenReturn(true);

      WebRequestFilter filter = filterWithoutSSL(siteProperties);
      filter.doFilter(requestForResource("/temp-redirect"), response, chain);

      verify(response).setHeader("Location", "/temp-target");
      verify(response).setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }
  }
}
