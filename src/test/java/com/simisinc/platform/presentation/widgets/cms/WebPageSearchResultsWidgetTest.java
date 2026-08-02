/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.TableOfContentsRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class WebPageSearchResultsWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // No query parameters
    WebPageSearchResultsWidget widget = new WebPageSearchResultsWidget();
    Assertions.assertNull(widget.execute(widgetContext));

    // Set widget preferences
    preferences.put("query", "test");

    // Expect no results
    widget.execute(widgetContext);
    Assertions.assertNull(widgetContext.getJsp());

    // Mock the results
//    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }

  // --- shouldRestrictToPublishedSearchableWebPages: mirrors isPageInTheNavigation's privileged
  // bypass (hasRole("admin") || hasRole("content-manager") means "skip restrictions") ---

  @Test
  void restrictsAPlainLoggedInUser() {
    Assertions.assertTrue(WebPageSearchResultsWidget.shouldRestrictToPublishedSearchableWebPages(widgetContext));
  }

  @Test
  void restrictsAGuest() {
    logout(widgetContext);
    Assertions.assertTrue(WebPageSearchResultsWidget.shouldRestrictToPublishedSearchableWebPages(widgetContext));
  }

  @Test
  void doesNotRestrictAnAdmin() {
    setRoles(widgetContext, ADMIN);
    Assertions.assertFalse(WebPageSearchResultsWidget.shouldRestrictToPublishedSearchableWebPages(widgetContext));
  }

  @Test
  void doesNotRestrictAContentManager() {
    setRoles(widgetContext, CONTENT_MANAGER);
    Assertions.assertFalse(WebPageSearchResultsWidget.shouldRestrictToPublishedSearchableWebPages(widgetContext),
        "a content-manager is privileged, like isPageInTheNavigation treats it, and must not be restricted");
  }

  @Test
  void doesNotRestrictAnAdminWhoIsAlsoAContentManager() {
    setRoles(widgetContext, ADMIN, CONTENT_MANAGER);
    Assertions.assertFalse(WebPageSearchResultsWidget.shouldRestrictToPublishedSearchableWebPages(widgetContext),
        "an admin+content-manager combination is still privileged");
  }

  // --- dateFacet (issue #634) ---

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheDateFacetParamAndOnlyListsBucketsWithResultsOrSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "dateFacet", "last7");

    try (MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<TableOfContentsRepository> tocRepository = mockStatic(TableOfContentsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      menuTabsCommand.when(LoadMenuTabsCommand::findAllActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      tocRepository.when(() -> TableOfContentsRepository.findAll(null, null)).thenReturn(new ArrayList<>());
      contentRepository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any())).thenReturn(null);
      // "last7" is the only bucket with a non-null start and a null end -- give it the only
      // non-zero count so the others must be omitted from dateFacets below
      contentRepository.when(() -> ContentRepository.countByDateRange(eq("widgets"), any(), any()))
          .thenAnswer(invocation -> {
            Timestamp start = invocation.getArgument(1);
            Timestamp end = invocation.getArgument(2);
            return (start != null && end == null) ? 5L : 0L;
          });

      WidgetContext result = new WebPageSearchResultsWidget().execute(widgetContext);

      List<FacetUrlCommand.FacetOption> dateFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("dateFacets");
      assertEquals(1, dateFacets.size(), "only the bucket with a non-zero count (or selected) should be listed");
      assertEquals("last7", dateFacets.get(0).getKey());
      assertEquals(5L, dateFacets.get(0).getCount());
      assertTrue(dateFacets.get(0).isSelected());

      List<FacetUrlCommand.ActiveFacetFilter> activeFilters = (List<FacetUrlCommand.ActiveFacetFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Last Updated", activeFilters.get(0).getFacetLabel());
    }
  }

  @Test
  void executeWithNoDateFacetSelectedHasNoActiveFilters() {
    addQueryParameter(widgetContext, "query", "widgets");

    try (MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<TableOfContentsRepository> tocRepository = mockStatic(TableOfContentsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      menuTabsCommand.when(LoadMenuTabsCommand::findAllActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      tocRepository.when(() -> TableOfContentsRepository.findAll(null, null)).thenReturn(new ArrayList<>());
      contentRepository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any())).thenReturn(null);
      contentRepository.when(() -> ContentRepository.countByDateRange(eq("widgets"), any(), any())).thenReturn(0L);

      WidgetContext result = new WebPageSearchResultsWidget().execute(widgetContext);

      assertTrue(((List<?>) result.getRequest().getAttribute("activeFilters")).isEmpty());
    }
  }

  @Test
  void executeSetsAnEmptySearchResultListWhenNoContentMatches() {
    // Regression check: the early-return path used to call finishRequest without ever setting
    // searchResultList, leaving it null instead of an empty collection.
    addQueryParameter(widgetContext, "query", "widgets");

    try (MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<TableOfContentsRepository> tocRepository = mockStatic(TableOfContentsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      menuTabsCommand.when(LoadMenuTabsCommand::findAllActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      tocRepository.when(() -> TableOfContentsRepository.findAll(null, null)).thenReturn(new ArrayList<>());
      contentRepository.when(() -> ContentRepository.findAll(any(ContentSpecification.class), any())).thenReturn(null);
      contentRepository.when(() -> ContentRepository.countByDateRange(eq("widgets"), any(), any())).thenReturn(0L);

      WidgetContext result = new WebPageSearchResultsWidget().execute(widgetContext);

      assertTrue(((java.util.Collection<?>) result.getRequest().getAttribute("searchResultList")).isEmpty());
      assertEquals(WebPageSearchResultsWidget.JSP, result.getJsp());
    }
  }
}