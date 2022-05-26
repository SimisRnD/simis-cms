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
import com.simisinc.platform.domain.model.dashboard.StatisticCard;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/19/22 9:35 PM
 */
public class StatisticCardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public static String JSP = "/dashboard/statistic-card.jsp";
  public static String JSP_VERTICAL = "/dashboard/statistic-card-vertical.jsp";

  public WidgetContext execute(WidgetContext context) {

    StatisticCard statisticCard = new StatisticCard();
    statisticCard.setValue(Integer.parseInt(context.getPreferences().getOrDefault("value", "0")));
    statisticCard.setLabel(context.getPreferences().getOrDefault("label", "label"));
    statisticCard.setIcon(context.getPreferences().getOrDefault("icon", null));
    statisticCard.setLink(context.getPreferences().getOrDefault("link", null));
    context.getRequest().setAttribute("statisticCard", statisticCard);

    context.getRequest().setAttribute("iconColor", valueForColor(context.getPreferences().getOrDefault("iconColor", "#ffffff")));

    String view = context.getPreferences().getOrDefault("view", null);
    if ("vertical".equals(view)) {
      context.setJsp(JSP_VERTICAL);
    } else {
      context.setJsp(JSP);
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
