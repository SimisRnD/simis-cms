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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.SaveCalendarCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * CalendarFormWidget previously had no in-widget role check at all -- add/edit calendar POSTs
 * relied solely on the page-level role gate in admin-layout.xml. These tests cover the new
 * admin-only gate (mirroring CalendarListWidget#delete()'s identical pattern for the same Calendar
 * entity), plus baseline execute()/post() coverage that didn't previously exist for this widget.
 */
class CalendarFormWidgetTest extends WidgetBase {

  @Test
  void executeWithCalendarIdLoadsTheExistingCalendarForEditing() {
    Calendar existing = new Calendar();
    existing.setId(3L);
    existing.setName("Team Events");
    existing.setColor("#336699");

    addQueryParameter(widgetContext, "calendarId", "3");

    try (MockedStatic<CalendarRepository> repository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarRepository.findById(3L)).thenReturn(existing);

      WidgetContext result = new CalendarFormWidget().execute(widgetContext);

      Calendar formBean = (Calendar) result.getRequest().getAttribute("calendar");
      assertEquals(3L, formBean.getId());
      assertEquals("Team Events", formBean.getName());
    }
  }

  @Test
  void executeWithNoCalendarIdPublishesNoBeanAndDoesNotQueryTheRepository() {
    try (MockedStatic<CalendarRepository> repository = mockStatic(CalendarRepository.class)) {
      WidgetContext result = new CalendarFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertNull(result.getRequest().getAttribute("calendar"));
    }
  }

  @Test
  void executePrefersAPendingRequestObjectOverLoadingById() {
    Calendar rejected = new Calendar();
    rejected.setId(3L);
    rejected.setName("Unsaved edits");
    widgetContext.setRequestObject(rejected);

    addQueryParameter(widgetContext, "calendarId", "3");

    try (MockedStatic<CalendarRepository> repository = mockStatic(CalendarRepository.class)) {
      WidgetContext result = new CalendarFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertEquals(rejected, result.getRequest().getAttribute("calendar"));
    }
  }

  // --- permission gate ---

  @Test
  void contentManagerCannotSaveACalendar() throws Exception {
    // The page-level role gate on /admin/calendar allows content-manager (and community-manager)
    // to reach this form, but saving itself mirrors CalendarListWidget#delete()'s admin-only gate
    // for the same Calendar entity -- a deliberate narrower boundary than the page-level gate.
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "name", "New Calendar");

    try (MockedStatic<SaveCalendarCommand> saveCommand = mockStatic(SaveCalendarCommand.class)) {
      WidgetContext result = new CalendarFormWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
      assertEquals("Must be an admin", result.getWarningMessage());
    }
  }

  @Test
  void adminCanSaveACalendar() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "name", "New Calendar");
    addQueryParameter(widgetContext, "color", "#a1a1a1");
    addQueryParameter(widgetContext, "enabled", "true");

    Calendar saved = new Calendar();
    saved.setId(9L);
    saved.setName("New Calendar");

    try (MockedStatic<SaveCalendarCommand> saveCommand = mockStatic(SaveCalendarCommand.class)) {
      saveCommand.when(() -> SaveCalendarCommand.saveCalendar(any())).thenReturn(saved);

      WidgetContext result = new CalendarFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveCalendarCommand.saveCalendar(
          argThat(bean -> "New Calendar".equals(bean.getName()) && "#a1a1a1".equals(bean.getColor()) && bean.getEnabled())),
          times(1));
      assertEquals("Calendar was saved", result.getSuccessMessage());
      assertEquals("/admin/calendars", result.getRedirect());
    }
  }
}
