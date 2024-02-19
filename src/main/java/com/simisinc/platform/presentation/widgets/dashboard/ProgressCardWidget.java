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

package com.simisinc.platform.presentation.widgets.dashboard;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.dashboard.ProgressCard;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a chart with progress indicator
 *
 * @author matt rajkowski
 * @created 5/19/22 9:35 PM
 */
public class ProgressCardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public static String JSP = "/dashboard/progress-card.jsp";
  public static String TEMPLATE = "/dashboard/progress-card.html";
  public static String JSP_VERTICAL = "/dashboard/progress-card-vertical.jsp";
  public static String TEMPLATE_VERTICAL = "/dashboard/progress-card-vertical.html";

  public WidgetContext execute(WidgetContext context) {
    // Determine preferences
    ProgressCard progressCard = new ProgressCard();
    progressCard.setLabel(context.getPreferences().getOrDefault("label", null));
    progressCard.setValue(context.getPreferences().getOrDefault("value", null));
    progressCard.setProgress(Integer.parseInt(context.getPreferences().getOrDefault("progress", "0")));
    progressCard.setMaxValue(Integer.parseInt(context.getPreferences().getOrDefault("maxValue", "0")));
    progressCard.setDifference(progressCard.getMaxValue() - progressCard.getProgress());
    progressCard.setMaxLabel(context.getPreferences().getOrDefault("maxLabel", "maxLabel"));
    progressCard.setLink(context.getPreferences().getOrDefault("link", null));
    context.getRequest().setAttribute("progressCard", progressCard);

    context.getRequest().setAttribute("textColor", valueForColor(context.getPreferences().getOrDefault("textColor", "#ffffff")));
    context.getRequest().setAttribute("subheaderColor", valueForColor(context.getPreferences().getOrDefault("subheaderColor", "#ffffff")));
    context.getRequest().setAttribute("progressColor", valueForColor(context.getPreferences().getOrDefault("progressColor", "#ffcc02")));
    context.getRequest().setAttribute("remainderColor", valueForColor(context.getPreferences().getOrDefault("remainderColor", "#1f1e22")));

    // Display the JSP
    String view = context.getPreferences().getOrDefault("view", null);
    if ("vertical".equals(view)) {
      context.setJsp(JSP_VERTICAL);
      context.setTemplate(TEMPLATE_VERTICAL);
    } else {
      context.setJsp(JSP);
      context.setTemplate(TEMPLATE);
    }
    return context;
  }

  private String valueForColor(String colorName) {
    if (colorName.startsWith("theme.")) {
      return LoadSitePropertyCommand.loadByName(colorName);
    }
    return colorName;
  }
}
