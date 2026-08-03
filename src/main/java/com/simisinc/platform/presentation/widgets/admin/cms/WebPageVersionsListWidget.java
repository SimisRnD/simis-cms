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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageVersion;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageVersionRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/web-page-versions admin page (issue #405): lists a web page's prior published
 * revisions (author, timestamp) and offers a restore action, which loads the chosen version's XML
 * into the draft slot for review -- a subsequent publish is required to make it live again.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class WebPageVersionsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/web-page-versions-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    long webPageId = context.getParameterAsLong("webPageId", -1);
    WebPage webPage = webPageId > -1 ? WebPageRepository.findById(webPageId) : null;
    if (webPage == null) {
      context.setErrorMessage("Web page was not found");
      return context;
    }
    context.getRequest().setAttribute("webPage", webPage);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    List<WebPageVersion> versionList = WebPageVersionRepository.findByWebPageId(webPageId, constraints);
    context.getRequest().setAttribute("versionList", versionList);

    // Resolve the author display name for each version shown on this page
    Map<Long, User> userMap = new HashMap<>();
    if (versionList != null) {
      for (WebPageVersion version : versionList) {
        long publishedBy = version.getPublishedBy();
        if (publishedBy > -1 && !userMap.containsKey(publishedBy)) {
          userMap.put(publishedBy, UserRepository.findByUserId(publishedBy));
        }
      }
    }
    context.getRequest().setAttribute("userMap", userMap);

    context.getRequest().setAttribute("recordPagingParams", "webPageId=" + webPageId);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    if (!"restore".equals(context.getParameter("action"))) {
      return null;
    }

    long webPageVersionId = context.getParameterAsLong("webPageVersionId", -1);
    WebPageVersion version = webPageVersionId > -1 ? WebPageVersionRepository.findById(webPageVersionId) : null;
    if (version == null) {
      context.setErrorMessage("The selected version was not found");
      return execute(context);
    }

    WebPage webPage = WebPageRepository.findById(version.getWebPageId());
    if (webPage == null) {
      context.setErrorMessage("The web page was not found");
      return execute(context);
    }

    if (!WebPageRepository.restoreDraftFromVersion(webPage.getId(), version.getPageXml())) {
      context.setErrorMessage("The version could not be restored");
      return execute(context);
    }

    context.setSuccessMessage("The version was restored to the draft. Publish it to make it live.");
    return execute(context);
  }
}
