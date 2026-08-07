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

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveCalendarEventCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Widget for displaying a system administration form to add/update calendar events
 *
 * @author matt rajkowski
 * @created 10/29/18 1:06 PM
 */
public class CalendarEventFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/calendar-event-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("calendarEvent", context.getRequestObject());
    } else {
      // Allow either calendarEventId (editing an existing event) or calendarId (pre-selecting the
      // calendar for a brand-new event)
      long calendarEventId = context.getParameterAsLong("calendarEventId");
      if (calendarEventId > -1) {
        CalendarEvent calendarEvent = CalendarEventRepository.findById(calendarEventId);
        if (calendarEvent != null) {
          context.getRequest().setAttribute("calendarEvent", calendarEvent);
        }
      } else {
        long calendarId = context.getParameterAsLong("calendarId");
        if (calendarId > -1) {
          CalendarEvent calendarEvent = new CalendarEvent();
          calendarEvent.setCalendarId(calendarId);
          context.getRequest().setAttribute("calendarEvent", calendarEvent);
        }
      }
    }

    // The bean's tagsList is a String[], but the JSP renders it as one comma-separated text input
    // (matching full-calendar.jsp's modal). Pre-join it here so the JSP can emit it through a
    // single c:out rather than escaping-then-reassembling an array inside an HTML attribute.
    CalendarEvent formBean = (CalendarEvent) context.getRequest().getAttribute("calendarEvent");
    if (formBean != null && formBean.getTagsList() != null && formBean.getTagsList().length > 0) {
      context.getRequest().setAttribute("tagsListValue", String.join(", ", formBean.getTagsList()));
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Permission is required -- matches CalendarWidget.post()'s admin/content-manager pairing for
    // calendar event mutations. This widget previously had no in-widget check at all, relying
    // solely on the page-level role gate in admin-layout.xml (defense-in-depth gap).
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to modify calendar events");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Populate the fields
    CalendarEvent calendarEventBean = new CalendarEvent();
    BeanUtils.populate(calendarEventBean, context.getParameterMap());
    calendarEventBean.setCreatedBy(context.getUserId());
    calendarEventBean.setModifiedBy(context.getUserId());

    // The following two fields cannot be populated by BeanUtils.populate() above -- both mirror
    // CalendarWidget.post()'s identical handling of the same form fields, since this form now
    // submits them too (previously it only submitted id/calendarId/title/summary/allDay/
    // startDate/endDate, so SaveCalendarEventCommand's unconditional overwrite of every field
    // silently reset an existing event's location/links/tags/published status to blank/draft on
    // every save through this page).

    // tagsList is a String[] on the bean but a single comma-separated form field
    String tagsListParam = context.getParameter("tagsList");
    if (StringUtils.isNotBlank(tagsListParam)) {
      String[] parsedTags = tagsListParam.split(",");
      List<String> tagsList = new ArrayList<>();
      for (String tag : parsedTags) {
        String trimmed = tag.trim();
        if (!trimmed.isEmpty()) {
          tagsList.add(trimmed);
        }
      }
      calendarEventBean.setTagsList(tagsList.isEmpty() ? null : tagsList.toArray(new String[0]));
    } else {
      calendarEventBean.setTagsList(null);
    }

    // published is a Timestamp on the bean, driven by the "enabled" checkbox -- same field name
    // and semantics as CalendarWidget.post()'s "Publish it?" checkbox: checked sets published to
    // now (re-publishing bumps the timestamp, same as the full calendar editor already does),
    // unchecked clears it back to a draft.
    String enabled = context.getParameter("enabled");
    if (StringUtils.isNotBlank(enabled)) {
      calendarEventBean.setPublished(new Timestamp(System.currentTimeMillis()));
    } else {
      calendarEventBean.setPublished(null);
    }

    // Determine additional settings
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    CalendarEvent calendarEvent = null;
    try {
      calendarEvent = SaveCalendarEventCommand.saveCalendarEvent(calendarEventBean);
      if (calendarEvent == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(calendarEventBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Event was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/calendars");
    }
    return context;

  }

}
