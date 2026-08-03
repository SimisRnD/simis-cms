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
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
    // Determine the active tag filters (issue #632), same repeated-param checkbox-group pattern
    // as categoryId above.
    List<Long> selectedTagIds = new ArrayList<>();
    String[] tagIdParams = context.getParameterMap().get("tagId");
    if (tagIdParams != null) {
      for (String rawTagId : tagIdParams) {
        if (StringUtils.isNumeric(rawTagId)) {
          Long parsedTagId = Long.valueOf(rawTagId);
          if (!selectedTagIds.contains(parsedTagId)) {
            selectedTagIds.add(parsedTagId);
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
    if (!selectedTagIds.isEmpty()) {
      specification.setTagIds(selectedTagIds);
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
    if (!selectedTagIds.isEmpty()) {
      appliedFacetKeys.add("tagId");
    }
    if (selectedDateBucket != null) {
      appliedFacetKeys.add("dateFacet");
    }
    String facetKey = appliedFacetKeys.isEmpty() ? null : String.join(",", appliedFacetKeys);
    SearchAnalyticsCommand.record(context, query, "items", itemList == null ? 0 : itemList.size(), facetKey);

    // Facet panels + active filter chips (issue #421; tag facet is #632)
    boolean showCategoryFacet = !"false".equals(context.getPreferences().get("showCategoryFacet"));
    boolean showTagFacet = !"false".equals(context.getPreferences().get("showTagFacet"));
    boolean showDateFacet = !"false".equals(context.getPreferences().get("showDateFacet"));
    String categoryFacetLabel = context.getPreferences().getOrDefault("categoryFacetLabel", "Category");
    String tagFacetLabel = context.getPreferences().getOrDefault("tagFacetLabel", "Tag");
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
            String url = FacetUrlCommand.buildMultiSelectToggleUrl(context, "categoryId", selectedCategoryIds, category.getId());
            categoryFacets.add(new ItemFacetOption(String.valueOf(category.getId()), category.getName(), count, selected, url));
          }
        }
      }
      context.getRequest().setAttribute("categoryFacets", categoryFacets);
      context.getRequest().setAttribute("categoryFacetLabel", categoryFacetLabel);
    }

    // Resolve every tag's count in a single grouped query, up front -- same shape as
    // categoryCounts above (issue #632 mirrors issue #637's countGroupedByCategory exactly).
    // Unlike countGroupedByCategory, this map is not affected by the specification's own tag
    // selection at all (countGroupedByTag never reads it), so this single computation safely
    // backs both the facet list below AND the active-filter chip disclosure check further down --
    // no separate "no tag selection" specification is needed the way category's chip logic needs
    // one (see noCategorySelectionSpec below), because there is nothing to neutralize. It IS
    // affected by the specification's active category selection (issue #916, AND-across-dimensions)
    // -- exactly the standalone-but-combined-with-category count the chip disclosure check below
    // wants, so that forwarding doesn't change any of the reasoning above.
    Map<Long, Long> tagCounts = null;
    if (!selectedTagIds.isEmpty() || showTagFacet) {
      tagCounts = ItemRepository.countGroupedByTag(specification);
    }

    if (showTagFacet) {
      List<ItemFacetOption> tagFacets = new ArrayList<>();
      List<Tag> allTags = TagRepository.findAll();
      if (allTags != null) {
        for (Tag tag : allTags) {
          boolean selected = selectedTagIds.contains(tag.getId());
          long count = tagCounts.getOrDefault(tag.getId(), 0L);
          // Tags with a 0 count here are omitted entirely, selected or not -- same
          // access-control-non-disclosure reasoning as the category facet above: tagId is a
          // guessable sequential id, so an inaccessible or empty tag's name must not leak.
          if (count > 0) {
            String url = FacetUrlCommand.buildMultiSelectToggleUrl(context, "tagId", selectedTagIds, tag.getId());
            tagFacets.add(new ItemFacetOption(String.valueOf(tag.getId()), tag.getName(), count, selected, url));
          }
        }
      }
      context.getRequest().setAttribute("tagFacets", tagFacets);
      context.getRequest().setAttribute("tagFacetLabel", tagFacetLabel);
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
      // Issue #916: forward the active tag selection too (AND-across-dimensions) -- countByCategory
      // now folds the passed-in specification's own tag selection into its facet-count query, so
      // this standalone check must carry the real active tagIds, not leave them empty, or a
      // category's chip would keep disclosing a name that no longer has any results once combined
      // with the active tag filter.
      noCategorySelectionSpec.setTagIds(specification.getEffectiveTagIds());

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
        String clearUrl = FacetUrlCommand.buildMultiSelectToggleUrl(context, "categoryId", selectedCategoryIds, selectedCategoryId);
        activeFilters.add(new ItemActiveFilter(categoryFacetLabel, valueLabel, clearUrl));
      }
      if (selectedCategoryIds.size() > 1) {
        activeFilters.add(new ItemActiveFilter(categoryFacetLabel, "All categories", FacetUrlCommand.buildClearFilterUrl(context, "categoryId")));
      }
    }
    if (!selectedTagIds.isEmpty()) {
      // Unlike the category chip logic above, no separate "no selection" specification is needed
      // here: tagCounts (computed once, above, from countGroupedByTag) already ignores the
      // specification's own tag selection entirely -- see the comment on its computation -- so
      // it's already exactly the "this tag alone" standalone count the disclosure check needs.
      for (Long selectedTagId : selectedTagIds) {
        // Only resolve and show the real tag name once its own standalone count has confirmed it
        // is a non-empty, access-safe tag -- otherwise fall back to a generic label rather than
        // disclosing the name of a tag the requester may not have access to, or that doesn't
        // exist. Same reasoning as the category chip above.
        String valueLabel = "Selected tag";
        long standaloneCount = tagCounts.getOrDefault(selectedTagId, 0L);
        if (standaloneCount > 0) {
          Tag selectedTag = TagRepository.findById(selectedTagId);
          if (selectedTag != null) {
            valueLabel = selectedTag.getName();
          }
        }
        String clearUrl = FacetUrlCommand.buildMultiSelectToggleUrl(context, "tagId", selectedTagIds, selectedTagId);
        activeFilters.add(new ItemActiveFilter(tagFacetLabel, valueLabel, clearUrl));
      }
      if (selectedTagIds.size() > 1) {
        activeFilters.add(new ItemActiveFilter(tagFacetLabel, "All tags", FacetUrlCommand.buildClearFilterUrl(context, "tagId")));
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
