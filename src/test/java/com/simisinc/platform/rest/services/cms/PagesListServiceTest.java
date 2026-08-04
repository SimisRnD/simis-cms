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

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.ValidateApiAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link PagesListService} filters each page through
 * {@link ValidateApiAccessToWebPageCommand}, the same gate {@link PageService} uses for a single
 * page (issue #412).
 *
 * @author SimIS Inc.
 */
class PagesListServiceTest {

  private ServiceContext contextWithParams(String page, String size) {
    ServiceContext context = new ServiceContext();
    HashMap<String, String[]> params = new HashMap<>();
    if (page != null) {
      params.put("page", new String[] { page });
    }
    if (size != null) {
      params.put("size", new String[] { size });
    }
    context.setParameterMap(params);
    return context;
  }

  private WebPage webPageWithLink(String link) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    return webPage;
  }

  @Test
  void getPassesTheRequestedPageAndSizeToDataConstraints() {
    ServiceContext context = contextWithParams("3", "5");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      repo.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of());

      new PagesListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      repo.verify(() -> WebPageRepository.findAll(any(WebPageSpecification.class), constraintsCaptor.capture()));
      assertEquals(3, constraintsCaptor.getValue().getPageNumber());
      assertEquals(5, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void getDefaultsToPageOneSizeTwentyWhenParamsAreMissing() {
    ServiceContext context = contextWithParams(null, null);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      repo.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of());

      new PagesListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      repo.verify(() -> WebPageRepository.findAll(any(WebPageSpecification.class), constraintsCaptor.capture()));
      assertEquals(1, constraintsCaptor.getValue().getPageNumber());
      assertEquals(20, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void getOmitsPagesThatFailTheAccessCheck() {
    ServiceContext context = contextWithParams(null, null);
    WebPage visible = webPageWithLink("/about");
    WebPage gated = webPageWithLink("/internal-only");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of(visible, gated));
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(visible, null)).thenReturn(true);
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(gated, null)).thenReturn(false);

      ServiceResponse response = new PagesListService().get(context);

      assertEquals(200, response.getStatus());
      @SuppressWarnings("unchecked")
      List<WebPageResponse> data = (List<WebPageResponse>) response.getData();
      assertEquals(1, data.size());
      assertEquals("/about", data.get(0).getLink());
    }
  }

  @Test
  void getReturns200WithAllVisiblePagesMapped() {
    ServiceContext context = contextWithParams(null, null);
    WebPage page = webPageWithLink("/about");
    page.setTitle("About");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> access = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of(page));
      access.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(page, null)).thenReturn(true);

      ServiceResponse response = new PagesListService().get(context);

      assertEquals(200, response.getStatus());
      @SuppressWarnings("unchecked")
      List<WebPageResponse> data = (List<WebPageResponse>) response.getData();
      assertEquals(1, data.size());
      assertEquals("/about", data.get(0).getLink());
      assertEquals("About", data.get(0).getTitle());
    }
  }
}
