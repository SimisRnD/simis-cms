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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.HtmlCommand;
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

    // Determine criteria
    WikiPageSpecification specification = new WikiPageSpecification();
    specification.setSearchTerm(query);

    // Query the data
    List<WikiPage> wikiPageList = WikiPageRepository.findAll(specification, constraints);
    if (wikiPageList == null || wikiPageList.isEmpty()) {
      return context;
    }

    // Resolve each result's wiki to build its link (/{wikiUniqueId}/{pageUniqueId}), memoized so a
    // page of results with repeated wikis does not re-query the same wiki
    Map<Long, Wiki> wikiById = new HashMap<>();
    List<SearchResult> searchResultList = new ArrayList<>();
    for (WikiPage wikiPage : wikiPageList) {
      Wiki wiki = wikiById.computeIfAbsent(wikiPage.getWikiId(), WikiRepository::findById);
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
}
