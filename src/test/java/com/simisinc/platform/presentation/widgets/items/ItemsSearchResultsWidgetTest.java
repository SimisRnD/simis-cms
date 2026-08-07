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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;
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

  private static Tag tag(long id, String name) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(name);
    return tag;
  }

  private static List<Tag> tags(Tag... tagArray) {
    List<Tag> tagList = new ArrayList<>();
    for (Tag tag : tagArray) {
      tagList.add(tag);
    }
    return tagList;
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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(itemList);
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets"), category(6, "Gadgets")));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      Map<Long, Long> categoryCounts = new HashMap<>();
      categoryCounts.put(5L, 3L);
      // category 6 is intentionally absent -- countGroupedByCategory omits zero-count categories
      // entirely, the same as countByCategory returning 0 for one
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(categoryCounts);
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L), specCaptor.getValue().getCategoryIds(),
          "the categoryId param must reach the query (via the issue #636 multi-select list), closing the gap the research found");

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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(99, "Confidential HR Records")));
      // findById is stubbed to return the real name deliberately, to prove the widget does NOT
      // trust/render it once the count check below has failed
      categoryRepository.when(() -> CategoryRepository.findById(99L)).thenReturn(category(99, "Confidential HR Records"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<ItemDateFacetCommand> dateFacetCommand = mockStatic(ItemDateFacetCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      dateFacetCommand.when(ItemDateFacetCommand::buckets).thenReturn(fixedBuckets);
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
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
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(new HashMap<>());
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

  @Test
  @SuppressWarnings("unchecked")
  void executeParsesRepeatedCategoryIdParamsIntoTheMultiSelectList() {
    // Issue #636: categoryId=5&categoryId=7 is how a checkbox group with a shared name naturally
    // serializes in a GET form submit -- read via getParameterMap() as a String[], same pattern as
    // the eventType checkbox group in the webhook admin panel's WebhookSubscriptionFormWidget.
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("categoryId", new String[] { "5", "7" });

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L, 7L), specCaptor.getValue().getCategoryIds(),
          "both repeated categoryId values must be parsed, in order, and reach the query");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeDropsANonNumericCategoryIdAndDedupesRepeatedValues() {
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("categoryId", new String[] { "5", "not-a-number", "5", "7" });

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L, 7L), specCaptor.getValue().getCategoryIds());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeBuildsAnAddToSelectionUrlForAnUncheckedCategoryAndARemoveUrlForAChecked() {
    // Issue #636: an unchecked category's facet link must ADD it to the current selection (keeping
    // whatever's already checked); an already-checked category's own facet link must REMOVE just
    // it.
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("categoryId", new String[] { "5" });

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets"), category(7, "Doohickeys")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(Map.of(5L, 1L, 7L, 1L));
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemFacetOption> categoryFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("categoryFacets");

      ItemFacetOption uncheckedFacet = categoryFacets.stream().filter(f -> "7".equals(f.getKey())).findFirst().orElseThrow();
      assertFalse(uncheckedFacet.isSelected());
      assertTrue(uncheckedFacet.getUrl().contains("categoryId=5") && uncheckedFacet.getUrl().contains("categoryId=7"),
          "checking an unchecked category must ADD it, keeping the already-selected categoryId=5: " + uncheckedFacet.getUrl());

      ItemFacetOption checkedFacet = categoryFacets.stream().filter(f -> "5".equals(f.getKey())).findFirst().orElseThrow();
      assertTrue(checkedFacet.isSelected());
      assertFalse(checkedFacet.getUrl().contains("categoryId="),
          "unchecking the only selected category must drop categoryId from the URL entirely: " + checkedFacet.getUrl());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeGivesEachSelectedCategoryItsOwnRemoveChipPlusAClearAllChipWhenMultipleAreSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("categoryId", new String[] { "5", "7" });

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets"), category(7, "Doohickeys")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      categoryRepository.when(() -> CategoryRepository.findById(7L)).thenReturn(category(7, "Doohickeys"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      // one chip per selected category, plus one "clear all categories" chip
      assertEquals(3, activeFilters.size());

      ItemActiveFilter widgetsChip = activeFilters.stream().filter(f -> "Widgets".equals(f.getValueLabel())).findFirst().orElseThrow();
      assertTrue(widgetsChip.getClearUrl().contains("categoryId=7"), "removing just Widgets must leave categoryId=7 selected: " + widgetsChip.getClearUrl());
      assertFalse(widgetsChip.getClearUrl().contains("categoryId=5"), "removing Widgets must drop its own id: " + widgetsChip.getClearUrl());

      ItemActiveFilter doohickeysChip = activeFilters.stream().filter(f -> "Doohickeys".equals(f.getValueLabel())).findFirst().orElseThrow();
      assertTrue(doohickeysChip.getClearUrl().contains("categoryId=5"), "removing just Doohickeys must leave categoryId=5 selected: " + doohickeysChip.getClearUrl());
      assertFalse(doohickeysChip.getClearUrl().contains("categoryId=7"), "removing Doohickeys must drop its own id: " + doohickeysChip.getClearUrl());

      boolean hasClearAllChip = activeFilters.stream().anyMatch(f -> !f.getClearUrl().contains("categoryId="));
      assertTrue(hasClearAllChip, "when 2+ categories are selected, one chip should clear the whole dimension");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeGivesASingleSelectedCategoryOnlyOneChipNoClearAll() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "categoryId", "5");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size(), "a single selection keeps the original one-chip-clears-it behavior, no separate 'clear all' chip");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeForwardsTheActiveTagSelectionIntoTheCategoryChipsStandaloneCheck() {
    // Issue #916: category and tag facet counts must AND against each other. The category
    // active-filter chip's standalone-count check (noCategorySelectionSpec in the widget) must
    // still carry the currently active tag selection, or an emptied-out-by-the-tag-filter category
    // would keep disclosing its name via the chip.
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("categoryId", new String[] { "5" });
    widgetContext.getParameterMap().put("tagId", new String[] { "100" });

    ArgumentCaptor<ItemSpecification> categorySpecCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countGroupedByCategory(any())).thenReturn(Map.of(5L, 1L));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(100L, 1L));
      repository.when(() -> ItemRepository.countByCategory(categorySpecCaptor.capture(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(100L), categorySpecCaptor.getValue().getEffectiveTagIds(),
          "the category chip's standalone check must forward the active tag selection");
    }
  }

  @Test
  void executeRecordsWhichFacetsWereAppliedForTheAdoptionRateReport() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "categoryId", "5");
    addQueryParameter(widgetContext, "dateFacet", "last7");

    ArgumentCaptor<String> facetKeyCaptor = ArgumentCaptor.forClass(String.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(categories(category(5, "Widgets")));
      categoryRepository.when(() -> CategoryRepository.findById(5L)).thenReturn(category(5, "Widgets"));
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByCategory(any(), anyLong())).thenReturn(1L);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(1L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      analytics.verify(() -> SearchAnalyticsCommand.record(any(), any(), any(), anyInt(), facetKeyCaptor.capture()));
      assertEquals("categoryId,dateFacet", facetKeyCaptor.getValue());
    }
  }

  @Test
  void executeRecordsNoFacetKeyWhenNoFacetWasSelected() {
    addQueryParameter(widgetContext, "query", "widgets");

    ArgumentCaptor<String> facetKeyCaptor = ArgumentCaptor.forClass(String.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      categoryRepository.when(CategoryRepository::findAll).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      analytics.verify(() -> SearchAnalyticsCommand.record(any(), any(), any(), anyInt(), facetKeyCaptor.capture()));
      assertNull(facetKeyCaptor.getValue());
    }
  }

  // Issue #632: the tag facet, mirroring the category facet tests above one dimension over.

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheTagIdParamAndOnlyListsTagsWithResultsOrSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "tagId", "5");

    List<Item> itemList = new ArrayList<>();
    itemList.add(item(1L, "widget-1", "Widget One"));

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(itemList);
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(5, "Fiction"), tag(6, "History")));
      tagRepository.when(() -> TagRepository.findById(5L)).thenReturn(tag(5, "Fiction"));
      Map<Long, Long> tagCounts = new HashMap<>();
      tagCounts.put(5L, 3L);
      // tag 6 is intentionally absent -- countGroupedByTag omits zero-count tags entirely, the
      // same as countGroupedByCategory
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(tagCounts);
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L), specCaptor.getValue().getTagIds(),
          "the tagId param must reach the query, via the issue #632 multi-select list");

      List<ItemFacetOption> tagFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("tagFacets");
      assertEquals(1, tagFacets.size(), "tag 6 has a 0 count and is not selected, so it must not be listed");
      assertEquals("Fiction", tagFacets.get(0).getLabel());
      assertEquals(3L, tagFacets.get(0).getCount());
      assertTrue(tagFacets.get(0).isSelected());

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Tag", activeFilters.get(0).getFacetLabel());
      assertEquals("Fiction", activeFilters.get(0).getValueLabel());
      assertFalse(activeFilters.get(0).getClearUrl().contains("tagId="),
          "the clear link must drop the tagId param entirely: " + activeFilters.get(0).getClearUrl());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeParsesRepeatedTagIdParamsIntoTheMultiSelectList() {
    // Same repeated-param checkbox-group parsing as categoryId (issue #636's pattern, reused here
    // for #632): tagId=5&tagId=7 read via getParameterMap() as a String[].
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("tagId", new String[] { "5", "7" });

    ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(5L, 1L, 7L, 1L));
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      assertEquals(List.of(5L, 7L), specCaptor.getValue().getTagIds(),
          "both repeated tagId values must be parsed, in order, and reach the query");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeBuildsAnAddToSelectionUrlForAnUncheckedTagAndARemoveUrlForAChecked() {
    // Issue #632: an unchecked tag's facet link must ADD it to the current selection (keeping
    // whatever's already checked); an already-checked tag's own facet link must REMOVE just it --
    // proves both add-to-selection and remove-from-selection toggling through the generalized
    // FacetUrlCommand.buildMultiSelectToggleUrl.
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("tagId", new String[] { "5" });

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(5, "Fiction"), tag(7, "History")));
      tagRepository.when(() -> TagRepository.findById(5L)).thenReturn(tag(5, "Fiction"));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(5L, 1L, 7L, 1L));
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemFacetOption> tagFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("tagFacets");

      ItemFacetOption uncheckedFacet = tagFacets.stream().filter(f -> "7".equals(f.getKey())).findFirst().orElseThrow();
      assertFalse(uncheckedFacet.isSelected());
      assertTrue(uncheckedFacet.getUrl().contains("tagId=5") && uncheckedFacet.getUrl().contains("tagId=7"),
          "checking an unchecked tag must ADD it, keeping the already-selected tagId=5: " + uncheckedFacet.getUrl());

      ItemFacetOption checkedFacet = tagFacets.stream().filter(f -> "5".equals(f.getKey())).findFirst().orElseThrow();
      assertTrue(checkedFacet.isSelected());
      assertFalse(checkedFacet.getUrl().contains("tagId="),
          "unchecking the only selected tag must drop tagId from the URL entirely: " + checkedFacet.getUrl());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeGivesEachSelectedTagItsOwnRemoveChipPlusAClearAllChipWhenMultipleAreSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    widgetContext.getParameterMap().put("tagId", new String[] { "5", "7" });

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(5, "Fiction"), tag(7, "History")));
      tagRepository.when(() -> TagRepository.findById(5L)).thenReturn(tag(5, "Fiction"));
      tagRepository.when(() -> TagRepository.findById(7L)).thenReturn(tag(7, "History"));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(5L, 1L, 7L, 1L));
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      // one chip per selected tag, plus one "clear all tags" chip
      assertEquals(3, activeFilters.size());

      ItemActiveFilter fictionChip = activeFilters.stream().filter(f -> "Fiction".equals(f.getValueLabel())).findFirst().orElseThrow();
      assertTrue(fictionChip.getClearUrl().contains("tagId=7"), "removing just Fiction must leave tagId=7 selected: " + fictionChip.getClearUrl());
      assertFalse(fictionChip.getClearUrl().contains("tagId=5"), "removing Fiction must drop its own id: " + fictionChip.getClearUrl());

      ItemActiveFilter historyChip = activeFilters.stream().filter(f -> "History".equals(f.getValueLabel())).findFirst().orElseThrow();
      assertTrue(historyChip.getClearUrl().contains("tagId=5"), "removing just History must leave tagId=5 selected: " + historyChip.getClearUrl());
      assertFalse(historyChip.getClearUrl().contains("tagId=7"), "removing History must drop its own id: " + historyChip.getClearUrl());

      boolean hasClearAllChip = activeFilters.stream().anyMatch(f -> !f.getClearUrl().contains("tagId="));
      assertTrue(hasClearAllChip, "when 2+ tags are selected, one chip should clear the whole dimension");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeGivesASingleSelectedTagOnlyOneChipNoClearAll() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "tagId", "5");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(5, "Fiction")));
      tagRepository.when(() -> TagRepository.findById(5L)).thenReturn(tag(5, "Fiction"));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(5L, 1L));
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size(), "a single selection keeps the original one-chip-clears-it behavior, no separate 'clear all' chip");
    }
  }

  @Test
  void executeOmitsTheTagFacetWhenThePreferenceIsFalse() {
    addQueryParameter(widgetContext, "query", "widgets");
    preferences.put("showTagFacet", "false");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      // TagRepository is never stubbed above -- with showTagFacet=false and no tagId param
      // selected, the widget must never even call TagRepository.findAll()/countGroupedByTag(),
      // the same "don't touch it at all" contract executeOmitsTheCategoryFacetWhenThePreferenceIsFalse
      // proves for the category facet.
      assertNull(result.getRequest().getAttribute("tagFacets"));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeDoesNotLeakAnInaccessibleOrEmptyTagsNameInTheFacetListOrActiveFilterChip() {
    // tagId=99 stands in for either a tag with zero matching items, or one belonging to a
    // collection the requester has no access to -- countGroupedByTag applies the same
    // access-control WHERE as the real query, so the two are indistinguishable and neither may
    // disclose the tag's name (issue: tagId is a guessable sequential id). Mirrors
    // executeDoesNotLeakAnInaccessibleOrEmptyCategorysNameInTheFacetListOrActiveFilterChip.
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "tagId", "99");

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(99, "Confidential Research Notes")));
      // findById is stubbed to return the real name deliberately, to prove the widget does NOT
      // trust/render it once the count check below has failed
      tagRepository.when(() -> TagRepository.findById(99L)).thenReturn(tag(99, "Confidential Research Notes"));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(new HashMap<>());
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      WidgetContext result = new ItemsSearchResultsWidget().execute(widgetContext);

      List<ItemFacetOption> tagFacets = (List<ItemFacetOption>) result.getRequest().getAttribute("tagFacets");
      assertTrue(tagFacets.isEmpty(), "a 0-count tag must not appear in the facet list even though it's selected");

      List<ItemActiveFilter> activeFilters = (List<ItemActiveFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Selected tag", activeFilters.get(0).getValueLabel(),
          "must not render \"Confidential Research Notes\" -- that would disclose the tag's name to a requester with no verified access to it");
    }
  }

  @Test
  void executeRecordsTagIdAsAnAppliedFacetKeyForTheAdoptionRateReport() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "tagId", "5");

    ArgumentCaptor<String> facetKeyCaptor = ArgumentCaptor.forClass(String.class);

    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // ItemDateFacetCommand.buckets() now reads the site timezone through
      // FormatDateCommand.getSiteZoneId(), which calls the two-arg loadByName(name,
      // defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      tagRepository.when(TagRepository::findAll).thenReturn(tags(tag(5, "Fiction")));
      tagRepository.when(() -> TagRepository.findById(5L)).thenReturn(tag(5, "Fiction"));
      repository.when(() -> ItemRepository.countGroupedByTag(any())).thenReturn(Map.of(5L, 1L));
      repository.when(() -> ItemRepository.countByDateRange(any(), any(), any())).thenReturn(0L);

      new ItemsSearchResultsWidget().execute(widgetContext);

      analytics.verify(() -> SearchAnalyticsCommand.record(any(), any(), any(), anyInt(), facetKeyCaptor.capture()));
      assertEquals("tagId", facetKeyCaptor.getValue());
    }
  }
}
