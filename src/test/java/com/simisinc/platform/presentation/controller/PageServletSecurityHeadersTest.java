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
}
