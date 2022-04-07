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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadCalendarCommand;
import com.simisinc.platform.application.cms.SaveCalendarCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/29/18 2:15 PM
 */
public class CalendarFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/calendar-form.jsp";

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
    Calendar calendar = null;
    if (context.getRequestObject() != null) {
      calendar = (Calendar) context.getRequestObject();
      context.getRequest().setAttribute("calendar", calendar);
    } else {
      long calendarId = context.getParameterAsLong("calendarId");
      if (calendarId > -1) {
        calendar = LoadCalendarCommand.loadCalendarById(calendarId);
        context.getRequest().setAttribute("calendar", calendar);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Calendar calendarBean = new Calendar();
    BeanUtils.populate(calendarBean, context.getParameterMap());
    calendarBean.setCreatedBy(context.getUserId());
    calendarBean.setModifiedBy(context.getUserId());

    // Determine additional settings
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the calendar
    Calendar calendar = null;
    try {
      calendar = SaveCalendarCommand.saveCalendar(calendarBean);
      if (calendar == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(calendarBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Calendar was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/calendars");
    }
    return context;
  }
}
