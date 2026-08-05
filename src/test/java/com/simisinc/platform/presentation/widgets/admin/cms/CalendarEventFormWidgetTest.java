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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Issue #426: the admin calendar-event-list edit link
 * ({@code ${ctx}/admin/calendar-event?calendarEventId=${event.id}&returnPage=/admin/calendars}) has
 * always sent {@code calendarEventId}, but execute()'s GET path only understood {@code calendarId}
 * (for pre-selecting a calendar on a brand-new event) -- the load-by-id branch was commented out as
 * "not yet implemented". So clicking an edit link landed on a blank create form: the
 * {@code calendarEvent} bean defaulted to id=-1 via calendar-event-form.jsp's
 * {@code <jsp:useBean>}, and because the hidden field is {@code name="id"} and
 * CalendarEventRepository.save() branches add-vs-update on {@code id > -1}, submitting that blank
 * form silently created a duplicate event instead of updating the original.
 *
 * These tests guard the fix: execute() now loads the existing event by {@code calendarEventId} (the
 * same param name the link already sends) and populates the form bean from it, following the same
 * load-existing-record pattern as WebPageFormWidget.execute() (webPageId -> findById -> null-check).
 */
class CalendarEventFormWidgetTest extends WidgetBase {

  @Test
  void executeWithCalendarEventIdLoadsTheExistingEventForEditing() {
    CalendarEvent existing = new CalendarEvent();
    existing.setId(42L);
    existing.setCalendarId(3L);
    existing.setTitle("Town Hall");
    existing.setSummary("Quarterly update");
    existing.setStartDate(Timestamp.valueOf("2026-09-01 18:00:00"));
    existing.setEndDate(Timestamp.valueOf("2026-09-01 19:30:00"));

    addQueryParameter(widgetContext, "calendarEventId", "42");
    addQueryParameter(widgetContext, "returnPage", "/admin/calendars");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(existing);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verify(() -> CalendarEventRepository.findById(42L), times(1));
      CalendarEvent formBean = (CalendarEvent) result.getRequest().getAttribute("calendarEvent");
      assertEquals(42L, formBean.getId());
      assertEquals(3L, formBean.getCalendarId());
      assertEquals("Town Hall", formBean.getTitle());
      assertEquals("Quarterly update", formBean.getSummary());
      assertEquals(Timestamp.valueOf("2026-09-01 18:00:00"), formBean.getStartDate());
      assertEquals(Timestamp.valueOf("2026-09-01 19:30:00"), formBean.getEndDate());
    }
  }

  /**
   * If the id doesn't resolve to a record (e.g. a stale or tampered link), no bean is published to
   * the request rather than silently exposing a blank id=-1 "new event" form under an edit link --
   * calendar-event-form.jsp's own &lt;jsp:useBean&gt; fallback then takes over, same as if
   * calendarEventId had never been passed at all.
   */
  @Test
  void executeWithUnknownCalendarEventIdPublishesNoBean() {
    addQueryParameter(widgetContext, "calendarEventId", "999");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      assertNull(result.getRequest().getAttribute("calendarEvent"));
    }
  }

  /**
   * The create flow (no calendarEventId at all -- e.g. "New Event" from the calendar list, or from a
   * specific calendar's page) must keep working exactly as before: no lookup happens, and when a
   * calendarId is given the bean is a fresh CalendarEvent pre-populated with just that calendar, so
   * the hidden id field renders as -1 and CalendarEventRepository.save() takes the add path.
   */
  @Test
  void executeWithOnlyCalendarIdStillPreSelectsTheCalendarForANewEvent() {
    addQueryParameter(widgetContext, "calendarId", "3");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      CalendarEvent formBean = (CalendarEvent) result.getRequest().getAttribute("calendarEvent");
      assertEquals(-1L, formBean.getId());
      assertEquals(3L, formBean.getCalendarId());
    }
  }

  /**
   * With neither calendarEventId nor calendarId (e.g. a bare "New Event" link with no calendar
   * context), no bean is published and no repository lookup happens -- calendar-event-form.jsp's
   * &lt;jsp:useBean&gt; fallback supplies a blank CalendarEvent.
   */
  @Test
  void executeWithNoIdParametersPublishesNoBeanAndDoesNotQueryTheRepository() {
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertNull(result.getRequest().getAttribute("calendarEvent"));
    }
  }

  /**
   * When post() sent the widget back here after a validation failure, execute() must render the
   * request object it was handed (the just-submitted, still-invalid bean) rather than re-loading
   * from the repository -- otherwise the user's in-progress edits would vanish.
   */
  @Test
  void executePrefersAPendingRequestObjectOverLoadingById() {
    CalendarEvent rejected = new CalendarEvent();
    rejected.setId(42L);
    rejected.setTitle("Unsaved edits");
    widgetContext.setRequestObject(rejected);

    addQueryParameter(widgetContext, "calendarEventId", "42");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertEquals(rejected, result.getRequest().getAttribute("calendarEvent"));
    }
  }
}
