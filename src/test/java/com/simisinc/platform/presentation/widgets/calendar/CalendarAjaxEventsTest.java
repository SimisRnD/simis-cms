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

package com.simisinc.platform.presentation.widgets.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;

/**
 * The calendar events feed (/json/calendar) returns event title/location/description as JSON that the
 * FullCalendar tooltip renders straight into markup via jQuery .html(). JSON-encoding is not HTML-safe,
 * so a crafted event title or location could inject markup (stored DOM XSS). This verifies the feed
 * HTML-encodes those fields so the payload is inert in the DOM.
 *
 * @author Elizabeth Houser
 */
class CalendarAjaxEventsTest {

  @Test
  void eventTitleAndLocationAreHtmlEncoded() {
    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setUniqueId("event-1");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(86400000L));
    event.setAllDay(true);
    event.setTitle("Party \"><img src=x onerror=alert(1)>");
    event.setLocation("<script>alert(1)</script>");

    StringBuilder sb = new StringBuilder();
    try (MockedStatic<CalendarRepository> calendars = mockStatic(CalendarRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      calendars.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));

      CalendarAjaxEvents.addCalendarEvents(1L, null, new Date(0L), new Date(86400000L), sb);

      String json = sb.toString();
      Assertions.assertFalse(json.contains("<img"), "raw markup must not appear: " + json);
      Assertions.assertFalse(json.contains("<script"), "raw markup must not appear: " + json);
      Assertions.assertTrue(json.contains("&lt;img"), "title must be HTML-encoded: " + json);
      Assertions.assertTrue(json.contains("&lt;script"), "location must be HTML-encoded: " + json);
    }
  }
}
