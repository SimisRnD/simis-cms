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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.WebPageXmlLayoutCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.persistence.cms.WebPagePreviewTokenRepository;

/**
 * Regression test for issue #419: a page that has never been published ({@code draft=true},
 * blank {@code pageXml}) normally 404s for a guest via {@link PageServlet#isDraftBlockedFromPublicAccess}
 * -- this is what {@link PageServletTest} already covers for the un-tokened case. This test covers
 * the new bypass: a valid, unexpired preview token must let that same request past the block (proven
 * here by observing that {@link WebPageXmlLayoutCommand#retrievePageForRequest} -- unreachable code
 * on the blocked path -- gets invoked), and must mark the response {@code X-Robots-Tag: noindex} so
 * the unreviewed content can never be indexed.
 *
 * <p>Reuses {@link PageServletSecurityHeadersTest}'s minimal mocking scaffold, stopping the request
 * at the "page not found" branch just past the bypass point under test -- deep widget/JSP rendering
 * is exercised by live Docker verification instead, not by this unit test.
 *
 * @author elizabeth houser
 */
class PageServletPreviewTokenTest {

  private static final String REQUEST_URI = "/never-published-page";
  private static final long WEB_PAGE_ID = 42L;

  private WebPage neverPublishedWebPageWithDraft() {
    WebPage webPage = new WebPage();
    webPage.setId(WEB_PAGE_ID);
    webPage.setLink(REQUEST_URI);
    webPage.setDraft(true);
    webPage.setPageXml(null);
    webPage.setDraftPageXml("<page><section/></page>");
    return webPage;
  }

  private HttpServletRequest mockRequest(HttpSession session, String previewToken) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(request.getServletContext()).thenReturn(servletContext);
    when(servletContext.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(REQUEST_URI);
    when(request.getSession()).thenReturn(session);
    when(request.getParameter("previewToken")).thenReturn(previewToken);
    return request;
  }

  private HttpSession mockSession() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(SessionConstants.CONTROLLER)).thenReturn(new ControllerSession());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(new UserSession());
    return session;
  }

  @Test
  void serviceBypassesTheDraftBlockAndMarksNoindexWhenAValidPreviewTokenIsPresented() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(), "valid-token");
    HttpServletResponse response = mock(HttpServletResponse.class);
    WebPage webPage = neverPublishedWebPageWithDraft();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> previewTokens = mockStatic(WebPagePreviewTokenRepository.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(REQUEST_URI)).thenReturn(webPage);
      previewTokens.when(() -> WebPagePreviewTokenRepository.findValidToken("valid-token", WEB_PAGE_ID, REQUEST_URI))
          .thenReturn(new WebPagePreviewToken());
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> new HashMap<String, String>());
      // Let the request 404 cleanly just past the bypass point under test, rather than mocking the
      // full widget/JSP render pipeline -- that end-to-end behavior is covered by live Docker verification.
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(eq(webPage), anyString())).thenReturn(null);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());

      new PageServlet().service(request, response);

      // Reaching retrievePageForRequest proves the isDraftBlockedFromPublicAccess 404 (which returns
      // before this line) was skipped for this token-bearing request.
      webPageXmlLayout.verify(() -> WebPageXmlLayoutCommand.retrievePageForRequest(eq(webPage), anyString()), times(1));
    }

    verify(response, times(1)).setHeader("X-Robots-Tag", "noindex");
  }

  @Test
  void serviceStillBlocksTheSamePageForAGuestWithNoPreviewToken() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(), null);
    HttpServletResponse response = mock(HttpServletResponse.class);
    WebPage webPage = neverPublishedWebPageWithDraft();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> previewTokens = mockStatic(WebPagePreviewTokenRepository.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(REQUEST_URI)).thenReturn(webPage);
      previewTokens.when(() -> WebPagePreviewTokenRepository.findValidToken(any(), eq(WEB_PAGE_ID), anyString())).thenReturn(null);
      loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      new PageServlet().service(request, response);

      verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
      // The block must return before layout resolution is ever attempted.
      webPageXmlLayout.verifyNoMoreInteractions();
    }

    verify(response, never()).setHeader(eq("X-Robots-Tag"), anyString());
  }
}
