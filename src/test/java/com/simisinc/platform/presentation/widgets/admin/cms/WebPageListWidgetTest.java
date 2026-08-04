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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;

/**
 * Tests the /admin/web-pages status-count summary (issue #497): a stable "X total -- Y live, Z
 * draft, ..." line that always reflects the full page set, independent of the active search/status
 * filter. Every page falls into exactly one of the four buckets, mirroring the same derivation the
 * existing status filters use.
 *
 * @author elizabeth houser
 */
class WebPageListWidgetTest extends WidgetBase {

  private WebPage draftPage() {
    WebPage webPage = new WebPage();
    webPage.setLink("/draft-page");
    webPage.setDraft(true);
    return webPage;
  }

  private WebPage redirectPage() {
    WebPage webPage = new WebPage();
    webPage.setLink("/old-page");
    webPage.setDraft(false);
    webPage.setRedirectUrl("/new-page");
    return webPage;
  }

  private WebPage livePage() {
    WebPage webPage = new WebPage();
    webPage.setLink("/about");
    webPage.setDraft(false);
    webPage.setPageXml("<page><section/></page>");
    return webPage;
  }

  private WebPage brokenPage() {
    // Not draft, no redirect, no page_xml, not a standard/built-in page (the mocked
    // ServletContext resolves no XML resources, so standardPages stays empty), not /directory/
    WebPage webPage = new WebPage();
    webPage.setLink("/broken-page");
    webPage.setDraft(false);
    return webPage;
  }

  @Test
  void statusCountsCoverEveryPageExactlyOnce() {
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(draftPage());
    webPageList.add(redirectPage());
    webPageList.add(livePage());
    webPageList.add(livePage());
    webPageList.add(brokenPage());

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageHitRepository> hitRepository = mockStatic(WebPageHitRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      menuTabsCommand.when(LoadMenuTabsCommand::findAllIncludeMenuItemList).thenReturn(new ArrayList<>());
      hitRepository.when(() -> WebPageHitRepository.countViewsByWebPageId(any(), anyInt())).thenReturn(new HashMap<>());

      new WebPageListWidget().execute(widgetContext);
    }

    assertEquals(5, request.getAttribute("webPageTotalCount"));
    assertEquals(2, request.getAttribute("webPageLiveCount"));
    assertEquals(1, request.getAttribute("webPageDraftCount"));
    assertEquals(1, request.getAttribute("webPageRedirectCount"));
    assertEquals(1, request.getAttribute("webPageBrokenCount"));
  }

  @Test
  void statusCountsStayStableWhenAStatusFilterIsActive() {
    List<WebPage> webPageList = new ArrayList<>();
    webPageList.add(draftPage());
    webPageList.add(livePage());
    webPageList.add(brokenPage());

    addQueryParameter(widgetContext, "status", "draft");
    List<WebPage> draftOnly = new ArrayList<>(List.of(draftPage()));

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageHitRepository> hitRepository = mockStatic(WebPageHitRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any())).thenReturn(draftOnly);
      menuTabsCommand.when(LoadMenuTabsCommand::findAllIncludeMenuItemList).thenReturn(new ArrayList<>());
      hitRepository.when(() -> WebPageHitRepository.countViewsByWebPageId(any(), anyInt())).thenReturn(new HashMap<>());

      new WebPageListWidget().execute(widgetContext);

      // The filtered display list narrows to just the draft page...
      List<WebPage> filtered = (List) request.getAttribute("webPageList");
      assertEquals(1, filtered.size());
    }

    // ...but the summary counts must still reflect the full, unfiltered set
    assertEquals(3, request.getAttribute("webPageTotalCount"));
    assertEquals(1, request.getAttribute("webPageLiveCount"));
    assertEquals(1, request.getAttribute("webPageDraftCount"));
    assertEquals(0, request.getAttribute("webPageRedirectCount"));
    assertEquals(1, request.getAttribute("webPageBrokenCount"));
  }

  @Test
  void emptyPageListProducesAllZeroCounts() {
    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageHitRepository> hitRepository = mockStatic(WebPageHitRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(new ArrayList<>());
      menuTabsCommand.when(LoadMenuTabsCommand::findAllIncludeMenuItemList).thenReturn(new ArrayList<>());
      hitRepository.when(() -> WebPageHitRepository.countViewsByWebPageId(any(), anyInt())).thenReturn(new HashMap<>());

      new WebPageListWidget().execute(widgetContext);
    }

    assertEquals(0, request.getAttribute("webPageTotalCount"));
    assertEquals(0, request.getAttribute("webPageLiveCount"));
    assertEquals(0, request.getAttribute("webPageDraftCount"));
    assertEquals(0, request.getAttribute("webPageRedirectCount"));
    assertEquals(0, request.getAttribute("webPageBrokenCount"));
  }

  @Test
  void viewCountMapIsPassedThroughFromTheRepositoryKeyedByWebPageId() {
    WebPage webPage = livePage();
    webPage.setId(42L);
    List<WebPage> webPageList = new ArrayList<>(List.of(webPage));
    Map<Long, Long> viewCounts = new HashMap<>();
    viewCounts.put(42L, 17L);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageHitRepository> hitRepository = mockStatic(WebPageHitRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      menuTabsCommand.when(LoadMenuTabsCommand::findAllIncludeMenuItemList).thenReturn(new ArrayList<>());
      hitRepository.when(() -> WebPageHitRepository.countViewsByWebPageId(List.of(42L), 30)).thenReturn(viewCounts);

      new WebPageListWidget().execute(widgetContext);
    }

    Map<Long, Long> viewCountMap = (Map<Long, Long>) request.getAttribute("webPageViewCountMap");
    assertEquals(17L, viewCountMap.get(42L));
  }

  @Test
  void viewCountMapCoversTheFullPageSetNotJustTheActiveFilterResults() {
    // The "In Navigation Menu" section always renders from the unfiltered webPageMap, independent
    // of the active search/status filter -- so a page excluded from the filtered "All Web Pages"
    // list below must still get a real view count, not a silent 0. Regression test for a review
    // finding: the count query was originally scoped to just the filtered list.
    WebPage draftPage = draftPage();
    draftPage.setId(1L);
    WebPage livePageExcludedByFilter = livePage();
    livePageExcludedByFilter.setId(2L);
    List<WebPage> webPageList = new ArrayList<>(List.of(draftPage, livePageExcludedByFilter));

    addQueryParameter(widgetContext, "status", "draft");
    List<WebPage> draftOnly = new ArrayList<>(List.of(draftPage));

    Map<Long, Long> viewCounts = new HashMap<>();
    viewCounts.put(1L, 3L);
    viewCounts.put(2L, 99L);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageHitRepository> hitRepository = mockStatic(WebPageHitRepository.class)) {
      repository.when(WebPageRepository::findAll).thenReturn(webPageList);
      repository.when(() -> WebPageRepository.findAll(any(WebPageSpecification.class), any())).thenReturn(draftOnly);
      menuTabsCommand.when(LoadMenuTabsCommand::findAllIncludeMenuItemList).thenReturn(new ArrayList<>());
      hitRepository.when(() -> WebPageHitRepository.countViewsByWebPageId(List.of(1L, 2L), 30)).thenReturn(viewCounts);

      new WebPageListWidget().execute(widgetContext);

      // The "All Web Pages" list narrows to just the draft page...
      List<WebPage> filtered = (List) request.getAttribute("webPageList");
      assertEquals(1, filtered.size());
    }

    // ...but the view-count map must still carry the excluded page's real count, since the
    // nav-menu section can still reference it via the unfiltered webPageMap
    Map<Long, Long> viewCountMap = (Map<Long, Long>) request.getAttribute("webPageViewCountMap");
    assertEquals(99L, viewCountMap.get(2L), "a page excluded by the active filter must not silently show 0 views");
  }
}
