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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.domain.model.cms.WebSearch;
import com.simisinc.platform.infrastructure.persistence.cms.WebSearchRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Displays what the user searched for
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class SearchInfoWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/search-info.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the query string
    String pagePath = (String) context.getRequest().getAttribute(RequestConstants.WEB_PAGE_PATH);
    String query = context.getParameter("query");
    context.getRequest().setAttribute("query", query);

    // Track the search terms
    if (StringUtils.isNotBlank(query) &&
        (!context.getUserSession().isLoggedIn() || !(context.hasRole("admin") || context.hasRole("content-manager")))) {
      WebSearch webSearch = new WebSearch();
      webSearch.setPagePath(pagePath);
      webSearch.setQuery(query);
      webSearch.setIpAddress(context.getRequest().getRemoteAddr());
      webSearch.setSessionId(context.getUserSession().getSessionId());
      webSearch.setIsLoggedIn(context.getUserSession().isLoggedIn());
      WebSearchRepository.save(webSearch);
    }

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
