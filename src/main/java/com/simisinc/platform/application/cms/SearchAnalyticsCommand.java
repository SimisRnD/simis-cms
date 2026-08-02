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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.DoNotTrackCommand;
import com.simisinc.platform.domain.model.cms.SearchAnalytics;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared recording entry point for all six search-results widgets (issue #424). Each widget calls
 * record() once with its own search type and result count immediately after running its own query,
 * before any further access-control or navigation filtering narrows the list -- so the count reflects
 * what the search itself found, not who happened to be allowed to see it.
 *
 * @author SimIS
 * @created 7/29/2026
 */
public class SearchAnalyticsCommand {

  public static void record(WidgetContext context, String query, String searchType, int resultCount) {
    record(context, query, searchType, resultCount, null);
  }

  /**
   * @param facetKey which facet dimension(s) were applied to this search, e.g. "categoryId",
   *                 "dateFacet", or "categoryId,dateFacet" when more than one is active -- or null
   *                 when no facet was applied, or the widget has no facet concept (issue #638). A
   *                 facet click re-runs this same search with the facet param set, so there's no
   *                 separate "facet selected" event to fire -- the search event that included the
   *                 facet param already is that event.
   */
  public static void record(WidgetContext context, String query, String searchType, int resultCount, String facetKey) {
    if (StringUtils.isBlank(query)) {
      return;
    }

    // Same exclusion as SearchInfoWidget/web_searches: don't pollute content-gap analytics with an
    // editor's own searches while working in the CMS
    UserSession userSession = context.getUserSession();
    boolean isPrivilegedEditor = userSession != null && userSession.isLoggedIn()
        && (context.hasRole("admin") || context.hasRole("content-manager"));
    if (isPrivilegedEditor) {
      return;
    }

    if (DoNotTrackCommand.isDoNotTrack(context.getRequest().getHeader("DNT"), context.getRequest().getHeader("Sec-GPC"))) {
      return;
    }

    SearchAnalytics searchAnalytics = new SearchAnalytics();
    // Normalized so "Widget", "widget", and " widget " roll up into the same trending/zero-result term
    searchAnalytics.setQuery(query.toLowerCase().trim());
    searchAnalytics.setSearchType(searchType);
    searchAnalytics.setResultCount(Math.max(resultCount, 0));
    searchAnalytics.setPagePath(context.getRequest().getRequestURI());
    searchAnalytics.setFacetKey(facetKey);
    SearchAnalyticsRepository.save(searchAnalytics);
  }
}
