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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.XMLPageLoader;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.Page;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
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

    // Load the web pages
    List<WebPage> webPageList = WebPageRepository.findAll();
    context.getRequest().setAttribute("webPageList", webPageList);

    // Create a map of links to pages
    Map<String, WebPage> webPageMap = new HashMap<>();
    for (WebPage webPage : webPageList) {
      webPageMap.put(webPage.getLink(), webPage);
    }
    context.getRequest().setAttribute("webPageMap", webPageMap);

    // Load the built in pages (just the ones which the pages use)
    Map<String, Page> standardPages = new HashMap<String, Page>();
    XMLPageLoader xmlPageConfig = new XMLPageLoader(standardPages);
    xmlPageConfig.loadWidgetLibrary(context.getRequest().getServletContext(), "/WEB-INF/widgets/widget-library.xml");
    xmlPageConfig.addFile("/WEB-INF/web-layouts/page/page-layout.xml");
    xmlPageConfig.load(context.getRequest().getServletContext());
    context.getRequest().setAttribute("standardPages", standardPages);

    LOG.debug("Widgets: " + xmlPageConfig.getWidgetLibrary().size());
    LOG.debug("Standard pages: " + standardPages.size());

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
