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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CspPolicyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.WebPageXmlLayoutCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;

/**
 * Regression test for issue #944: {@code service()} used to set {@code Content-Security-Policy}
 * twice -- a per-request nonce-based {@code script-src 'self' 'nonce-...'} policy, immediately
 * followed by an older, redundant call that dropped {@code script-src} entirely. Since
 * {@link HttpServletResponse#setHeader} replaces rather than appends, the second call silently
 * won and the nonce policy -- despite being wired into 119 JSP templates via the {@code cspNonce}
 * request attribute -- never actually reached the browser. The same clobbering pattern also reset
 * {@code Referrer-Policy} from {@code strict-origin-when-cross-origin} back to {@code same-origin}.
 *
 * <p>Reuses {@link PageServletServiceItemArchivedTest}'s minimal archived-item-404 scaffold:
 * headers are set unconditionally at the very top of {@code service()}, before the item-resolution
 * block this scaffold short-circuits on, so which branch the request ultimately takes is
 * irrelevant to what's under test here.
 *
 * @author elizabeth houser
 */
class PageServletSecurityHeadersTest {

  private static final String ITEM_UNIQUE_ID = "item123";
  private static final String REQUEST_URI = "/show/" + ITEM_UNIQUE_ID;

  private Page unrestrictedShowPage() {
    return new Page("/show/*", null, "/show/*");
  }

  private HttpServletRequest mockRequest(HttpSession session) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(request.getServletContext()).thenReturn(servletContext);
    when(servletContext.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(REQUEST_URI);
    when(request.getSession()).thenReturn(session);
    when(request.getHeader("X-Monitor")).thenReturn("true");
    return request;
  }

  private HttpSession mockSession() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.CONTROLLER)).thenReturn(new ControllerSession());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(new UserSession());
    return session;
  }

  @Test
  void serviceSendsTheNonceBasedContentSecurityPolicyExactlyOnceAndDoesNotClobberIt() throws Exception {
    HttpServletRequest request = mockRequest(mockSession());
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);
    }

    ArgumentCaptor<String> cspValues = ArgumentCaptor.forClass(String.class);
    // A regression that reintroduces the older clobbering call would make this a second
    // invocation -- times(1) catches that even before inspecting the value.
    verify(response, times(1)).setHeader(eq("Content-Security-Policy"), cspValues.capture());
    String actualCsp = cspValues.getValue();
    assertTrue(actualCsp.contains("script-src 'self' 'nonce-"),
        "the nonce-based script-src must actually reach the browser, not be dropped by a later overwrite: " + actualCsp);
    assertTrue(actualCsp.contains("base-uri 'self'") && actualCsp.contains("object-src 'none'") && actualCsp.contains("frame-ancestors 'self'"),
        "the rest of the baseline policy must still be present: " + actualCsp);
    assertTrue(actualCsp.contains("form-action 'self'"),
        "form-action must be present so an injected form cannot post to another origin: " + actualCsp);

    // Issue #1430: with no default-src, an absent directive falls back to nothing -- styles and
    // fonts were governed by neither. Both origins were measured as entirely first-party before
    // being locked to 'self'.
    assertTrue(actualCsp.contains("style-src 'self' 'unsafe-inline'"),
        "style-src must be present so a stylesheet cannot be loaded from a foreign origin: " + actualCsp);
    assertTrue(actualCsp.contains("font-src 'self'"),
        "font-src must be present so a webfont cannot be loaded from a foreign origin: " + actualCsp);
    // The directive that closes the CSS-based exfiltration channel: without it, a background-image
    // URL can carry a field value to any host. data: is permitted because it makes no request.
    assertTrue(actualCsp.contains("img-src 'self' data:"),
        "img-src must be present so an image cannot be fetched from a foreign origin: " + actualCsp);
    // VideoWidget paints a YouTube poster as a CSS background-image from img.youtube.com, and
    // video.jsp sets the Vimeo one from oEmbed, which serves from i.vimeocdn.com. Neither shows
    // up in a crawl of a site that does not use the widget, so assert them here instead.
    assertTrue(actualCsp.contains("https://img.youtube.com"),
        "img-src must allow the YouTube poster host or the video widget loses its thumbnail: " + actualCsp);
    assertTrue(actualCsp.contains("https://i.vimeocdn.com"),
        "img-src must allow the Vimeo thumbnail host set by video.jsp's oEmbed call: " + actualCsp);
    // Deliberately still absent: img-src cannot be set until published content stops referencing
    // external images, and default-src must come after it or the video/careers iframes break.
    assertTrue(!actualCsp.contains("default-src"),
        "default-src must not be added before frame-src is settled: " + actualCsp);
    // frame-src has to land before any backstop, or the third-party embeds inherit default-src
    // and stop rendering. If a future change adds default-src, frame-src must come with it.
    assertTrue(!actualCsp.contains("default-src") || actualCsp.contains("frame-src"),
        "default-src must not be introduced without frame-src: " + actualCsp);
  }

  @Test
  void serviceSendsTheReportOnlyHeaderWhenACandidatePolicyIsConfigured() throws Exception {
    // The gap this covers: CspPolicyCommand and its tests existed, the report receiver existed,
    // the admin view existed -- and nothing called reportOnlyPolicy() from the request path, so
    // setting the property changed nothing. Testing the builder in isolation could not see that.
    HttpServletRequest request = mockRequest(mockSession());
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(CspPolicyCommand.REPORT_ONLY_PROPERTY))
          .thenReturn("default-src 'self'; connect-src 'self'; script-src 'self' 'nonce-{nonce}'");
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);
    }

    ArgumentCaptor<String> reportOnly = ArgumentCaptor.forClass(String.class);
    verify(response, times(1)).setHeader(eq("Content-Security-Policy-Report-Only"), reportOnly.capture());
    String policy = reportOnly.getValue();
    assertTrue(policy.contains("connect-src 'self'"), "the configured candidate must reach the browser: " + policy);
    assertTrue(policy.contains("'nonce-") && !policy.contains("{nonce}"),
        "the nonce placeholder must be substituted, or a candidate carrying script-src reports on itself: " + policy);
    // Without a destination the browser evaluates the policy and reports to nobody, which looks
    // exactly like a policy that found nothing wrong
    assertTrue(policy.contains("report-uri") && policy.contains("report-to"), "both reporting directives: " + policy);
    verify(response, times(1)).setHeader(eq("Reporting-Endpoints"), anyString());
  }

  @Test
  void serviceSendsNoReportOnlyHeaderWhenNoCandidateIsConfigured() throws Exception {
    // Blank is how it ships, and blank must mean no header at all rather than an empty one
    HttpServletRequest request = mockRequest(mockSession());
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);
    }

    verify(response, never()).setHeader(eq("Content-Security-Policy-Report-Only"), anyString());
    verify(response, never()).setHeader(eq("Reporting-Endpoints"), anyString());
  }

  @Test
  void serviceSendsStrictReferrerPolicyExactlyOnceAndDoesNotResetItToSameOrigin() throws Exception {
    HttpServletRequest request = mockRequest(mockSession());
    HttpServletResponse response = mock(HttpServletResponse.class);
    Page pageRef = unrestrictedShowPage();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);
    }

    ArgumentCaptor<String> referrerPolicyValues = ArgumentCaptor.forClass(String.class);
    verify(response, times(1)).setHeader(eq("Referrer-Policy"), referrerPolicyValues.capture());
    assertEquals("strict-origin-when-cross-origin", referrerPolicyValues.getValue());
  }

  /** Runs service() with the standard scaffold, optionally with system.ssl configured. */
  private void serviceWithSsl(HttpServletResponse response, String systemSsl) throws Exception {
    HttpServletRequest request = mockRequest(mockSession());
    Page pageRef = unrestrictedShowPage();
    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(any(), anyString())).thenReturn(pageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      // Stubbed after the catch-all so this one wins for the key HSTS is gated on.
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.ssl")).thenReturn(systemSsl);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq(ITEM_UNIQUE_ID), anyLong(), eq(true)))
          .thenReturn(null);

      new PageServlet().service(request, response);
    }
  }

  /**
   * The three headers that are set unconditionally at the top of service(). They were emitted but
   * never asserted, so deleting any one of them broke nothing that anyone would notice: no test
   * failed, and the absence of a header is invisible in a browser unless you go looking.
   */
  @Test
  void serviceSendsTheBaselineSecurityHeadersOnEveryResponse() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    serviceWithSsl(response, null);

    verify(response, times(1)).setHeader("X-Frame-Options", "SAMEORIGIN");
    verify(response, times(1)).setHeader("X-Content-Type-Options", "nosniff");
    verify(response, times(1)).setHeader("X-XSS-Protection", "1; mode=block");
  }

  /**
   * HSTS is gated on the system.ssl property rather than the request scheme, deliberately: the
   * platform runs behind a TLS-terminating proxy, so the request arrives as http and the scheme
   * would report the wrong answer. Both directions are pinned, because the failure modes are
   * opposite and both are bad -- a missing header on an HTTPS site loses the protection, and a
   * header sent from a site that cannot serve HTTPS makes browsers refuse it for a full year.
   */
  @Test
  void serviceSendsHstsWhenTheDeploymentIsConfiguredForSsl() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    serviceWithSsl(response, "true");

    ArgumentCaptor<String> hsts = ArgumentCaptor.forClass(String.class);
    verify(response, times(1)).setHeader(eq("Strict-Transport-Security"), hsts.capture());
    assertEquals("max-age=31536000", hsts.getValue());
  }

  @Test
  void serviceSendsNoHstsWhenSslIsNotConfigured() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    serviceWithSsl(response, null);

    verify(response, never()).setHeader(eq("Strict-Transport-Security"), anyString());
  }
}
