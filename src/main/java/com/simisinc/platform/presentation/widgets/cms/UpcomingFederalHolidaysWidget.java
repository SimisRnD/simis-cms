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

package com.simisinc.platform.presentation.widgets.cms;

import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;

import com.simisinc.platform.application.cms.FederalHolidayCommand;
import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.domain.model.FederalHoliday;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists the next few United States federal holidays, computed rather than stored.
 *
 * <p>"Today" is taken in the site's own timezone, not the server's, so a site in Hawaii does not
 * drop a holiday from the list while it is still that day locally.
 *
 * @author SimIS Inc.
 */
public class UpcomingFederalHolidaysWidget extends GenericWidget {

  static final long serialVersionUID = 8675309105061981L;

  static String JSP = "/cms/upcoming-federal-holidays.jsp";

  /** Enough to see past the end of the year without turning a sidebar into a calendar */
  private static final int DEFAULT_LIMIT = 4;

  /** There are only eleven, so asking for more than that is asking for all of them */
  private static final int MAX_LIMIT = 11;

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showObservedNote",
        context.getPreferences().getOrDefault("showObservedNote", "true"));

    int limit = NumberUtils.toInt(context.getPreferences().get("limit"), DEFAULT_LIMIT);
    if (limit < 1) {
      limit = DEFAULT_LIMIT;
    }
    if (limit > MAX_LIMIT) {
      limit = MAX_LIMIT;
    }

    LocalDate today = LocalDate.now(FormatDateCommand.getSiteZoneId());
    List<FederalHoliday> holidayList = FederalHolidayCommand.upcoming(today, limit);
    if (holidayList.isEmpty()) {
      // Cannot happen with a working clock -- the list is computed two years out -- but an empty
      // panel with a heading and nothing under it is worse than no panel
      return context;
    }
    context.getRequest().setAttribute("holidayList", holidayList);
    context.setJsp(JSP);
    return context;
  }
}
