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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPagePreviewTokenRepository;

/**
 * Regression test for the interaction between the #419 draft-preview-token bypass and the
 * pre-existing "setup mode" placeholder: when a guest requests {@code /} on a site with
 * {@code site.online=false}, {@link PageServlet#service} unconditionally overwrites {@code pageRef}
 * with the {@code _new_install_} template -- this ran AFTER the token logic, so it clobbered a
 * valid preview token's draft render too, silently showing the install placeholder instead of the
 * draft with no error. A valid token must now take precedence, the same way it already does over
 * {@link PageServlet#isDraftBlockedFromPublicAccess}.
 *
 * <p>Reuses {@link PageServletPreviewTokenTest}'s minimal mocking scaffold, using
 * {@link WebComponentCommand#allowsUser} as the downstream tripwire that reveals which {@code Page}
 * instance {@code pageRef} actually resolved to (the draft, or the install placeholder).
 *
 * @author elizabeth houser
 */
class PageServletSetupModePreviewTokenTest {

  private static final String REQUEST_URI = "/";
  private static final long WEB_PAGE_ID = 7L;

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

  private void stubSiteOffline(MockedStatic<LoadSitePropertyCommand> loadSiteProperty) {
    loadSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
    loadSiteProperty.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenAnswer(inv -> {
      Map<String, String> map = new HashMap<>();
      if ("site".equals(inv.getArgument(0))) {
        map.put("site.online", "false");
      }
      return map;
    });
  }

  @Test
  void serviceRendersTheDraftPreviewInsteadOfTheSetupModePlaceholderForTheHomepage() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(), "valid-token");
    HttpServletResponse response = mock(HttpServletResponse.class);

    WebPage webPage = new WebPage();
    webPage.setId(WEB_PAGE_ID);
    webPage.setLink(REQUEST_URI);
    webPage.setDraft(true);
    webPage.setPageXml(null);
    webPage.setDraftPageXml("<page><section/></page>");

    Page draftPageRef = new Page();
    Page newInstallPageRef = new Page();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> previewTokens = mockStatic(WebPagePreviewTokenRepository.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebComponentCommand> webComponent = mockStatic(WebComponentCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialMediaLinks = mockStatic(SocialMediaLinkRepository.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(REQUEST_URI)).thenReturn(webPage);
      previewTokens.when(() -> WebPagePreviewTokenRepository.findValidToken("valid-token", WEB_PAGE_ID, REQUEST_URI))
          .thenReturn(new WebPagePreviewToken());
      stubSiteOffline(loadSiteProperty);
      socialMediaLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      // No live page yet -- this is the pre-launch scenario the fix targets: a customer building a
      // brand-new site wants to preview its homepage draft before the site has ever gone live.
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(eq(webPage), anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.parseFreshDraft(eq(webPage), anyString())).thenReturn(draftPageRef);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePage("_new_install_")).thenReturn(newInstallPageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      // Stop the request at the access check just past pageRef resolution -- which Page instance it
      // was called with is the tripwire that proves the draft survived (or didn't) past setup mode.
      webComponent.when(() -> WebComponentCommand.allowsUser(any(Page.class), any(UserSession.class))).thenReturn(false);

      new PageServlet().service(request, response);

      webComponent.verify(() -> WebComponentCommand.allowsUser(eq(draftPageRef), any(UserSession.class)), times(1));
      webComponent.verify(() -> WebComponentCommand.allowsUser(eq(newInstallPageRef), any(UserSession.class)), never());
    }

    verify(response, times(1)).setHeader("X-Robots-Tag", "noindex");
  }

  @Test
  void serviceStillShowsTheSetupModePlaceholderForAGuestWithNoPreviewToken() throws Exception {
    HttpServletRequest request = mockRequest(mockSession(), null);
    HttpServletResponse response = mock(HttpServletResponse.class);

    WebPage webPage = new WebPage();
    webPage.setId(WEB_PAGE_ID);
    webPage.setLink(REQUEST_URI);
    webPage.setDraft(false);
    webPage.setPageXml(null);
    webPage.setDraftPageXml(null);

    Page newInstallPageRef = new Page();

    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> previewTokens = mockStatic(WebPagePreviewTokenRepository.class);
        MockedStatic<WebPageXmlLayoutCommand> webPageXmlLayout = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> loadSiteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebComponentCommand> webComponent = mockStatic(WebComponentCommand.class);
        MockedStatic<SocialMediaLinkRepository> socialMediaLinks = mockStatic(SocialMediaLinkRepository.class)) {

      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(REQUEST_URI)).thenReturn(webPage);
      previewTokens.when(() -> WebPagePreviewTokenRepository.findValidToken(any(), eq(WEB_PAGE_ID), anyString())).thenReturn(null);
      stubSiteOffline(loadSiteProperty);
      socialMediaLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(eq(webPage), anyString())).thenReturn(null);
      webPageXmlLayout.when(() -> WebPageXmlLayoutCommand.retrievePage("_new_install_")).thenReturn(newInstallPageRef);
      webPageXmlLayout.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(new HashMap<>());
      webComponent.when(() -> WebComponentCommand.allowsUser(any(Page.class), any(UserSession.class))).thenReturn(false);

      new PageServlet().service(request, response);

      // Setup mode must still gate a guest with no token -- the fix only adds a bypass for a valid
      // token, it must not disable the placeholder outright.
      webComponent.verify(() -> WebComponentCommand.allowsUser(eq(newInstallPageRef), any(UserSession.class)), times(1));
      webPageXmlLayout.verify(() -> WebPageXmlLayoutCommand.parseFreshDraft(any(), anyString()), never());
    }

    verify(response, never()).setHeader(eq("X-Robots-Tag"), anyString());
  }
}
