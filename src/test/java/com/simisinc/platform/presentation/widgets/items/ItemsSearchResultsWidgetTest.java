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

package com.simisinc.platform.presentation.widgets.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.application.items.ItemDateFacetCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.items.ItemsSearchResultsWidget.ItemActiveFilter;
import com.simisinc.platform.presentation.widgets.items.ItemsSearchResultsWidget.ItemFacetOption;

/**
 * @author SimIS Inc.
 */
class ItemsSearchResultsWidgetTest extends WidgetBase {

  private static Category category(long id, String name) {
    Category category = new Category();
    category.setId(id);
    category.setName(name);
    return category;
  }

  private static Item item(long id, String uniqueId, String name) {
    Item item = new Item();
    item.setId(id);
    item.setUniqueId(uniqueId);
    item.setName(name);
    return item;
  }

  private static List<Category> categories(Category... categoryArray) {
    List<Category> categoryList = new ArrayList<>();
    for (Category category : categoryArray) {
      categoryList.add(category);
    }
    return categoryList;
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheCategoryIdParamAndOnlyListsCategoriesWithResultsOrSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "categoryId", "5");

    List<Item> itemList = new ArrayList<>();
    itemList.add(item(1L, "widget-1", "Widget One"));

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(itemList);
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets"), category(6, "Gadgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      Map<Long, Long> categoryCounts = new HashMap<>();
      categoryCounts.put(5L, 3L);
      // category 6 is intentionally absent -- countGroupedByCategory omits zero-count categories
      // entirely, the same as countByCategory returning 0 for one
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(categoryCounts);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(5L, specCaptor.getValue().getCategoryId(), "the categoryId param must reach the query, closing the gap the research found");

      List<ItemFacetOption> categoryFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("categoryFacets");
      assertEquals(1, categoryFacets.size(), "category 6 has a 0 count and is not selected, so it must not be listed");
      assertEquals("Widgets", categoryFacets.get(0).getLabel());
      assertEquals(3L, categoryFacets.get(0).getCount());
      assertTrue(categoryFacets.get(0).isSelected());

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Category", activeFilters.get(0).getFacetLabel());
      assertEquals("Widgets", activeFilters.get(0).getValueLabel());
      assertFalse(activeFilters.get(0).getClearUrl().contains("categoryId="),
          "the clear link must drop the categoryId param entirely: " + activeFilters.get(0).getClearUrl());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheDateFacetParamToTheQuery() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "dateFacet", "last7");

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      assertNotNull(specCaptor.getValue().getDateRangeStart(), "the last7 bucket's start must reach the query");
      assertNull(specCaptor.getValue().getDateRangeEnd(), "last7 is open-ended on the recent side");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeRoutesToTheJspAndShowsARemoveAFilterPromptWhenAFilteredSearchHasNoResults() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "categoryId", "5");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(new HashMap<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      // Before #421, an empty itemList returned without ever calling setJsp(), so the page rendered
      // as if the widget weren't there at all -- the exact "empty page" behavior the issue calls out
      assertNotNull(result.getJsp(), "a zero-result filtered search must still render the JSP so the 'remove a filter' prompt can show");

      List<Item> renderedItemList = (List<Item>) result.getRequest().getAttribute("itemList");
      assertTrue(renderedItemList.isEmpty());

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size(), "the active filter must still be surfaced so the user has something to remove");
      assertEquals("Selected category", activeFilters.get(0).getValueLabel(),
          "a 0-count categoryId must not disclose the real category name -- see executeDoesNotLeakAnInaccessibleOrEmptyCategorysName");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeDoesNotLeakAnInaccessibleOrEmptyCategorysNameInTheFacetListOrActiveFilterChip() {
    // categoryId=99 stands in for either a category with zero matching items, or one belonging to
    // a collection the requester has no access to -- countByCategory returns 0 in both cases
    // (access control is enforced in the same WHERE clause), so the two are indistinguishable and
    // neither may disclose the category's name (issue: categoryId is a guessable sequential id).
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "categoryId", "99");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(99, "Confidential HR Records")));
      // findById is stubbed to return the real name deliberately, to prove the widget does NOT
      // trust/render it once the count check below has failed
      categoryRepository.when(() -> CategoryRepository.findById(99L)).thenReturn(category(99, "Confidential HR Records"));
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(new HashMap<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemFacetOption> categoryFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("categoryFacets");
      assertTrue(categoryFacets.isEmpty(), "a 0-count category must not appear in the facet list even though it's selected");

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Selected category", activeFilters.get(0).getValueLabel(),
          "must not render \"Confidential HR Records\" -- that would disclose the category's name to a requester with no verified access to it");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeComputesDateFacetBucketsExactlyOncePerRequest() {
    // Before the fix, ItemDateFacetCommand.buckets() (which calls Instant.now() internally) was
    // called twice per request -- once to resolve the selected bucket from the "dateFacet" param,
    // once more to render the facet list -- so the two Instant.now() calls could disagree by a
    // few milliseconds. Computing it once and reusing the same list for both closes that gap.
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "dateFacet", "last7");

    List<ItemDateFacetCommand.DateFacetBucket> fixedBuckets = new ArrayList<>();
    fixedBuckets.add(new ItemDateFacetCommand.DateFacetBucket("last7", "Last 7 days",
        Timestamp.valueOf("2026-07-22 00:00:00"), null));

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<ItemDateFacetCommand> dateFacetCommand = mockStatic(ItemDateFacetCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      dateFacetCommand.when(ItemDateFacetCommand::buckets).thenReturn(fixedBuckets);
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      dateFacetCommand.verify(ItemDateFacetCommand::buckets, times(1));
    }
  }

  @Test
  void executeOmitsTheCategoryFacetWhenThePreferenceIsFalse() {
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("showCategoryFacet", "false");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      assertNull(result.getRequest().getAttribute("categoryFacets"));
    }
  }

  @Test
  void executeReturnsNullWhenNoQueryIsProvided() {
    WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);
    assertNull(result);
  }
}
