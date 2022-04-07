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

import java.lang.reflect.InvocationTargetException;

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveCalendarEventCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;

/**
 * Description
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

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("calendarEvent", context.getRequestObject());
    } else {
//      int calendarEventId = context.getParameterAsInt("calendarEventId");
//      CalendarEvent calendarEvent = CalendarEventRepository.findById(calendarEventId);
//      context.getRequest().setAttribute("calendarEvent", calendarEvent);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    CalendarEvent calendarEventBean = new CalendarEvent();
    BeanUtils.populate(calendarEventBean, context.getParameterMap());
    calendarEventBean.setCreatedBy(context.getUserId());
    calendarEventBean.setModifiedBy(context.getUserId());

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
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Event was saved");
    context.setRedirect("/admin/calendar-events");
    return context;

  }

}
