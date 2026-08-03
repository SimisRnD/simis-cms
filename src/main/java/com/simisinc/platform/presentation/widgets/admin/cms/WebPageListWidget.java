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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.presentation.controller.XMLPageLoader;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.Page;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The full /admin/web-pages list is split into two sections: pages currently in the site
 * navigation menu (top), and the complete list of every {@link WebPage} record (bottom, labeled
 * "All Web Pages"). Only the bottom section supports search/status filtering (issue #497) -- the
 * nav-menu section is already organized by its own hierarchy and every page shown there also
 * appears again in the full list below, so filtering it separately would just be confusing.
 *
 * @author matt rajkowski
 * @created 4/25/18 5:45 PM
 */
public class WebPageListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/web-page-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the menu tabs
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemList();
    context.getRequest().setAttribute("menuTabList", menuTabList);

    // Load the built in pages (just the ones which the pages use) -- needed before filtering the
    // "All Web Pages" list below, since a standard/built-in page is always "live" regardless of
    // whether it has stored page_xml.
    Map<String, Page> standardPages = new HashMap<String, Page>();
    XMLPageLoader xmlPageConfig = new XMLPageLoader(standardPages);
    xmlPageConfig.loadWidgetLibrary(context.getRequest().getServletContext(), "/WEB-INF/widgets/widget-library.xml");
    xmlPageConfig.addFile("/WEB-INF/web-layouts/page/page-layout.xml");
    xmlPageConfig.load(context.getRequest().getServletContext());
    context.getRequest().setAttribute("standardPages", standardPages);

    LOG.debug("Widgets: " + xmlPageConfig.getWidgetLibrary().size());
    LOG.debug("Standard pages: " + standardPages.size());

    // Load every web page (used to resolve nav-menu items to their record, e.g. draft/301 status)
    List<WebPage> webPageList = WebPageRepository.findAll();

    // Create a map of links to pages
    Map<String, WebPage> webPageMap = new HashMap<>();
    for (WebPage webPage : webPageList) {
      webPageMap.put(webPage.getLink(), webPage);
    }
    context.getRequest().setAttribute("webPageMap", webPageMap);

    // Status-count summary (issue #497): always computed over the full, unfiltered list so it
    // stays a stable "at a glance" total regardless of the active search/status filter below.
    // Every page falls into exactly one bucket, using the same live/broken/draft/redirect
    // derivation as the status filters further down.
    int webPageDraftCount = 0;
    int webPageRedirectCount = 0;
    int webPageLiveCount = 0;
    int webPageBrokenCount = 0;
    for (WebPage webPage : webPageList) {
      if (webPage.getDraft()) {
        webPageDraftCount++;
      } else if (StringUtils.isNotBlank(webPage.getRedirectUrl())) {
        webPageRedirectCount++;
      } else if (standardPages.containsKey(webPage.getLink())
          || webPage.getLink().startsWith("/directory/")
          || StringUtils.isNotBlank(webPage.getPageXml())) {
        webPageLiveCount++;
      } else {
        webPageBrokenCount++;
      }
    }
    context.getRequest().setAttribute("webPageTotalCount", webPageList.size());
    context.getRequest().setAttribute("webPageLiveCount", webPageLiveCount);
    context.getRequest().setAttribute("webPageDraftCount", webPageDraftCount);
    context.getRequest().setAttribute("webPageRedirectCount", webPageRedirectCount);
    context.getRequest().setAttribute("webPageBrokenCount", webPageBrokenCount);

    // Filter the "All Web Pages" list (search box + status dropdown)
    String searchTerm = context.getParameter("q");
    String status = context.getParameter("status");

    WebPageSpecification specification = new WebPageSpecification();
    if (StringUtils.isNotBlank(searchTerm)) {
      specification.setSearchTerm(searchTerm);
    }
    if ("draft".equals(status)) {
      specification.setDraft(true);
    } else if ("redirect".equals(status)) {
      specification.setHasRedirect(true);
    }
    List<WebPage> filteredWebPageList = (StringUtils.isNotBlank(searchTerm) || StringUtils.isNotBlank(status))
        ? WebPageRepository.findAll(specification, null)
        : webPageList;

    // "live"/"broken" aren't stored columns -- they're derived the same way the JSP derives them
    // (draft/redirect already excluded a page from reaching here; a standard/built-in page or a
    // page under /directory/ is always live regardless of its stored page_xml).
    if ("broken".equals(status)) {
      List<WebPage> brokenList = new ArrayList<>();
      for (WebPage webPage : filteredWebPageList) {
        if (!webPage.getDraft() && StringUtils.isBlank(webPage.getRedirectUrl())
            && !standardPages.containsKey(webPage.getLink())
            && !webPage.getLink().startsWith("/directory/")
            && StringUtils.isBlank(webPage.getPageXml())) {
          brokenList.add(webPage);
        }
      }
      filteredWebPageList = brokenList;
    } else if ("live".equals(status)) {
      List<WebPage> liveList = new ArrayList<>();
      for (WebPage webPage : filteredWebPageList) {
        if (!webPage.getDraft() && StringUtils.isBlank(webPage.getRedirectUrl())
            && (standardPages.containsKey(webPage.getLink())
                || webPage.getLink().startsWith("/directory/")
                || StringUtils.isNotBlank(webPage.getPageXml()))) {
          liveList.add(webPage);
        }
      }
      filteredWebPageList = liveList;
    }
    context.getRequest().setAttribute("webPageList", filteredWebPageList);

    // Governed publish workflow status per page (#407), keyed by web_page_id -- only pages with a
    // pending draft carry an interesting label; a page with no draft is left out of the map and the
    // JSP falls back to its existing draft/live/redirect/broken derivation for those rows.
    Map<Long, String> webPageReviewStatusMap = new HashMap<>();
    for (WebPage webPage : filteredWebPageList) {
      if (webPage.hasDraftContent()) {
        webPageReviewStatusMap.put(webPage.getId(), ContentReviewCommand.listStatusLabel(webPage));
      }
    }
    context.getRequest().setAttribute("webPageReviewStatusMap", webPageReviewStatusMap);

    // Echo the filter values back so the form keeps its state
    context.getRequest().setAttribute("q", searchTerm);
    context.getRequest().setAttribute("status", status);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
