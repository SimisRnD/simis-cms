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

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

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
    searchAnalytics.setPagePath(resolveOriginatingPath(context.getRequest()));
    searchAnalytics.setFacetKey(facetKey);
    SearchAnalyticsRepository.save(searchAnalytics);
  }

  /** The page the visitor searched <em>from</em>, which is the whole point of page_path.
   *
   * <p>This was getRequestURI(), but a search executes on the results page, so every row recorded
   * the results page itself -- findTopSearchPaths and findTopZeroResultPaths, whose stated purpose
   * is "which pages are sending visitors into a search that comes up empty", could therefore only
   * ever report /search. The referring page is the one piece of information that makes those two
   * reports mean anything.
   *
   * <p>Referer is client-supplied, so it is only trusted when it names this same host: an off-site
   * referer falls back to the request URI rather than writing a third-party URL into an
   * admin-facing report. Only the path is kept -- never the query string, which on a search referral
   * carries the visitor's previous search terms. The result is truncated to page_path's VARCHAR(255).
   */
  static String resolveOriginatingPath(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String referer = request.getHeader("Referer");
    if (StringUtils.isBlank(referer)) {
      return requestUri;
    }
    try {
      URI refererUri = new URI(referer);
      String host = refererUri.getHost();
      if (host == null || !host.equalsIgnoreCase(request.getServerName())) {
        return requestUri;
      }
      String path = refererUri.getPath();
      if (StringUtils.isBlank(path)) {
        return requestUri;
      }
      return path.length() > 255 ? path.substring(0, 255) : path;
    } catch (URISyntaxException e) {
      return requestUri;
    }
  }
}
