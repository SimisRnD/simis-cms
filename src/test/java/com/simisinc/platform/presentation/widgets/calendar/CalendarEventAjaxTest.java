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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.presentation.controller.DataConstants;

class CalendarEventAjaxTest extends WidgetBase {

  @Test
  void jsonIncludesVideoUrlWhenSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));
    event.setVideoUrl("https://teams.microsoft.com/l/meetup-join/abc");

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    // JsonCommand.toJson escapes "/" as "\/", so match that rather than a literal URL
    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"videoUrl\":\"https:\\/\\/teams.microsoft.com\\/l\\/meetup-join\\/abc\""),
        "videoUrl must be present in the JSON: " + json);
  }

  @Test
  void jsonCarriesTheAddressSoTheEditModalCanRoundTripIt() {
    // The calendar's own edit modal fills its fields from this feed and submits every one of them
    // back. SaveCalendarEventCommand overwrites each field from the submitted bean, so a field the
    // feed omits is a field that modal silently blanks the next time anyone saves the event.
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("I/ITSEC");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));
    event.setLocation("Orange County Convention Center");
    event.setStreet("9899 International Drive");
    event.setCity("Orlando");
    event.setState("FL");
    event.setPostalCode("32819");
    event.setCountry("US");

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"street\":\"9899 International Drive\""), "street: " + json);
    Assertions.assertTrue(json.contains("\"city\":\"Orlando\""), "city: " + json);
    Assertions.assertTrue(json.contains("\"state\":\"FL\""), "state: " + json);
    Assertions.assertTrue(json.contains("\"postalCode\":\"32819\""), "postalCode: " + json);
    Assertions.assertTrue(json.contains("\"country\":\"US\""), "country: " + json);
  }

  @Test
  void jsonOmitsTheAddressFieldsThatAreNotSet() {
    // Omitted rather than emitted empty: the modal treats an absent key as "leave this field
    // blank", and an event with no address should not ship five empty strings to every client.
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    for (String field : new String[] { "street", "city", "state", "postalCode", "country" }) {
      Assertions.assertFalse(json.contains("\"" + field + "\""), field + " must be absent: " + json);
    }
  }

  @Test
  void jsonOmitsVideoUrlWhenNotSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertFalse(json.contains("videoUrl"), "videoUrl must be omitted when not set: " + json);
  }

  @Test
  void jsonIncludesTagsListWhenSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));
    event.setTagsList(new String[] { "conference", "quarterly" });

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"tagsList\":[\"conference\",\"quarterly\"]"),
        "tagsList must be present as a JSON array in the JSON: " + json);
  }

  @Test
  void jsonOmitsTagsListWhenNull() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertFalse(json.contains("tagsList"), "tagsList must be omitted when not set: " + json);
  }

  @Test
  void jsonOmitsTagsListWhenEmpty() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));
    event.setTagsList(new String[0]);

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertFalse(json.contains("tagsList"), "tagsList must be omitted when empty: " + json);
  }

  @Test
  void jsonIncludesPublishedWhenSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));
    event.setPublished(new Timestamp(System.currentTimeMillis()));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"published\":true"), "published must be present in the JSON: " + json);
  }

  @Test
  void jsonOmitsPublishedWhenNotSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertFalse(json.contains("published"), "published must be omitted when not set: " + json);
  }

  @Test
  void nonPrivilegedCallerIsRestrictedToPublishedEvents() {
    // Default login() grants no roles, i.e. an unprivileged/logged-in-only caller.
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Draft Event");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);

      events.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
    }

    Assertions.assertEquals(DataConstants.TRUE, specCaptor.getValue().getPublishedOnly(),
        "a non-admin/content-manager caller must only be able to look up published events");
  }

  @Test
  void adminCallerCanSeeUnpublishedEvents() {
    addQueryParameter(widgetContext, "id", "1");
    setRoles(widgetContext, ADMIN);

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Draft Event");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);

      events.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
    }

    Assertions.assertEquals(DataConstants.UNDEFINED, specCaptor.getValue().getPublishedOnly(),
        "an admin/content-manager caller must not be filtered to published-only events");
  }

  /**
   * Regression test for a bug where a timed (non-allDay) event's date/time was formatted using
   * the JVM's default timezone instead of the site's configured timezone (site.timezone), so an
   * event stored near midnight UTC could render on the wrong calendar day for a site configured
   * in a non-UTC zone. 2026-01-15T02:30:00Z is 2026-01-14 21:30 in America/New_York (EST, UTC-5,
   * no DST in January) -- the previous calendar day, with a -05:00 offset -- so the JSON must
   * reflect that, not the UTC day/offset.
   */
  @Test
  void timedEventDateAndOffsetAreFormattedInTheSiteTimezoneNotTheJvmDefault() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Late Night Meeting");
    event.setStartDate(Timestamp.from(Instant.parse("2026-01-15T02:30:00Z")));
    event.setEndDate(Timestamp.from(Instant.parse("2026-01-15T03:30:00Z")));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertTrue(json.contains("\"start\":\"2026-01-14T21:30:00-05:00\""), "start must be in the site's timezone: " + json);
    Assertions.assertTrue(json.contains("\"end\":\"2026-01-14T22:30:00-05:00\""), "end must be in the site's timezone: " + json);
  }
}
