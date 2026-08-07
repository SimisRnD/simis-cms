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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;

/**
 * @author Elizabeth Houser
 */
class CalendarAjaxEventsTest {

  /**
   * The calendar events feed (/json/calendar) returns event title/location/description as JSON that the
   * FullCalendar tooltip renders straight into markup via jQuery .html(). JSON-encoding is not HTML-safe,
   * so a crafted event title or location could inject markup (stored DOM XSS). This verifies the feed
   * HTML-encodes those fields so the payload is inert in the DOM.
   */
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
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      calendars.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");

      CalendarAjaxEvents.addCalendarEvents(1L, null, new Date(0L), new Date(86400000L), sb, false);

      String json = sb.toString();
      Assertions.assertFalse(json.contains("<img"), "raw markup must not appear: " + json);
      Assertions.assertFalse(json.contains("<script"), "raw markup must not appear: " + json);
      Assertions.assertTrue(json.contains("&lt;img"), "title must be HTML-encoded: " + json);
      Assertions.assertTrue(json.contains("&lt;script"), "location must be HTML-encoded: " + json);
    }
  }

  /**
   * Regression test for a bug where all-day dates were formatted with the JVM's default
   * timezone instead of the site's configured timezone (site.timezone), so an event stored near
   * midnight UTC could render on the wrong calendar day for a site configured in a non-UTC zone.
   * 2026-01-15T02:30:00Z is 2026-01-14 21:30 in America/New_York (EST, UTC-5, no DST in January)
   * -- the previous calendar day -- so the all-day date must reflect that, not the UTC day.
   */
  @Test
  void allDayDateIsFormattedInTheSiteTimezoneNotTheJvmDefault() {
    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setUniqueId("event-1");
    event.setAllDay(true);
    event.setStartDate(Timestamp.from(Instant.parse("2026-01-15T02:30:00Z")));
    event.setEndDate(Timestamp.from(Instant.parse("2026-01-16T02:30:00Z")));
    event.setTitle("Late Night Meeting");

    StringBuilder sb = new StringBuilder();
    try (MockedStatic<CalendarRepository> calendars = mockStatic(CalendarRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      calendars.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of(event));
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");

      CalendarAjaxEvents.addCalendarEvents(1L, null, new Date(0L), new Date(86400000L), sb, false);

      String json = sb.toString();
      Assertions.assertTrue(json.contains("\"start\":\"2026-01-14\""), "start date must be in the site's timezone: " + json);
      Assertions.assertTrue(json.contains("\"end\":\"2026-01-15T24:00\""), "end date must be in the site's timezone: " + json);
    }
  }

  /**
   * Regression test: a calendar's "Online?" checkbox (Calendar.enabled) is meant to take its
   * events off the public /json/calendar feed that small-calendar.jsp/full-calendar.jsp's
   * FullCalendar grids render, mirroring CalendarEventDetailsWidget's existing admin/
   * content-manager bypass for the single-event details page. CalendarAjax passes
   * publishedOnly=true for a non-previewing visitor, so that same signal must also request the
   * calendar-enabled filter from the repository.
   */
  @Test
  void publishedOnlyTrueRequestsTheEnabledCalendarFilterFromTheRepository() {
    StringBuilder sb = new StringBuilder();
    try (MockedStatic<CalendarRepository> calendars = mockStatic(CalendarRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      calendars.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of());
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");

      CalendarAjaxEvents.addCalendarEvents(1L, null, new Date(0L), new Date(86400000L), sb, true);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      events.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
      Assertions.assertTrue(specCaptor.getValue().isCalendarEnabledOnly());
    }
  }

  /**
   * The inverse of the above: CalendarAjax passes publishedOnly=false for an admin/content-manager
   * previewer, who must still see events on a currently-offline calendar (same bypass as
   * CalendarEventDetailsWidget).
   */
  @Test
  void publishedOnlyFalseDoesNotRequestTheEnabledCalendarFilter() {
    StringBuilder sb = new StringBuilder();
    try (MockedStatic<CalendarRepository> calendars = mockStatic(CalendarRepository.class);
        MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      calendars.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());
      events.when(() -> CalendarEventRepository.findAll(any(), any())).thenReturn(List.of());
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");

      CalendarAjaxEvents.addCalendarEvents(1L, null, new Date(0L), new Date(86400000L), sb, false);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      events.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
      Assertions.assertFalse(specCaptor.getValue().isCalendarEnabledOnly());
    }
  }
}
