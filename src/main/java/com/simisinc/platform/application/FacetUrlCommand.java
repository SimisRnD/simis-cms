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

package com.simisinc.platform.application;

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared URL-building helpers for search-results facet links (issue #634), extracted from
 * ItemsSearchResultsWidget's original issue #421 implementation once a second widget needed the
 * same single-select facet-link/clear-filter behavior, per that issue's own suggestion rather than
 * copy-pasting a third time. Multi-select toggle behavior (buildMultiSelectToggleUrl, issue #636's
 * categoryId checkbox group) was originally kept private to ItemsSearchResultsWidget as
 * buildCategoryToggleUrl, on the reasoning that category was the only multi-select facet in this
 * codebase; generalized here for issue #632's tag facet, its second multi-select consumer, per the
 * same extract-on-the-second-user precedent this class itself was created under.
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class FacetUrlCommand {

  /**
   * The current request's URL with the given single-value param set to the given value, all other
   * current params preserved, and paging reset to page 1 (a facet selection changes the result
   * set, so whatever page the user was on may no longer exist).
   */
  public static String buildFacetLinkUrl(WidgetContext context, String paramName, String paramValue) {
    Map<String, List<String>> overrides = new LinkedHashMap<>();
    overrides.put(paramName, Collections.singletonList(paramValue));
    return buildUrl(context, overrides, paramName);
  }

  /** The current request's URL with the given param removed entirely, all other current params preserved. */
  public static String buildClearFilterUrl(WidgetContext context, String paramName) {
    return buildUrl(context, new LinkedHashMap<>(), paramName);
  }

  /**
   * The current request's URL with excludeParam's current value(s) dropped and replaced by
   * overrides (if any), every OTHER param preserved with EVERY one of its repeated values (not
   * just the first) -- so, for example, clicking a dateFacet link doesn't silently drop all but
   * one of the currently selected categoryId values -- and paging reset to page 1. Public so
   * ItemsSearchResultsWidget's own multi-select toggle can reuse this same engine.
   */
  public static String buildUrl(WidgetContext context, Map<String, List<String>> overrides, String excludeParam) {
    LinkedHashMap<String, List<String>> params = new LinkedHashMap<>();
    for (Map.Entry<String, String[]> entry : context.getParameterMap().entrySet()) {
      String name = entry.getKey();
      if (name.equals(excludeParam) || "page".equals(name)) {
        continue;
      }
      if (entry.getValue() == null) {
        continue;
      }
      List<String> values = new ArrayList<>();
      for (String value : entry.getValue()) {
        if (StringUtils.isNotBlank(value)) {
          values.add(value);
        }
      }
      if (!values.isEmpty()) {
        params.put(name, values);
      }
    }
    params.putAll(overrides);

    StringBuilder url = new StringBuilder(context.getUri());
    boolean first = true;
    for (Map.Entry<String, List<String>> entry : params.entrySet()) {
      for (String value : entry.getValue()) {
        url.append(first ? '?' : '&');
        first = false;
        url.append(UrlCommand.encodeUri(entry.getKey())).append('=').append(UrlCommand.encodeUri(value));
      }
    }
    return url.toString();
  }

  /**
   * The current request's URL with candidateValue toggled in or out of paramName's current
   * multi-select selection (issue #636, generalized for #632): added if it's not in
   * currentSelection, removed if it is, every OTHER currently selected value for paramName
   * preserved. This one method backs both a multi-select facet's checkbox links (toggling an
   * unchecked option on, or an already-checked one off) and each of its active-filter chips'
   * "remove just this one" link -- removing a selected value via its chip is exactly the same
   * toggle-off operation as unchecking its facet checkbox.
   */
  public static String buildMultiSelectToggleUrl(WidgetContext context, String paramName, List<Long> currentSelection,
      long candidateValue) {
    List<String> newSelection = new ArrayList<>();
    boolean removed = false;
    for (Long selectedId : currentSelection) {
      if (selectedId == candidateValue) {
        removed = true; // omitting it from newSelection toggles it off
        continue;
      }
      newSelection.add(String.valueOf(selectedId));
    }
    if (!removed) {
      newSelection.add(String.valueOf(candidateValue)); // toggles it on
    }
    Map<String, List<String>> overrides = new LinkedHashMap<>();
    if (!newSelection.isEmpty()) {
      overrides.put(paramName, newSelection);
    }
    // When newSelection ends up empty, paramName is simply absent from overrides -- combined with
    // excludeParam below dropping the current values, the result is the same as
    // buildClearFilterUrl: the param disappears from the URL entirely.
    return buildUrl(context, overrides, paramName);
  }

  /** One facet's rendered option: display label, result count, whether it's currently selected, and its link. */
  public static class FacetOption {
    private final String key;
    private final String label;
    private final long count;
    private final boolean selected;
    private final String url;

    public FacetOption(String key, String label, long count, boolean selected, String url) {
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
  public static class ActiveFacetFilter {
    private final String facetLabel;
    private final String valueLabel;
    private final String clearUrl;

    public ActiveFacetFilter(String facetLabel, String valueLabel, String clearUrl) {
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
