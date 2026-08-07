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

import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/29/18 1:59 PM
 */
public class CalendarListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/calendar-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the calendars
    List<Calendar> calendarList = CalendarRepository.findAll();
    context.getRequest().setAttribute("calendarList", calendarList);

    // Determine the event count for every calendar in a single grouped query (previously one
    // findCount() round trip per calendar in a loop -- an N+1 query as the calendar count grows)
    Map<Long, Long> calendarEventCount = CalendarEventRepository.countGroupedByCalendarId();
    context.getRequest().setAttribute("calendarEventCount", calendarEventCount);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Permission is required
    if (!context.hasRole("admin")) {
      context.setWarningMessage("Must be an admin");
      return context;
    }

    // Determine what's being deleted
    long calendarId = context.getParameterAsLong("id");
    if (calendarId > -1) {
      Calendar calendar = CalendarRepository.findById(calendarId);
      if (calendar == null) {
        context.setErrorMessage("Calendar was not found");
      } else {
        if (CalendarRepository.remove(calendar)) {
          context.setSuccessMessage("Calendar was deleted");
        } else {
          context.setWarningMessage("Calendar could not be deleted");
        }
      }
    }
    context.setRedirect(context.getUri());
    return context;
  }
}
