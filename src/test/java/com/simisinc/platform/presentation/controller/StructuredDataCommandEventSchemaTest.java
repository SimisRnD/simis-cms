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

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;

/**
 * Event schema generation for a single calendar event page (issue #1181).
 */
class StructuredDataCommandEventSchemaTest {

  private static CalendarEvent event(String title, String uniqueId) {
    CalendarEvent calendarEvent = new CalendarEvent();
    calendarEvent.setTitle(title);
    calendarEvent.setUniqueId(uniqueId);
    calendarEvent.setStartDate(Timestamp.valueOf("2026-09-15 14:00:00"));
    calendarEvent.setEndDate(Timestamp.valueOf("2026-09-17 22:00:00"));
    return calendarEvent;
  }

  private static PageRenderInfo renderInfoFor(CalendarEvent calendarEvent) {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setCalendarEvent(calendarEvent);
    return pageRenderInfo;
  }

  @Test
  void computeEventSchemaReturnsNullWithoutABridgedEvent() {
    assertNull(StructuredDataCommand.computeEventSchema(new PageRenderInfo(), "https://example.org"));
  }

  @Test
  void computeEventSchemaReturnsNullForAnEventWithNoTitle() {
    // schema.org Event requires a name; emitting one without it would fail validation
    CalendarEvent calendarEvent = event(null, "sea-air-space-2026");
    assertNull(StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent), "https://example.org"));
  }

  @Test
  void computeEventSchemaEmitsTheCoreEventProperties() {
    CalendarEvent calendarEvent = event("Sea Air Space 2026", "sea-air-space-2026");
    calendarEvent.setSummary("Visit the SimIS booth");

    Map<String, Object> schema = StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent),
        "https://example.org");

    assertNotNull(schema);
    assertEquals("Event", schema.get("@type"));
    assertEquals("Sea Air Space 2026", schema.get("name"));
    assertEquals("https://example.org/calendar-event/sea-air-space-2026", schema.get("url"));
    assertEquals("Visit the SimIS booth", schema.get("description"));
    assertEquals("https://schema.org/EventScheduled", schema.get("eventStatus"));
  }

  @Test
  void computeEventSchemaFallsBackToTheBodyWithMarkupStripped() {
    // JSON-LD description is plain text; raw HTML there is ignored at best
    CalendarEvent calendarEvent = event("Trade Show", "trade-show");
    calendarEvent.setBody("<p>Meet us at <strong>booth 412</strong></p>");

    Map<String, Object> schema = StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent),
        "https://example.org");

    assertEquals("Meet us at booth 412", schema.get("description"));
  }

  @Test
  void computeEventSchemaOmitsLocationAndAttendanceModeWhenThereIsNoPlace() {
    // Claiming an attendance mode for an event with no location at all would be inventing data
    CalendarEvent calendarEvent = event("Webinar", "webinar");

    Map<String, Object> schema = StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent),
        "https://example.org");

    assertFalse(schema.containsKey("location"));
    assertFalse(schema.containsKey("eventAttendanceMode"));
  }

  @Test
  void computeEventSchemaAddsAttendanceModeOnceThereIsAPlace() {
    CalendarEvent calendarEvent = event("Sea Air Space 2026", "sea-air-space-2026");
    calendarEvent.setLocation("Gaylord National Resort");

    Map<String, Object> schema = StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent),
        "https://example.org");

    assertEquals("https://schema.org/OfflineEventAttendanceMode", schema.get("eventAttendanceMode"));
  }

  @Test
  void computeEventSchemaMakesARelativeImageUrlAbsolute() {
    CalendarEvent calendarEvent = event("Trade Show", "trade-show");
    calendarEvent.setImageUrl("/assets/img/1/booth.png");

    Map<String, Object> schema = StructuredDataCommand.computeEventSchema(renderInfoFor(calendarEvent),
        "https://example.org");

    assertEquals("https://example.org/assets/img/1/booth.png", schema.get("image"));
  }

  @Test
  void computeEventLocationReturnsNullWithNeitherNameNorAddress() {
    // An empty Place adds nothing and Google treats it as a validation error
    assertNull(StructuredDataCommand.computeEventLocation(event("Webinar", "webinar")));
  }

  @Test
  void computeEventLocationBuildsAPostalAddress() {
    CalendarEvent calendarEvent = event("Sea Air Space 2026", "sea-air-space-2026");
    calendarEvent.setLocation("Gaylord National Resort");
    calendarEvent.setStreet("201 Waterfront St");
    calendarEvent.setCity("National Harbor");
    calendarEvent.setState("MD");
    calendarEvent.setPostalCode("20745");
    calendarEvent.setCountry("US");

    Map<String, Object> place = StructuredDataCommand.computeEventLocation(calendarEvent);

    assertEquals("Place", place.get("@type"));
    assertEquals("Gaylord National Resort", place.get("name"));

    @SuppressWarnings("unchecked")
    Map<String, Object> address = (Map<String, Object>) place.get("address");
    assertEquals("PostalAddress", address.get("@type"));
    assertEquals("201 Waterfront St", address.get("streetAddress"));
    assertEquals("National Harbor", address.get("addressLocality"));
    assertEquals("MD", address.get("addressRegion"));
    assertEquals("20745", address.get("postalCode"));
    assertEquals("US", address.get("addressCountry"));
  }

  @Test
  void computeEventLocationOmitsGeoWhenNeverGeocoded() {
    // 0.0/0.0 is the model default for "not geocoded", not a real point in the Atlantic
    CalendarEvent calendarEvent = event("Trade Show", "trade-show");
    calendarEvent.setLocation("Somewhere");

    Map<String, Object> place = StructuredDataCommand.computeEventLocation(calendarEvent);

    assertFalse(place.containsKey("geo"));
  }

  @Test
  void computeEventLocationIncludesGeoOnceGeocoded() {
    CalendarEvent calendarEvent = event("Trade Show", "trade-show");
    calendarEvent.setLocation("Gaylord National Resort");
    calendarEvent.setLatitude(38.7817);
    calendarEvent.setLongitude(-77.0169);

    @SuppressWarnings("unchecked")
    Map<String, Object> geo = (Map<String, Object>) StructuredDataCommand.computeEventLocation(calendarEvent).get("geo");

    assertEquals("GeoCoordinates", geo.get("@type"));
    assertEquals(38.7817, geo.get("latitude"));
    assertEquals(-77.0169, geo.get("longitude"));
  }

  @Test
  void formatEventDateReturnsNullForAMissingDate() {
    assertNull(StructuredDataCommand.formatEventDate(null, false));
    assertNull(StructuredDataCommand.formatEventDate(null, true));
  }

  @Test
  void formatEventDateEmitsAFullInstantForATimedEvent() {
    String formatted = StructuredDataCommand.formatEventDate(Timestamp.valueOf("2026-09-15 14:00:00"), false);
    assertTrue(formatted.endsWith("Z"), "expected an ISO-8601 instant, got: " + formatted);
  }

  @Test
  void formatEventDateEmitsABareCalendarDateForAnAllDayEventInTheSiteTimezone() {
    // The whole point of the all-day branch: rendering a US-timezone all-day event as a UTC
    // instant shifts it to the following day. A timestamp late on the 15th in New York is
    // already the 16th in UTC, so a naive toInstant() would report the wrong date.
    Timestamp lateOnTheFifteenth = Timestamp.from(
        java.time.LocalDateTime.of(2026, 9, 15, 20, 0).atZone(ZoneId.of("America/New_York")).toInstant());

    try (MockedStatic<FormatDateCommand> dates = mockStatic(FormatDateCommand.class)) {
      dates.when(FormatDateCommand::getSiteZoneId).thenReturn(ZoneId.of("America/New_York"));

      assertEquals("2026-09-15", StructuredDataCommand.formatEventDate(lateOnTheFifteenth, true));
      // and the same instant read as UTC really would land on the 16th
      assertTrue(lateOnTheFifteenth.toInstant().toString().startsWith("2026-09-16"));
    }
  }
}
