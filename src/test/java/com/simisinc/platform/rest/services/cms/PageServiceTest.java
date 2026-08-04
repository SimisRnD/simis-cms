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

package com.simisinc.platform.rest.services.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.ValidateApiAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link PageService}, including that a multi-segment page path (pathParam + pathParam2)
 * is reconstructed correctly back into a single {@code WebPage.link} value, and that visibility
 * is delegated to {@link ValidateApiAccessToWebPageCommand} rather than a raw enabled/draft check
 * (issue #412).
 *
 * @author SimIS Inc.
 */
class PageServiceTest {

  private WebPage webPageWithLink(String link) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle("Title for " + link);
    return webPage;
  }

  @Test
  void getReconstructsATwoSegmentPagePathIntoTheFullLinkWithLeadingSlash() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("about");
    context.setPathParam2("team");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      WebPage webPage = webPageWithLink("/about/team");
      repo.when(() -> WebPageRepository.findByLink("/about/team")).thenReturn(webPage);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(webPage, null)).thenReturn(true);

      ServiceResponse response = new PageService().get(context);

      assertEquals(200, response.getStatus());
      repo.verify(() -> WebPageRepository.findByLink("/about/team"));
    }
  }

  @Test
  void getReconstructsAThreeSegmentPagePath() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("about");
    context.setPathParam2("team/leadership");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      WebPage webPage = webPageWithLink("/about/team/leadership");
      repo.when(() -> WebPageRepository.findByLink("/about/team/leadership")).thenReturn(webPage);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(webPage, null)).thenReturn(true);

      ServiceResponse response = new PageService().get(context);

      assertEquals(200, response.getStatus());
      WebPageResponse data = (WebPageResponse) response.getData();
      assertEquals("/about/team/leadership", data.getLink());
    }
  }

  @Test
  void getTreatsAnEmptyPathParam2FromATrailingSlashAsAbsent() {
    // RestServlet's split yields "" (not null) for pathParam2 on a request like
    // GET /api/page/about/ -- must reconstruct to "/about", not "/about/".
    ServiceContext context = new ServiceContext();
    context.setPathParam("about");
    context.setPathParam2("");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      WebPage webPage = webPageWithLink("/about");
      repo.when(() -> WebPageRepository.findByLink("/about")).thenReturn(webPage);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(webPage, null)).thenReturn(true);

      ServiceResponse response = new PageService().get(context);

      assertEquals(200, response.getStatus());
      repo.verify(() -> WebPageRepository.findByLink("/about"));
    }
  }

  @Test
  void getUsesJustThePathParamWhenThereIsOnlyOneSegment() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("about");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      WebPage webPage = webPageWithLink("/about");
      repo.when(() -> WebPageRepository.findByLink("/about")).thenReturn(webPage);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(webPage, null)).thenReturn(true);

      ServiceResponse response = new PageService().get(context);

      assertEquals(200, response.getStatus());
      WebPageResponse data = (WebPageResponse) response.getData();
      assertEquals("/about", data.getLink());
    }
  }

  @Test
  void getReturns404WhenThereIsNoPathParamAtAll() {
    // GET /api/page with no further segment -- RestServlet's direct-key lookup means pathParam
    // never gets populated. Must not become the literal string "/null".
    ServiceContext context = new ServiceContext();

    ServiceResponse response = new PageService().get(context);

    assertEquals(404, response.getStatus());
  }

  @Test
  void getReturns404WhenThePageDoesNotExist() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("nowhere");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      repo.when(() -> WebPageRepository.findByLink("/nowhere")).thenReturn(null);

      ServiceResponse response = new PageService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns404WhenAccessIsDenied() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("gated-page");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      WebPage webPage = webPageWithLink("/gated-page");
      repo.when(() -> WebPageRepository.findByLink("/gated-page")).thenReturn(webPage);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(any(WebPage.class), any())).thenReturn(false);

      ServiceResponse response = new PageService().get(context);

      assertEquals(404, response.getStatus());
    }
  }
}
