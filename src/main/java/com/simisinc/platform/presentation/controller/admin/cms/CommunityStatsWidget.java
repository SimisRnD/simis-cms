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

import static com.simisinc.platform.presentation.controller.admin.SiteStatsWidget.BAR_CHART_JSP;
import static com.simisinc.platform.presentation.controller.admin.SiteStatsWidget.CARD_JSP;
import static com.simisinc.platform.presentation.controller.admin.SiteStatsWidget.LINE_CHART_JSP;

import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/13/2020 3:29 PM
 */
public class CommunityStatsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Different kinds of stats and preferences...
    int days = Integer.parseInt(context.getPreferences().getOrDefault("days", "7"));
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    String type = context.getPreferences().get("type");
    String JSP = LINE_CHART_JSP;
    if ("bar".equals(type)) {
      JSP = BAR_CHART_JSP;
    }

    String report = context.getPreferences().get("report");
    if (report == null) {
      LOG.error("DEV: A report preference was not specified");
      return null;
    }

    context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Dataset"));
    context.getRequest().setAttribute("label1", context.getPreferences().get("label1"));

    // Reports
    if ("total-mailing-list-members".equalsIgnoreCase(report)) {
      Long count = MailingListRepository.countTotalMembers();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.setJsp(CARD_JSP);
    } else {
      return null;
    }
    return context;
  }
}
