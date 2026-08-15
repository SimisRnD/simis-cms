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

package com.simisinc.platform.presentation.widgets.calendar;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadCalendarCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

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

    // An admin/content-manager previews content the public cannot see yet; the same pair of roles
    // gates the calendar's "Online?" check below, and CalendarAjaxEvents/CalendarEventAjax name
    // this exact bypass canSeeUnpublished.
    boolean isPreviewer = context.hasRole("admin") || context.hasRole("content-manager");

    // Check the event's own visibility. findByUniqueId() applies no filtering, so the two states
    // CalendarEventSpecification exposes as publishedOnly/archivedOnly ("published IS NOT NULL" /
    // "archived IS NULL" in CalendarEventRepository.createWhereStatement) are checked here
    // instead. Without this, a draft or archived event on an enabled calendar rendered in full to
    // any visitor who knew its uniqueId, while every list/feed surface correctly hid it.
    if (calendarEvent.getPublished() == null && !isPreviewer) {
      LOG.debug("Calendar event is not published: " + eventUniqueId);
      return null;
    }
    // issue #882: archived is a distinct "no longer relevant" state rather than a preview-gate
    // concern, so it is honored for every visitor, previewer included -- matching the
    // unconditional setArchivedOnly(false) in CalendarEventAjax, CalendarAjaxEvents,
    // CalendarSearchResultsWidget and UpcomingCalendarEventsWidget.
    if (calendarEvent.getArchived() != null) {
      LOG.debug("Calendar event is archived: " + eventUniqueId);
      return null;
    }

    // Check the calendar
    Calendar calendar = LoadCalendarCommand.loadCalendarById(calendarEvent.getCalendarId());
    if (!calendar.getEnabled() && !isPreviewer) {
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
    // Bridge the event for Event JSON-LD (issue #1181). This is set only after the calendar-enabled
    // check above, so PageServlet never sees an event the visitor could not already read. PageServlet
    // cannot resolve the event itself -- /calendar-event{/event-unique-id} is a wildcard page and
    // this widget performs the lookup -- which is the same reason Product schema is bridged.
    context.setCalendarEvent(calendarEvent);
    context.setJsp(JSP);
    return context;
  }
}
