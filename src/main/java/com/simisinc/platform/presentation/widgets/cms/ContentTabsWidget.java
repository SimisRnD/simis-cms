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

import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.ContentTab;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * Displays horizontal tabs and contains embedded content in each tab
 *
 * @author matt rajkowski
 * @created 7/10/18 9:28 AM
 */
public class ContentTabsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String LINKS_JSP = "/cms/content-tabs-links.jsp";
  static String LINKS_TEMPLATE = "/cms/content-tabs-links.html";
  static String JSP = "/cms/content-tabs.jsp";
  static String TEMPLATE = "/cms/content-tabs.html";

  public WidgetContext execute(WidgetContext context) {

    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("tabs");
    if (entriesList.isEmpty()) {
      LOG.debug("Tabs preference is empty");
      return null;
    }

    ArrayList<ContentTab> contentTabList = new ArrayList<>();
    for (Map<String, String> valueMap : entriesList) {
      if (valueMap.containsKey("enabled") && "false".equals(valueMap.get("enabled"))) {
        LOG.debug("Tab is not enabled");
        continue;
      }
      ContentTab contentTab = new ContentTab();
      contentTab.setName(valueMap.get("name"));
      contentTab.setLinkId(valueMap.get("linkId"));
      contentTab.setLink(valueMap.get("link"));
      contentTab.setContentUniqueId(valueMap.get("contentUniqueId"));
      contentTab.setIsActive("true".equals(valueMap.get("isActive")));

      String html = null;
      Content content = LoadContentCommand.loadContentByUniqueId(contentTab.getContentUniqueId());
      if (content != null) {
        html = content.getContent();
      }

      if (StringUtils.isBlank(html)) {
        if (context.hasRole("admin") || context.hasRole("content-manager")) {
          html = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath() + "/content-editor?uniqueId=" + contentTab.getContentUniqueId() + "&returnPage=" + context.getUri() + "\"><i class=\"fa fa-edit\"></i> Add Content Here</a>";
        }
      }

      if (StringUtils.isNotBlank(html)) {
        contentTab.setHtml(html);
        contentTabList.add(contentTab);
      }
    }

    if (contentTabList.isEmpty()) {
      return null;
    }

    if (context.hasRole("admin") || context.hasRole("content-manager")) {
      context.getRequest().setAttribute("showEditor", "true");
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
    }

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("smudge", context.getPreferences().getOrDefault("smudge", "true"));
    context.getRequest().setAttribute("contentTabList", contentTabList);

    if ("true".equals(context.getPreferences().get("useLinks"))) {
      context.setJsp(LINKS_JSP);
      context.setTemplate(LINKS_TEMPLATE);
    } else {
      context.setJsp(JSP);
      context.setTemplate(TEMPLATE);
    }
    return context;
  }
}
