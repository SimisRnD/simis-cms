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

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.application.cms.UrlCommand;
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

    // Determine the active facet filters (issue #421)
    Long categoryId = null;
    String categoryIdParam = context.getParameter("categoryId");
    if (StringUtils.isNumeric(categoryIdParam)) {
      categoryId = Long.valueOf(categoryIdParam);
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
    if (categoryId != null) {
      specification.setCategoryId(categoryId);
    }
    if (selectedDateBucket != null) {
      specification.setDateRangeStart(selectedDateBucket.getStart());
      specification.setDateRangeEnd(selectedDateBucket.getEnd());
    }

    // Determine how the view will show the item's link
    boolean useItemLink = "true".equals(context.getPreferences().getOrDefault("useItemLink", "false"));

    // Query the data
    List<Item> itemList = ItemRepository.findAll(specification, constraints);
    SearchAnalyticsCommand.record(context, query, "items", itemList == null ? 0 : itemList.size());

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
    if (categoryId != null || showCategoryFacet) {
      categoryCounts = ItemRepository.countGroupedByCategory(specification);
    }
    Long selectedCategoryCount = categoryId != null ? categoryCounts.getOrDefault(categoryId, 0L) : null;

    if (showCategoryFacet) {
      List<ItemFacetOption> categoryFacets = new ArrayList<>();
      List<Category> allCategories = CategoryRepository.findAll();
      if (allCategories != null) {
        for (Category category : allCategories) {
          boolean selected = categoryId != null && categoryId.equals(category.getId());
          long count = categoryCounts.getOrDefault(category.getId(), 0L);
          // Categories with a 0 count here are omitted entirely, selected or not -- see the
          // access-control note on selectedCategoryCount above for why "selected" alone must not
          // be enough to reveal a category that turns out to be empty or inaccessible.
          if (count > 0) {
            String url = buildFacetLinkUrl(context, "categoryId", String.valueOf(category.getId()));
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
          String url = buildFacetLinkUrl(context, "dateFacet", bucket.getKey());
          dateFacets.add(new ItemFacetOption(bucket.getKey(), bucket.getLabel(), count, selected, url));
        }
      }
      context.getRequest().setAttribute("dateFacets", dateFacets);
      context.getRequest().setAttribute("dateFacetLabel", dateFacetLabel);
    }

    // Active filter chips, each with a URL that clears just that one filter
    List<ItemActiveFilter> activeFilters = new ArrayList<>();
    if (categoryId != null) {
      // Only resolve and show the real category name once selectedCategoryCount has confirmed it
      // is a non-empty, access-safe category (see the note above) -- otherwise fall back to a
      // generic label rather than disclosing the name of a category the requester may not have
      // access to, or that doesn't exist.
      String valueLabel = "Selected category";
      if (selectedCategoryCount != null && selectedCategoryCount > 0) {
        Category selectedCategory = CategoryRepository.findById(categoryId);
        if (selectedCategory != null) {
          valueLabel = selectedCategory.getName();
        }
      }
      activeFilters.add(new ItemActiveFilter(categoryFacetLabel, valueLabel, buildClearFilterUrl(context, "categoryId")));
    }
    if (selectedDateBucket != null) {
      activeFilters.add(new ItemActiveFilter(dateFacetLabel, selectedDateBucket.getLabel(), buildClearFilterUrl(context, "dateFacet")));
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
   * The current request's URL with the given param set to the given value, all other current
   * params preserved, and paging reset to page 1 (a facet selection changes the result set, so
   * whatever page the user was on may no longer exist).
   */
  private static String buildFacetLinkUrl(WidgetContext context, String paramName, String paramValue) {
    Map<String, String> overrides = new LinkedHashMap<>();
    overrides.put(paramName, paramValue);
    return buildUrl(context, overrides, paramName);
  }

  /** The current request's URL with the given param removed, all other current params preserved. */
  private static String buildClearFilterUrl(WidgetContext context, String paramName) {
    return buildUrl(context, new LinkedHashMap<>(), paramName);
  }

  private static String buildUrl(WidgetContext context, Map<String, String> overrides, String excludeParam) {
    LinkedHashMap<String, String> params = new LinkedHashMap<>();
    for (Map.Entry<String, String[]> entry : context.getParameterMap().entrySet()) {
      String name = entry.getKey();
      if (name.equals(excludeParam) || "page".equals(name)) {
        continue;
      }
      if (entry.getValue() != null && entry.getValue().length > 0 && StringUtils.isNotBlank(entry.getValue()[0])) {
        params.put(name, entry.getValue()[0]);
      }
    }
    params.putAll(overrides);

    StringBuilder url = new StringBuilder(context.getUri());
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      url.append(first ? '?' : '&');
      first = false;
      url.append(UrlCommand.encodeUri(entry.getKey())).append('=').append(UrlCommand.encodeUri(entry.getValue()));
    }
    return url.toString();
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
