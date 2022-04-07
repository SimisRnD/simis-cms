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

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveCalendarEventCommand;
import com.simisinc.platform.domain.events.cms.CalendarEventRemovedEvent;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * A widget for displaying an interactive calendar
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class CalendarWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/calendar/full-calendar.jsp";
  static String SMALL_JSP = "/calendar/small-calendar.jsp";

  public WidgetContext execute(WidgetContext context) {

    // @note xmlhttp populates the events from /json/calendar

    // Determine which calendar(s) to show, for form too
    List<Calendar> calendarList = CalendarRepository.findAll();
    context.getRequest().setAttribute("calendarList", calendarList);

    // Check for a specific calendar
    String calendarUniqueId = context.getPreferences().get("calendarUniqueId");
    if (StringUtils.isNotBlank(calendarUniqueId)) {
      context.getRequest().setAttribute("calendarUniqueId", calendarUniqueId);
    }

    // Determine the view
    String view = context.getPreferences().getOrDefault("view", null);
    if ("small".equals(view)) {
      context.setJsp(SMALL_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Populate the fields
    CalendarEvent calendarEventBean = new CalendarEvent();
    BeanUtils.populate(calendarEventBean, context.getParameterMap());
    calendarEventBean.setCreatedBy(context.getUserId());
    calendarEventBean.setModifiedBy(context.getUserId());

    // See if the user is duplicating an existing event
    if (context.getParameter("duplicate") != null) {
      calendarEventBean.setId(-1L);
    }

    // Save the event
    CalendarEvent calendarEvent = null;
    try {
      calendarEvent = SaveCalendarEventCommand.saveCalendarEvent(calendarEventBean);
      if (calendarEvent == null) {
        throw new AppException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      LOG.error("Save calendar event error", e);
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(calendarEventBean);
//      context.addSharedRequestValue("returnPage", returnPage);
//      context.setRedirect(returnPage);
      return context;
    }

    // Determine the page to return to
//    context.setSuccessMessage("Event was saved");
    context.setRedirect(context.getUri());
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return context;
    }

    // Determine what's being deleted
    long eventId = context.getParameterAsLong("id");
    if (eventId > -1) {
      CalendarEvent calendarEvent = CalendarEventRepository.findById(eventId);
      if (calendarEvent == null) {
        context.setErrorMessage("Event was not found");
      } else {
        if (CalendarEventRepository.remove(calendarEvent)) {
          context.setSuccessMessage("Calendar event was deleted");
          // Trigger workflow
          WorkflowManager.triggerWorkflowForEvent(new CalendarEventRemovedEvent(calendarEvent));
        } else {
          context.setWarningMessage("Calendar event could not be deleted");
        }
      }
    }
    context.setRedirect(context.getUri());
    return context;
  }
}
