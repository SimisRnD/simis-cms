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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.domain.model.cms.*;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.*;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/28/19 12:15 PM
 */
public class WebPageSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/web-page-search-results.jsp";

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

    // Load the menu tabs, these are the directly linkable web pages
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllActiveIncludeMenuItemList();

    // Load the table of contents
    List<TableOfContents> tableOfContentsList = TableOfContentsRepository.findAll(null, null);

    // Search the content and figure out the matching web pages
    ContentSpecification contentSpecification = new ContentSpecification();
    contentSpecification.setSearchTerm(query);
    List<Content> contentList = ContentRepository.findAll(contentSpecification, constraints);

    // Find active web pages with the matching content object
    Map<String, SearchResult> resultsMap = new LinkedHashMap<>();
    if (contentList != null) {
      // Determine the web pages that can be searched
      WebPageSpecification webPageSpecification = new WebPageSpecification();
      if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
        webPageSpecification.setSearchable(true);
        webPageSpecification.setDraft(false);
      }
      webPageSpecification.setHasRedirect(false);
      List<WebPage> webPageList = WebPageRepository.findAll(webPageSpecification, null);
      // Now search the web pages for a matching unique id
      for (Content content : contentList) {
        String contentUniqueId = content.getUniqueId();
        for (WebPage webPage : webPageList) {
          // Find an active page the content is valid on
          if (webPage.getPageXml() != null &&
              (webPage.getPageXml().contains("<uniqueId>" + contentUniqueId + "</uniqueId>") ||
                  webPage.getPageXml().contains("${uniqueId:" + contentUniqueId + "}"))) {
            String link = webPage.getLink();
            // Skip blank links
            if (StringUtils.isBlank(link)) {
              continue;
            }
            // Skip my page, unless user is logged in
            if (link.startsWith("/my-page") && !context.getUserSession().isLoggedIn()) {
              continue;
            }
            // Skip the cart to give priority to content pages
            if ("/cart".equals(link)) {
              continue;
            }
            // Determine if the link is in the navigation... like menu tabs, menu items, table of contents
            if (!context.hasRole("admin")) {
              boolean foundLinkedPage = false;
              for (MenuTab menuTab : menuTabList) {
                if (menuTab.getLink().equals(link)) {
                  foundLinkedPage = true;
                  break;
                }
                for (MenuItem menuItem : menuTab.getMenuItemList()) {
                  if (menuItem.getLink().equals(link)) {
                    foundLinkedPage = true;
                    break;
                  }
                }
              }
              // Determine if the link is in the table of contents
              if (!foundLinkedPage) {
                for (TableOfContents tableOfContents : tableOfContentsList) {
                  for (TableOfContentsLink tableOfContentsLink : tableOfContents.getEntries()) {
                    if (link.equals(tableOfContentsLink.getLink())) {
                      foundLinkedPage = true;
                      break;
                    }
                  }
                }
              }
              if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
                if (!foundLinkedPage) {
                  continue;
                }
              }
            }

            // Add the search result
            String htmlContent = HtmlCommand.toHtml(content.getHighlight());
            if (htmlContent != null) {
              htmlContent = StringUtils.replace(htmlContent, "${b}", "<strong>");
              htmlContent = StringUtils.replace(htmlContent, "${/b}", "</strong>");
            }
            SearchResult searchResult = resultsMap.get(link);
            if (searchResult == null) {
              searchResult = new SearchResult();
              searchResult.setLink(link);
              if ("/".equals(link)) {
                // It's the home page
                searchResult.setPageTitle(LoadSitePropertyCommand.loadByName("site.name"));
              } else {
                searchResult.setPageTitle(webPage.getTitle());
              }
              searchResult.setHtmlExcerpt(htmlContent);
              resultsMap.put(link, searchResult);
            } else {
              searchResult.setHtmlExcerpt(searchResult.getHtmlExcerpt() + " " + htmlContent);
            }
            // No need to show more web pages which have the same repeated contentId
            break;
          }
        }
      }
      context.getRequest().setAttribute("searchResultList", resultsMap.values());
    }

    // Determine if the widget is shown
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));
    if (resultsMap.isEmpty() && !showWhenEmpty) {
      return context;
    }

    // Determine the view
    context.setJsp(JSP);
    return context;
  }
}
