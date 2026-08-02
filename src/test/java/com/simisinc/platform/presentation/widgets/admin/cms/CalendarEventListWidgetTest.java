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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;

/**
 * Tests the /admin/calendars event-list widget (issue #501): search/filter request parameters map
 * onto the existing CalendarEventSpecification query layer (the same one CalendarSearchResultsWidget
 * uses on the public site), and pagination carries the filters forward.
 *
 * @author elizabeth houser
 */
class CalendarEventListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"calendarEventList\">\n" +
        "  <title>Events</title>\n" +
        "</widget>");

    List<CalendarEvent> eventList = new ArrayList<>();
    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    eventList.add(event);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(eventList);
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarEventListWidget().execute(widgetContext);
    }

    assertEquals(CalendarEventListWidget.JSP, widgetContext.getJsp());
    assertEquals("Events", request.getAttribute("title"));
    List<CalendarEvent> eventListRequest = (List) request.getAttribute("calendarEventList");
    assertEquals(event.getId(), eventListRequest.get(0).getId());
  }

  @Test
  void searchAndFilterParametersMapOntoTheSpecification() {
    addQueryParameter(widgetContext, "q", "town hall");
    addQueryParameter(widgetContext, "calendarId", "7");
    addQueryParameter(widgetContext, "status", "published");
    addQueryParameter(widgetContext, "fromDate", "2026-08-01");
    addQueryParameter(widgetContext, "toDate", "2026-08-20");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarEventListWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> captor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(captor.capture(), any(DataConstraints.class)));
      CalendarEventSpecification spec = captor.getValue();

      assertEquals("town hall", spec.getSearchTerm());
      assertEquals(7L, spec.getCalendarId());
      assertEquals(1, spec.getPublishedOnly()); // DataConstants.TRUE
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-08-01").atStartOfDay()), spec.getStartingDateRange());
      // The "to" bound is half-open: the start of the day AFTER the picked date, so that whole day is included
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-08-21").atStartOfDay()), spec.getEndingDateRange());

      // Pagination must carry the filters forward (URL-encoded) so page 2+ stays filtered
      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("q=town+hall")); // space is URL-encoded
      assertTrue(pagingParams.contains("calendarId=7"));
      assertTrue(pagingParams.contains("status=published"));
      assertTrue(pagingParams.contains("fromDate=2026-08-01"));
      assertTrue(pagingParams.contains("toDate=2026-08-20"));
    }
  }

  @Test
  void draftStatusMapsToPublishedOnlyFalse() {
    addQueryParameter(widgetContext, "status", "draft");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarEventListWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> captor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(captor.capture(), any(DataConstraints.class)));
      assertEquals(0, captor.getValue().getPublishedOnly()); // DataConstants.FALSE
    }
  }

  @Test
  void blankFiltersLeaveTheSpecificationUnset() {
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarEventListWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> captor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(captor.capture(), any(DataConstraints.class)));
      CalendarEventSpecification spec = captor.getValue();

      assertNull(spec.getSearchTerm());
      assertEquals(-1L, spec.getCalendarId());
      assertEquals(-1, spec.getPublishedOnly()); // DataConstants.UNDEFINED
      assertNull(spec.getStartingDateRange());
      assertNull(spec.getEndingDateRange());
    }
  }

  @Test
  void anInvalidDateIsIgnoredRatherThanBreakingTheQuery() {
    addQueryParameter(widgetContext, "fromDate", "not-a-date");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarEventListWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> captor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(captor.capture(), any(DataConstraints.class)));
      assertNull(captor.getValue().getStartingDateRange());
    }
  }

  @Test
  void theCalendarListIsExposedToTheRequestForTheFilterDropdown() {
    List<Calendar> calendarList = new ArrayList<>();
    Calendar calendar = new Calendar();
    calendar.setId(3L);
    calendar.setName("Staff Events");
    calendarList.add(calendar);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class)) {
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(calendarList);

      new CalendarEventListWidget().execute(widgetContext);

      List<Calendar> exposed = (List) widgetContext.getRequest().getAttribute("calendarList");
      assertEquals(1, exposed.size());
      assertEquals("Staff Events", exposed.get(0).getName());
    }
  }
}
