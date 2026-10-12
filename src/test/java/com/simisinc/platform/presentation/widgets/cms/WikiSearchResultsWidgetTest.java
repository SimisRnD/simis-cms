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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the wikiId facet added to WikiSearchResultsWidget (issue #634), and the fix to the
 * zero-result path that used to bail out before setting standard request attrs at all.
 *
 * @author SimIS Inc.
 */
class WikiSearchResultsWidgetTest extends WidgetBase {

  private static Wiki wiki(long id, String name) {
    Wiki wiki = new Wiki();
    wiki.setId(id);
    wiki.setName(name);
    wiki.setUniqueId("wiki-" + id);
    wiki.setEnabled(true);
    return wiki;
  }

  private static WikiPage wikiPage(long id, long wikiId) {
    WikiPage wikiPage = new WikiPage();
    wikiPage.setId(id);
    wikiPage.setWikiId(wikiId);
    wikiPage.setUniqueId("page-" + id);
    wikiPage.setTitle("Page " + id);
    return wikiPage;
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheWikiIdParamAndOnlyListsWikisWithResultsOrSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "wikiId", "5");

    List<WikiPage> wikiPageList = new ArrayList<>();
    wikiPageList.add(wikiPage(1L, 5L));

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(wikiPageList);
      wikiRepository.when(WikiRepository::findAll).thenReturn(List.of(wiki(5, "Engineering"), wiki(6, "Support")));
      wikiRepository.when(() -> WikiRepository.findById(5L)).thenReturn(wiki(5, "Engineering"));
      // Wiki 5 has results, wiki 6 does not and is not selected -- omitted entirely
      repository.when(() -> WikiPageRepository.findCount(any(WikiPageSpecification.class)))
          .thenAnswer(invocation -> {
            WikiPageSpecification spec = invocation.getArgument(0);
            return spec.getWikiId() == 5L ? 2L : 0L;
          });

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      List<FacetUrlCommand.FacetOption> wikiFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("wikiFacets");
      assertEquals(1, wikiFacets.size(), "wiki 6 has a 0 count and is not selected, so it must not be listed");
      assertEquals("Engineering", wikiFacets.get(0).getLabel());
      assertEquals(2L, wikiFacets.get(0).getCount());
      assertTrue(wikiFacets.get(0).isSelected());

      List<FacetUrlCommand.ActiveFacetFilter> activeFilters = (List<FacetUrlCommand.ActiveFacetFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Wiki", activeFilters.get(0).getFacetLabel());
      assertEquals("Engineering", activeFilters.get(0).getValueLabel());
    }
  }

  @Test
  void executeSetsStandardAttrsAndAnEmptySearchResultListWhenNothingMatches() {
    // Regression check for issue #634's fix: this used to return context before setting icon/
    // title/showPaging/returnPage/searchResultList at all, so the JSP never rendered anything --
    // not even a "no results" message.
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("title", "Wiki Search");
    preferences.put("icon", "fa-book");

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      assertEquals(WikiSearchResultsWidget.JSP, result.getJsp());
      assertEquals("Wiki Search", result.getRequest().getAttribute("title"));
      assertEquals("fa-book", result.getRequest().getAttribute("icon"));
      assertEquals("true", result.getRequest().getAttribute("showPaging"));
      assertTrue(((List<?>) result.getRequest().getAttribute("searchResultList")).isEmpty());
    }
  }

  @Test
  void executeReturnsNullForABlankQuery() {
    assertNull(new WikiSearchResultsWidget().execute(widgetContext));
  }

  @Test
  void executeStillShowsTheWidgetOnZeroResultsWhenShowWhenEmptyIsTrue() {
    // The web-template default (see "Search Results.xml") and this widget's own default when the
    // preference is absent -- unchanged current behavior.
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("showWhenEmpty", "true");

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      assertEquals(WikiSearchResultsWidget.JSP, result.getJsp());
    }
  }

  @Test
  void executeSuppressesTheWidgetOnZeroResultsWhenShowWhenEmptyIsFalse() {
    // The bug: this preference was never read at all, so a zero-result wiki search kept
    // rendering its "Documentation Found:" heading and empty-state text even though sibling
    // sections on the same search-results page (e.g. webPageSearchResults, blogPostSearchResults)
    // correctly disappeared with the identical preference set.
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("showWhenEmpty", "false");

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      assertNull(result.getJsp());
    }
  }

  @Test
  void executeStillShowsTheWidgetWhenShowWhenEmptyIsFalseButResultsExist() {
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("showWhenEmpty", "false");

    List<WikiPage> wikiPageList = new ArrayList<>();
    wikiPageList.add(wikiPage(1L, 5L));

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(wikiPageList);
      wikiRepository.when(WikiRepository::findAll).thenReturn(List.of(wiki(5, "Engineering")));
      wikiRepository.when(() -> WikiRepository.findById(5L)).thenReturn(wiki(5, "Engineering"));
      repository.when(() -> WikiPageRepository.findCount(any(WikiPageSpecification.class))).thenReturn(1L);

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      assertEquals(WikiSearchResultsWidget.JSP, result.getJsp());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeOnlySearchesAndListsWikisTheVisitorCanReach() {
    // A wiki whose route page the visitor cannot open (role, group or internal-page rules) must
    // contribute nothing: no results, no excerpts, no facet entry with its name and count
    addQueryParameter(widgetContext, "query", "steps");

    List<WikiPage> wikiPageList = new ArrayList<>();
    wikiPageList.add(wikiPage(1L, 5L));
    // Even if a page from the hidden wiki came back, it must not be rendered
    wikiPageList.add(wikiPage(2L, 6L));
    AtomicReference<WikiPageSpecification> searched = new AtomicReference<>();

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/wiki-5"), any())).thenReturn(true);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/wiki-6"), any())).thenReturn(false);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any()))
          .thenAnswer(invocation -> {
            searched.set(invocation.getArgument(0));
            return wikiPageList;
          });
      wikiRepository.when(WikiRepository::findAll).thenReturn(List.of(wiki(5, "Engineering"), wiki(6, "Operations")));
      repository.when(() -> WikiPageRepository.findCount(any(WikiPageSpecification.class))).thenReturn(1L);

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L), new ArrayList<>(searched.get().getWikiIdList()),
          "the query must be restricted to the reachable wiki");
      List<SearchResult> searchResultList = (List<SearchResult>) result.getRequest().getAttribute("searchResultList");
      assertEquals(1, searchResultList.size());
      assertEquals("/wiki-5/page-1", searchResultList.get(0).getLink());
      List<FacetUrlCommand.FacetOption> wikiFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("wikiFacets");
      assertEquals(1, wikiFacets.size());
      assertEquals("Engineering", wikiFacets.get(0).getLabel());
    }
  }

  @Test
  void executeHidesADisabledWikiFromVisitorsButNotFromAnAdmin() {
    // Same rule WikiWidget applies to viewing a disabled wiki
    addQueryParameter(widgetContext, "query", "steps");
    Wiki disabled = wiki(5, "Engineering");
    disabled.setEnabled(false);
    AtomicReference<WikiPageSpecification> searched = new AtomicReference<>();

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any()))
          .thenAnswer(invocation -> {
            searched.set(invocation.getArgument(0));
            return new ArrayList<>();
          });
      wikiRepository.when(WikiRepository::findAll).thenReturn(List.of(disabled));

      new WikiSearchResultsWidget().execute(widgetContext);
      assertTrue(searched.get().getWikiIdList().isEmpty(), "a guest must not search a disabled wiki");

      login(widgetContext);
      setRoles(widgetContext, ADMIN);
      new WikiSearchResultsWidget().execute(widgetContext);
      assertEquals(List.of(5L), new ArrayList<>(searched.get().getWikiIdList()), "an admin can open it, so can search it");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeDoesNotNameAHiddenWikiInTheActiveFilter() {
    // ?wikiId= for a wiki the visitor cannot open must not turn the filter chip into a way to read
    // that wiki's name
    addQueryParameter(widgetContext, "query", "steps");
    addQueryParameter(widgetContext, "wikiId", "6");

    try (MockedStatic<WikiPageRepository> repository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/wiki-5"), any())).thenReturn(true);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/wiki-6"), any())).thenReturn(false);
      repository.when(() -> WikiPageRepository.findAll(any(WikiPageSpecification.class), any())).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(List.of(wiki(5, "Engineering"), wiki(6, "Operations")));
      wikiRepository.when(() -> WikiRepository.findById(6L)).thenReturn(wiki(6, "Operations"));
      repository.when(() -> WikiPageRepository.findCount(any(WikiPageSpecification.class))).thenReturn(0L);

      WidgetContext result = new WikiSearchResultsWidget().execute(widgetContext);

      List<FacetUrlCommand.ActiveFacetFilter> activeFilters = (List<FacetUrlCommand.ActiveFacetFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Selected wiki", activeFilters.get(0).getValueLabel());
      List<FacetUrlCommand.FacetOption> wikiFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("wikiFacets");
      assertTrue(wikiFacets.stream().noneMatch(f -> "Operations".equals(f.getLabel())));
    }
  }
}
