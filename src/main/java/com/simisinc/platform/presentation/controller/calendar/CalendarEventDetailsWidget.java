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

package com.simisinc.platform.presentation.controller.calendar;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadCalendarCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/19 11:08 PM
 */
public class CalendarEventDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/calendar/calendar-event-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the calendar event
    String eventUniqueId = context.getUri().substring(context.getUri().lastIndexOf("/") + 1);
    CalendarEvent calendarEvent = CalendarEventRepository.findByUniqueId(eventUniqueId);
    if (calendarEvent == null) {
      LOG.debug("Calendar event not found: " + eventUniqueId);
      return null;
    }

    // Check the calendar
    Calendar calendar = LoadCalendarCommand.loadCalendarById(calendarEvent.getCalendarId());
    if (!calendar.getEnabled() &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    context.getRequest().setAttribute("calendar", calendar);
    context.getRequest().setAttribute("calendarEvent", calendarEvent);

    // Set Add-To-Calendar requirements
    String timezone = LoadSitePropertyCommand.loadByName("site.timezone");
    context.getRequest().setAttribute("timezone", timezone);

    // Determine the view
    context.getRequest().setAttribute("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
    context.setPageTitle(calendarEvent.getTitle());
    context.setJsp(JSP);
    return context;
  }
}
