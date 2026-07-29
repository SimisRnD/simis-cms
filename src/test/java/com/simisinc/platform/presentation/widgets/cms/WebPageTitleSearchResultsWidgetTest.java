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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class WebPageTitleSearchResultsWidgetTest extends WidgetBase {

  @Test
  @SuppressWarnings("unchecked")
  void executeReturnsSearchResultsForAccessiblePages() {
    addQueryParameter(widgetContext, "query", "widgets");

    WebPage found = new WebPage();
    found.setLink("/about");
    found.setTitle("Widgets");
    found.setDescription("A page about our company");
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(found);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      repository.when(() -> WebPageRepository.search(eq("widgets"), any())).thenReturn(webPageList);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/about"), any())).thenReturn(true);

      WidgetContext result = new WebPageTitleSearchResultsWidget().execute(widgetContext);

      Assertions.assertNotNull(result);
      List<SearchResult> searchResultList = (List<SearchResult>) result.getRequest().getAttribute("searchResultList");
      assertEquals(1, searchResultList.size());
      assertEquals("/about", searchResultList.get(0).getLink());
      assertEquals("Widgets", searchResultList.get(0).getPageTitle());
      assertEquals("A page about our company", searchResultList.get(0).getPageDescription());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeExcludesPagesTheUserCannotAccess() {
    addQueryParameter(widgetContext, "query", "widgets");

    WebPage restricted = new WebPage();
    restricted.setLink("/staff-only");
    restricted.setTitle("Widgets (Internal)");
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(restricted);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      repository.when(() -> WebPageRepository.search(eq("widgets"), any())).thenReturn(webPageList);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/staff-only"), any())).thenReturn(false);

      new WebPageTitleSearchResultsWidget().execute(widgetContext);

      List<SearchResult> searchResultList = (List<SearchResult>) widgetContext.getRequest().getAttribute("searchResultList");
      Assertions.assertTrue(searchResultList.isEmpty());
    }
  }

  @Test
  void executeReturnsNullWhenNoQueryIsProvided() {
    WidgetContext result = new WebPageTitleSearchResultsWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeReturnsNullWhenResultsAreEmptyAndShowWhenEmptyIsFalse() {
    addQueryParameter(widgetContext, "query", "xylophone");
    preferences.put("showWhenEmpty", "false");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      repository.when(() -> WebPageRepository.search(anyString(), any())).thenReturn(new ArrayList<>());

      WidgetContext result = new WebPageTitleSearchResultsWidget().execute(widgetContext);

      assertNull(result.getJsp());
    }
  }
}
