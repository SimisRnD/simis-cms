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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.application.items.ItemDateFacetCommand;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/27/18 4:27 PM
 */
public class ItemsSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/items-integrated-search-results-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "15"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    String sortBy = context.getPreferences().get("sortBy");
    if ("new".equals(sortBy)) {
      constraints.setColumnToSortBy("created", "desc");
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the search term
    String query = context.getParameter("query");
    if (StringUtils.isBlank(query)) {
      return null;
    }

    // Determine the location
    String location = context.getParameter("location");

    // Determine the active facet filters (issue #421; multi-select within categoryId is #636)
    // Repeated categoryId params (categoryId=1&categoryId=2) are how a checkbox group with a
    // shared name naturally serializes in a GET form submit -- read via getParameterMap() as a
    // String[], the same pattern already used for the eventType checkbox group in the webhook
    // admin panel's WebhookSubscriptionFormWidget (issue #453). Order is preserved and duplicates
    // are dropped.
    List<Long> selectedCategoryIds = new ArrayList<>();
    String[] categoryIdParams = context.getParameterMap().get("categoryId");
    if (categoryIdParams != null) {
      for (String rawCategoryId : categoryIdParams) {
        if (StringUtils.isNumeric(rawCategoryId)) {
          Long parsedCategoryId = Long.valueOf(rawCategoryId);
          if (!selectedCategoryIds.contains(parsedCategoryId)) {
            selectedCategoryIds.add(parsedCategoryId);
          }
        }
      }
    }
    // Computed once and reused below for both filtering and facet-count rendering, so the date
    // boundaries used to narrow the query and the ones used to report that same bucket's count
    // can never drift apart by the few milliseconds between two separate "now" calls
    List<ItemDateFacetCommand.DateFacetBucket> dateBuckets = ItemDateFacetCommand.buckets();
    String dateFacetParam = context.getParameter("dateFacet");
    ItemDateFacetCommand.DateFacetBucket selectedDateBucket = null;
    for (ItemDateFacetCommand.DateFacetBucket bucket : dateBuckets) {
      if (bucket.getKey().equals(dateFacetParam)) {
        selectedDateBucket = bucket;
        break;
      }
    }

    // Determine criteria
    ItemSpecification specification = new ItemSpecification();
    //    specification.setCollectionId(collection.getId());
    specification.setForUserId(context.getUserId());
    if (!context.hasRole("admin") && !context.hasRole("data-manager")) {
      specification.setApprovedOnly(true);
    }
    specification.setSearchName(query);
    if (StringUtils.isNotBlank(location)) {
      specification.setSearchLocation(location);
      specification.setWithinMeters(48281);
    }
    if (!selectedCategoryIds.isEmpty()) {
      specification.setCategoryIds(selectedCategoryIds);
    }
    if (selectedDateBucket != null) {
      specification.setDateRangeStart(selectedDateBucket.getStart());
      specification.setDateRangeEnd(selectedDateBucket.getEnd());
    }

    // Determine how the view will show the item's link
    boolean useItemLink = "true".equals(context.getPreferences().getOrDefault("useItemLink", "false"));

    // Query the data
    List<Item> itemList = ItemRepository.findAll(specification, constraints);
    // Which facet dimension(s) narrowed this search, for the facet-adoption-rate report (issue #638)
    List<String> appliedFacetKeys = new ArrayList<>();
    if (!selectedCategoryIds.isEmpty()) {
      appliedFacetKeys.add("categoryId");
    }
    if (selectedDateBucket != null) {
      appliedFacetKeys.add("dateFacet");
    }
    String facetKey = appliedFacetKeys.isEmpty() ? null : String.join(",", appliedFacetKeys);
    SearchAnalyticsCommand.record(context, query, "items", itemList == null ? 0 : itemList.size(), facetKey);

    // Facet panels + active filter chips (issue #421)
    boolean showCategoryFacet = !"false".equals(context.getPreferences().get("showCategoryFacet"));
    boolean showDateFacet = !"false".equals(context.getPreferences().get("showDateFacet"));
    String categoryFacetLabel = context.getPreferences().getOrDefault("categoryFacetLabel", "Category");
    String dateFacetLabel = context.getPreferences().getOrDefault("dateFacetLabel", "Date");

    // Resolve every category's count in a single grouped query, up front (issue #637 -- replaces
    // what used to be one ItemRepository.countByCategory round trip per candidate category).
    // countGroupedByCategory already applies the same access-control WHERE as the real query, so a
    // missing/0 entry here is indistinguishable between "genuinely no matches" and "this
    // categoryId belongs to a collection the requester can't see at all". categoryId is a
    // guessable sequential id, so neither case may disclose the category's name below -- only a
    // verified non-zero, access-safe count may.
    Map<Long, Long> categoryCounts = null;
    if (!selectedCategoryIds.isEmpty() || showCategoryFacet) {
      categoryCounts = ItemRepository.countGroupedByCategory(specification);
    }

    if (showCategoryFacet) {
      List<ItemFacetOption> categoryFacets = new ArrayList<>();
      List<Category> allCategories = CategoryRepository.findAll();
      if (allCategories != null) {
        for (Category category : allCategories) {
          boolean selected = selectedCategoryIds.contains(category.getId());
          long count = categoryCounts.getOrDefault(category.getId(), 0L);
          // Categories with a 0 count here are omitted entirely, selected or not -- see the
          // access-control note above for why "selected" alone must not be enough to reveal a
          // category that turns out to be empty or inaccessible.
          if (count > 0) {
            String url = buildCategoryToggleUrl(context, selectedCategoryIds, category.getId());
            categoryFacets.add(new ItemFacetOption(String.valueOf(category.getId()), category.getName(), count, selected, url));
          }
        }
      }
      context.getRequest().setAttribute("categoryFacets", categoryFacets);
      context.getRequest().setAttribute("categoryFacetLabel", categoryFacetLabel);
    }

    if (showDateFacet) {
      List<ItemFacetOption> dateFacets = new ArrayList<>();
      for (ItemDateFacetCommand.DateFacetBucket bucket : dateBuckets) {
        boolean selected = selectedDateBucket != null && selectedDateBucket.getKey().equals(bucket.getKey());
        long count = ItemRepository.countByDateRange(specification, bucket.getStart(), bucket.getEnd());
        if (count > 0 || selected) {
          String url = FacetUrlCommand.buildFacetLinkUrl(context, "dateFacet", bucket.getKey());
          dateFacets.add(new ItemFacetOption(bucket.getKey(), bucket.getLabel(), count, selected, url));
        }
      }
      context.getRequest().setAttribute("dateFacets", dateFacets);
      context.getRequest().setAttribute("dateFacetLabel", dateFacetLabel);
    }

    // Active filter chips, each with a URL that clears just that one filter (issue #636: with
    // multiple categories selected, that's one chip per selected category -- removing one via its
    // own chip leaves the others active -- plus, once 2+ are selected, one "clear all categories"
    // chip; a single selection keeps the original one-chip-clears-it behavior).
    List<ItemActiveFilter> activeFilters = new ArrayList<>();
    if (!selectedCategoryIds.isEmpty()) {
      // A specification with no category selection at all, used to verify each selected category
      // STANDALONE (ignoring the rest of the current selection) before disclosing its name --
      // countByCategory unions its candidate into whatever's selected on the specification passed
      // in, so passing one with nothing selected yields "this category alone"'s count, the same
      // access-control-safe check the original single-select code performed.
      ItemSpecification noCategorySelectionSpec = new ItemSpecification();
      noCategorySelectionSpec.setApprovedOnly(specification.getApprovedOnly());
      noCategorySelectionSpec.setUnapprovedOnly(specification.getUnapprovedOnly());
      noCategorySelectionSpec.setIncludeArchived(specification.getIncludeArchived());
      noCategorySelectionSpec.setForUserId(specification.getForUserId());
      noCategorySelectionSpec.setSearchName(specification.getSearchName());
      noCategorySelectionSpec.setSearchLocation(specification.getSearchLocation());
      noCategorySelectionSpec.setDateRangeStart(specification.getDateRangeStart());
      noCategorySelectionSpec.setDateRangeEnd(specification.getDateRangeEnd());

      for (Long selectedCategoryId : selectedCategoryIds) {
        // Only resolve and show the real category name once its own standalone count has
        // confirmed it is a non-empty, access-safe category (see the note above) -- otherwise fall
        // back to a generic label rather than disclosing the name of a category the requester may
        // not have access to, or that doesn't exist.
        String valueLabel = "Selected category";
        long standaloneCount = ItemRepository.countByCategory(noCategorySelectionSpec, selectedCategoryId);
        if (standaloneCount > 0) {
          Category selectedCategory = CategoryRepository.findById(selectedCategoryId);
          if (selectedCategory != null) {
            valueLabel = selectedCategory.getName();
          }
        }
        String clearUrl = buildCategoryToggleUrl(context, selectedCategoryIds, selectedCategoryId);
        activeFilters.add(new ItemActiveFilter(categoryFacetLabel, valueLabel, clearUrl));
      }
      if (selectedCategoryIds.size() > 1) {
        activeFilters.add(new ItemActiveFilter(categoryFacetLabel, "All categories", FacetUrlCommand.buildClearFilterUrl(context, "categoryId")));
      }
    }
    if (selectedDateBucket != null) {
      activeFilters.add(new ItemActiveFilter(dateFacetLabel, selectedDateBucket.getLabel(), FacetUrlCommand.buildClearFilterUrl(context, "dateFacet")));
    }
    context.getRequest().setAttribute("activeFilters", activeFilters);

    if (itemList == null || itemList.isEmpty()) {
      context.getRequest().setAttribute("itemList", new ArrayList<Item>());
      context.getRequest().setAttribute("searchResultList", new ArrayList<SearchResult>());
      context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
      context.getRequest().setAttribute("title", context.getPreferences().get("title"));
      context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
      context.setJsp(JSP);
      return context;
    }
    context.getRequest().setAttribute("itemList", itemList);

    List<SearchResult> searchResultList = new ArrayList<>();
    for (Item item : itemList) {
      // Add the search result
      SearchResult searchResult = new SearchResult();
      searchResult.setPageTitle(item.getName());
      if (useItemLink && StringUtils.isNotBlank(item.getUrl())
          && (item.getUrl().startsWith("http://") || item.getUrl().startsWith("https://"))) {
        searchResult.setLink(item.getUrl());
      } else {
        searchResult.setLink(context.getContextPath() + "/show/" + item.getUniqueId());
      }
      if (StringUtils.isNotBlank(item.getSummary())) {
        searchResult.setPageDescription(item.getSummary());
      }
      // Include an excerpt
      String htmlContent = HtmlCommand.toHtml(item.getHighlight());
      if (StringUtils.isNotBlank(htmlContent)) {
        htmlContent = StringUtils.replace(htmlContent, "${b}", "<strong>");
        htmlContent = StringUtils.replace(htmlContent, "${/b}", "</strong>");
        searchResult.setHtmlExcerpt(htmlContent);
      }
      searchResultList.add(searchResult);
    }
    context.getRequest().setAttribute("searchResultList", searchResultList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
    context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * The current request's URL with candidateCategoryId toggled in or out of the categoryId
   * selection (issue #636): added if it's not in currentSelection, removed if it is, every OTHER
   * currently selected categoryId preserved. This one method backs both the facet checkbox links
   * (toggling an unchecked category on, or an already-checked one off) and each active-filter
   * chip's "remove just this one" link -- removing a selected category via its chip is exactly the
   * same toggle-off operation as unchecking its facet checkbox. Category is the only multi-select
   * facet in this codebase, so this stays private here rather than in the shared FacetUrlCommand
   * (issue #634) alongside the single-select buildFacetLinkUrl/buildClearFilterUrl it still uses.
   */
  private static String buildCategoryToggleUrl(WidgetContext context, List<Long> currentSelection, long candidateCategoryId) {
    List<String> newSelection = new ArrayList<>();
    boolean removed = false;
    for (Long selectedId : currentSelection) {
      if (selectedId == candidateCategoryId) {
        removed = true; // omitting it from newSelection toggles it off
        continue;
      }
      newSelection.add(String.valueOf(selectedId));
    }
    if (!removed) {
      newSelection.add(String.valueOf(candidateCategoryId)); // toggles it on
    }
    Map<String, List<String>> overrides = new LinkedHashMap<>();
    if (!newSelection.isEmpty()) {
      overrides.put("categoryId", newSelection);
    }
    // When newSelection ends up empty, categoryId is simply absent from overrides -- combined with
    // excludeParam below dropping the current categoryId values, the result is the same as
    // FacetUrlCommand.buildClearFilterUrl: the param disappears from the URL entirely.
    return FacetUrlCommand.buildUrl(context, overrides, "categoryId");
  }

  /** One facet's rendered option: display label, result count, whether it's currently selected, and its link. */
  public static class ItemFacetOption {
    private final String key;
    private final String label;
    private final long count;
    private final boolean selected;
    private final String url;

    public ItemFacetOption(String key, String label, long count, boolean selected, String url) {
      this.key = key;
      this.label = label;
      this.count = count;
      this.selected = selected;
      this.url = url;
    }

    public String getKey() {
      return key;
    }

    public String getLabel() {
      return label;
    }

    public long getCount() {
      return count;
    }

    public boolean isSelected() {
      return selected;
    }

    public String getUrl() {
      return url;
    }
  }

  /** One active-filter chip: which facet dimension, the selected value's label, and the URL to clear just this filter. */
  public static class ItemActiveFilter {
    private final String facetLabel;
    private final String valueLabel;
    private final String clearUrl;

    public ItemActiveFilter(String facetLabel, String valueLabel, String clearUrl) {
      this.facetLabel = facetLabel;
      this.valueLabel = valueLabel;
      this.clearUrl = clearUrl;
    }

    public String getFacetLabel() {
      return facetLabel;
    }

    public String getValueLabel() {
      return valueLabel;
    }

    public String getClearUrl() {
      return clearUrl;
    }
  }
}
