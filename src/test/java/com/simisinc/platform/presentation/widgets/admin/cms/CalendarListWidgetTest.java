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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class CalendarListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"calendarList\" />");

    List<Calendar> calendarList = new ArrayList<>();
    Calendar calendar = new Calendar();
    calendar.setId(1L);
    calendar.setUniqueId("calendar");
    calendarList.add(calendar);

    try (MockedStatic<CalendarRepository> calendarRepositoryMockedStatic = mockStatic(CalendarRepository.class)) {
      calendarRepositoryMockedStatic.when(CalendarRepository::findAll).thenReturn(calendarList);

      try (MockedStatic<CalendarEventRepository> calendarEventRepositoryMockedStatic = mockStatic(CalendarEventRepository.class)) {
        calendarEventRepositoryMockedStatic.when(() -> CalendarEventRepository.findCount(any())).thenReturn(8L);

        // Execute the widget
        CalendarListWidget widget = new CalendarListWidget();
        widget.execute(widgetContext);
      }
    }

    // Verify the request
    Assertions.assertEquals(CalendarListWidget.JSP, widgetContext.getJsp());

    List<Calendar> calendarListRequest = (List) request.getAttribute("calendarList");
    Assertions.assertEquals(calendarList.size(), calendarListRequest.size());

    Map<Long, Long> calendarEventCount = (Map) request.getAttribute("calendarEventCount");
    Assertions.assertEquals(8L, calendarEventCount.get(calendar.getId()));
  }

  @Test
  void deleteError() {
    // Set query parameters
    addQueryParameter(widgetContext, "id", "1");

    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"calendarList\" />");

    Calendar calendar = new Calendar();
    calendar.setId(1L);

    try (MockedStatic<CalendarRepository> calendarRepositoryMockedStatic = mockStatic(CalendarRepository.class)) {
      calendarRepositoryMockedStatic.when(() -> CalendarRepository.findById(calendar.getId())).thenReturn(calendar);

      // Execute the widget
      CalendarListWidget widget = new CalendarListWidget();
      WidgetContext result = widget.delete(widgetContext);

      // Verify without Admin role
      Assertions.assertNotNull(widgetContext.getWarningMessage());
      Assertions.assertNotNull(result);
    }
  }

  @Test
  void deleteSuccess() {
    // Set query parameters
    addQueryParameter(widgetContext, "id", "1");

    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"calendarList\" />");

    Calendar calendar = new Calendar();
    calendar.setId(1L);

    try (MockedStatic<CalendarRepository> calendarRepositoryMockedStatic = mockStatic(CalendarRepository.class)) {
      calendarRepositoryMockedStatic.when(() -> CalendarRepository.findById(calendar.getId())).thenReturn(calendar);
      calendarRepositoryMockedStatic.when(() -> CalendarRepository.remove(calendar)).thenReturn(true);

      // Run as Admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      CalendarListWidget widget = new CalendarListWidget();
      WidgetContext result = widget.delete(widgetContext);

      // Verify
      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getWarningMessage());
      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNotNull(widgetContext.getSuccessMessage());
    }
  }
}