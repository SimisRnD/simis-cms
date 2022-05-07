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

import com.simisinc.platform.application.cms.ThemeCommand;
import com.simisinc.platform.domain.model.cms.Theme;
import com.simisinc.platform.infrastructure.persistence.cms.ThemeRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Allows admin to view, set, and save themes
 *
 * @author matt rajkowski
 * @created 7/12/19 2:34 PM
 */
public class ThemeEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  static String JSP = "/admin/theme-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show a list of themes
    List<Theme> themeList = ThemeRepository.findAll();
    context.getRequest().setAttribute("themeList", themeList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Validate the form
    String name = context.getParameter("name");
    if (StringUtils.isBlank(name)) {
      context.setErrorMessage("A name is required for saving a theme");
      return context;
    }
    // Save the theme
    ThemeCommand.createSnapshotWithName(name);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Check the action
    String action = context.getParameter("action");
    if (!"restore".equals(action)) {
      return null;
    }
    // Load the theme to be restored
    long themeId = context.getParameterAsLong("id");
    Theme theme = ThemeRepository.findById(themeId);
    if (theme != null) {
      ThemeCommand.restoreTheme(theme);
    }
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Load the theme to be deleted
    long themeId = context.getParameterAsLong("id");
    Theme theme = ThemeRepository.findById(themeId);
    if (theme != null) {
      ThemeRepository.remove(theme);
    }
    return context;
  }
}
