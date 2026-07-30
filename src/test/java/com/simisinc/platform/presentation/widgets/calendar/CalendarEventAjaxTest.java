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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
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

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
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
  void jsonOmitsVideoUrlWhenNotSet() {
    addQueryParameter(widgetContext, "id", "1");

    CalendarEvent event = new CalendarEvent();
    event.setId(1L);
    event.setCalendarId(1L);
    event.setTitle("Team Sync");
    event.setStartDate(new Timestamp(0L));
    event.setEndDate(new Timestamp(3600000L));

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);
    }

    String json = widgetContext.getJson();
    Assertions.assertFalse(json.contains("videoUrl"), "videoUrl must be omitted when not set: " + json);
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

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
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

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
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
    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
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
    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class)) {
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(List.of(event));

      CalendarEventAjax widget = new CalendarEventAjax();
      widget.execute(widgetContext);

      events.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
    }

    Assertions.assertEquals(DataConstants.UNDEFINED, specCaptor.getValue().getPublishedOnly(),
        "an admin/content-manager caller must not be filtered to published-only events");
  }
}
