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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Returns search results for wiki pages, across all wikis. wiki_pages.tsv has been populated and
 * GIN-indexed since the initial code drop; this is the first caller that queries it.
 *
 * @author SimIS
 * @created 7/28/2026
 */
public class WikiSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/cms/wiki-search-results-list.jsp";
  static String WIKI_FACET_LABEL = "Wiki";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "15"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the search term
    String query = context.getParameter("query");
    if (StringUtils.isBlank(query)) {
      return null;
    }

    // Determine the active facet filter (issue #634; wikiId is fully supported by
    // WikiPageRepository, single-select since a page belongs to exactly one wiki)
    long selectedWikiId = context.getParameterAsLong("wikiId", -1);

    // Only the wikis this visitor could open. Results, facet counts, the facet panel's wiki names
    // and the active-filter label all draw on this one set, so a wiki the visitor cannot reach
    // contributes nothing to the page -- not its pages' titles or excerpts, not its name.
    Map<Long, Wiki> visibleWikis = new LinkedHashMap<>();
    List<Wiki> wikiList = WikiRepository.findAll();
    if (wikiList != null) {
      for (Wiki wiki : wikiList) {
        if (canViewWiki(context, wiki)) {
          visibleWikis.put(wiki.getId(), wiki);
        }
      }
    }

    // Determine criteria
    WikiPageSpecification specification = new WikiPageSpecification();
    specification.setSearchTerm(query);
    specification.setWikiIdList(visibleWikis.keySet());
    if (selectedWikiId != -1) {
      specification.setWikiId(selectedWikiId);
    }

    // Query the data
    List<WikiPage> wikiPageList = WikiPageRepository.findAll(specification, constraints);
    // Not yet passing a facetKey here (issue #638's SearchAnalyticsCommand.record() overload isn't
    // on main at the time of this PR) -- a trivial follow-up once #638 merges.
    SearchAnalyticsCommand.record(context, query, "wiki", wikiPageList == null ? 0 : wikiPageList.size());

    // Facet panel: one option per wiki with a non-zero count (or the currently selected one), each
    // counted standalone (ignoring the current wikiId selection) so switching wikis shows every
    // wiki's own count, not just the selected one's (issue #634)
    List<FacetUrlCommand.FacetOption> wikiFacets = new ArrayList<>();
    if (!visibleWikis.isEmpty()) {
      for (Wiki wiki : visibleWikis.values()) {
        boolean selected = selectedWikiId == wiki.getId();
        WikiPageSpecification countSpecification = new WikiPageSpecification();
        countSpecification.setSearchTerm(query);
        countSpecification.setWikiId(wiki.getId());
        long count = WikiPageRepository.findCount(countSpecification);
        if (count > 0 || selected) {
          String url = FacetUrlCommand.buildFacetLinkUrl(context, "wikiId", String.valueOf(wiki.getId()));
          wikiFacets.add(new FacetUrlCommand.FacetOption(String.valueOf(wiki.getId()), wiki.getName(), count, selected, url));
        }
      }
    }
    context.getRequest().setAttribute("wikiFacets", wikiFacets);
    context.getRequest().setAttribute("wikiFacetLabel", WIKI_FACET_LABEL);

    // Active filter chip, with a URL that clears just the wikiId filter (issue #634)
    List<FacetUrlCommand.ActiveFacetFilter> activeFilters = new ArrayList<>();
    if (selectedWikiId != -1) {
      // Looked up in the visible set only: a ?wikiId= for a wiki the visitor cannot open must not
      // turn the filter chip into a way to read that wiki's name
      Wiki selectedWiki = visibleWikis.get(selectedWikiId);
      String valueLabel = selectedWiki != null ? selectedWiki.getName() : "Selected wiki";
      activeFilters.add(new FacetUrlCommand.ActiveFacetFilter(WIKI_FACET_LABEL, valueLabel,
          FacetUrlCommand.buildClearFilterUrl(context, "wikiId")));
    }
    context.getRequest().setAttribute("activeFilters", activeFilters);

    // Standard request items -- set even when the result list is empty (issue #634 fix: this used
    // to bail out before these were set, so the JSP never even rendered a zero-result message,
    // let alone one that could offer to clear an active filter)
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
    context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());

    // Resolve each result's wiki to build its link (/{wikiUniqueId}/{pageUniqueId}) from the visible
    // set already loaded above; a page whose wiki is not in it is skipped (the query is already
    // restricted to these wikis, so this only guards against the two drifting apart)
    List<SearchResult> searchResultList = new ArrayList<>();
    if (wikiPageList != null) {
      for (WikiPage wikiPage : wikiPageList) {
        Wiki wiki = visibleWikis.get(wikiPage.getWikiId());
        if (wiki == null) {
          continue;
        }

        SearchResult searchResult = new SearchResult();
        searchResult.setPageTitle(wikiPage.getTitle());
        if (StringUtils.isNotBlank(wikiPage.getSummary())) {
          searchResult.setPageDescription(wikiPage.getSummary());
        }
        searchResult.setLink("/" + wiki.getUniqueId() + "/" + wikiPage.getUniqueId());

        // Include an excerpt
        String htmlContent = HtmlCommand.toHtml(wikiPage.getHighlight());
        if (StringUtils.isNotBlank(htmlContent)) {
          htmlContent = StringUtils.replace(htmlContent, "${b}", "<strong>");
          htmlContent = StringUtils.replace(htmlContent, "${/b}", "</strong>");
          searchResult.setHtmlExcerpt(htmlContent);
        }
        searchResultList.add(searchResult);
      }
    }
    context.getRequest().setAttribute("searchResultList", searchResultList);

    // Determine if the widget is shown at all -- honors this widget's own showWhenEmpty
    // preference (set on its web-template entry, see "Search Results.xml") the same way its
    // sibling WebPageSearchResultsWidget does. Previously this was never read here, so a
    // zero-result wiki search always rendered its "Documentation Found:" heading and empty-state
    // text even though every other section on the same search-results page correctly disappeared.
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));
    if (searchResultList.isEmpty() && !showWhenEmpty) {
      return context;
    }

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Whether this visitor could open the wiki, by the same two rules that govern viewing it:
   * WikiWidget hides a disabled wiki from everyone but its managers, and a wiki's pages are served
   * by its route web page ("/" + uniqueId), whose role, group and internal-page restrictions
   * apply to every page beneath it. SitemapServlet and LlmsTxtServlet apply the same route-page
   * check for anonymous visitors (issue #1402); search runs it for the actual visitor, so an
   * admin still finds what an admin can open.
   */
  static boolean canViewWiki(WidgetContext context, Wiki wiki) {
    if (wiki == null || StringUtils.isBlank(wiki.getUniqueId())) {
      return false;
    }
    if (!wiki.getEnabled() &&
        !(context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager"))) {
      return false;
    }
    return ValidateUserAccessToWebPageCommand.hasAccess("/" + wiki.getUniqueId(), context.getUserSession());
  }
}
