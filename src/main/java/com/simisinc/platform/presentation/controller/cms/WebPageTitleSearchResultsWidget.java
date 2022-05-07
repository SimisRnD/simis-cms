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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/29/19 8:52 AM
 */
public class WebPageTitleSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/web-page-title-search-results.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

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
    query = query.toLowerCase().trim();

    // Prepare the search results
    List<SearchResult> searchResultList = new ArrayList<>();

    // Load the menu tabs, these are the directly linkable web pages
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllActiveIncludeMenuItemList();

    for (MenuTab menuTab : menuTabList) {
      if (menuTab.getName().toLowerCase().contains(query)) {
        // Check the corresponding page
        if (!ValidateUserAccessToWebPageCommand.hasAccess(menuTab.getLink(), context.getUserSession())) {
          continue;
        }
        SearchResult searchResult = new SearchResult();
        searchResult.setPageTitle(menuTab.getName());
        searchResult.setLink(menuTab.getLink());
        searchResultList.add(searchResult);
      }
      for (MenuItem menuItem : menuTab.getMenuItemList()) {
        if (menuItem.getName().toLowerCase().contains(query)) {
          // Check the corresponding page
          if (!ValidateUserAccessToWebPageCommand.hasAccess(menuItem.getLink(), context.getUserSession())) {
            continue;
          }
          SearchResult searchResult = new SearchResult();
          searchResult.setPageTitle(menuItem.getName());
          searchResult.setLink(menuItem.getLink());
          searchResultList.add(searchResult);
        }
      }
    }

    // Check the page the table of contents links to
//    List<TableOfContents> tableOfContentsList = TableOfContentsRepository.findAll(null, null);

    context.getRequest().setAttribute("searchResultList", searchResultList);

    // Determine if the widget is shown
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"));
    if (searchResultList.isEmpty() && !showWhenEmpty) {
      return context;
    }

    // Determine the view
    context.setJsp(JSP);
    return context;
  }
}
