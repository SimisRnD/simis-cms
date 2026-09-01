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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.SaveMenuEditorChangesCommand;
import com.simisinc.platform.application.cms.SaveMenuTabCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:39 AM
 */
public class SiteMapWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sitemap.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The tree, so nested items are at least VISIBLE here (issue #1728). They are read-only on
    // this screen -- editing them lives in the Edit Links editor -- but showing a menu without
    // its third level would misrepresent the live site to whoever is reordering it.
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemTree();
    context.getRequest().setAttribute("menuTabList", menuTabList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }


  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Determine the action
    String method = context.getParameter("method");
    WidgetContext updatedContext = null;
    if ("sitemap-editor".equals(method)) {
      updatedContext = processSiteMapChanges(context);
    } else {
      updatedContext = processNewTabForm(context);
    }
    // Trigger cache refresh
    CacheManager.invalidateObjectCacheKey(CacheManager.MENU_TAB_LIST);
    return updatedContext;
  }

  private WidgetContext processNewTabForm(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    LOG.debug("processNewTabForm...");

    // Populate the fields
    MenuTab menuTabBean = new MenuTab();
    BeanUtils.populate(menuTabBean, context.getParameterMap());

    // Save the record
    MenuTab menuTab = null;
    try {
      menuTab = SaveMenuTabCommand.appendNewTab(menuTabBean);
      if (menuTab == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(menuTabBean);
      return context;
    }

    // Determine the page to return to
//    context.setSuccessMessage("Menu Tab was saved");
    context.setRedirect("/admin/sitemap");
    return context;
  }


  private WidgetContext processSiteMapChanges(WidgetContext context) {
    LOG.debug("processSiteMapChanges...");
    // The parsing is shared with the Edit Links editor (issue #1732). This screen simply does not
    // post link fields or a third-level order, and those steps are no-ops when their parameters
    // are absent, so sharing the method does not give this screen behaviour its form cannot reach.
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
