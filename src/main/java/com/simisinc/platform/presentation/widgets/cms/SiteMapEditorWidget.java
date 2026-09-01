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

import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.SaveMenuEditorChangesCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:39 AM
 */
public class SiteMapEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sitemap-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The tree, not the flat list: the editor has to show nested items or they look deleted,
    // and a third level that cannot be seen cannot be reordered or unnested (issue #1728).
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemTree();
    context.getRequest().setAttribute("menuTabList", menuTabList);

    List<WebPage> webPageList = WebPageRepository.findAll();
    context.getRequest().setAttribute("webPageList", webPageList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }


  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Execute the action
    WidgetContext updatedContext = processSiteMapChanges(context);
    // Trigger cache refresh
    CacheManager.invalidateObjectCacheKey(CacheManager.MENU_TAB_LIST);
    return updatedContext;
  }

  private WidgetContext processSiteMapChanges(WidgetContext context) {
    LOG.debug("processSiteMapChanges...");
    // Shared with the Navigation Menu Editor (issue #1732): one parser, and each screen gets the
    // handling for the fields it actually posts. This screen posts links and a third-level order;
    // it does not post staged adds or deletions, and those steps are no-ops without their fields.
    SaveMenuEditorChangesCommand.applyChanges(context);
    context.setRedirect("/admin/sitemap");
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Execute the action
    WidgetContext updatedContext = executeDelete(context);
    // Trigger cache refresh
    CacheManager.invalidateObjectCacheKey(CacheManager.MENU_TAB_LIST);
    return updatedContext;
  }

  private WidgetContext executeDelete(WidgetContext context) {
    try {
      SaveMenuEditorChangesCommand.applyDeleteRequest(context);
    } catch (Exception e) {
      context.setErrorMessage("Error. " + e.getMessage());
    }
    context.setRedirect("/admin/sitemap");
    return context;
  }
}
