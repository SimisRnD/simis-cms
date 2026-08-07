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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class GenerateCalendarEventUniqueIdCommandTest {

  @Test
  void generateUniqueIdForNewCalendarEvent() {
    try (MockedStatic<CalendarEventRepository> calendarEventRepository = mockStatic(CalendarEventRepository.class)) {
      calendarEventRepository.when(() -> CalendarEventRepository.findByUniqueId(anyString())).thenReturn(null);

      CalendarEvent calendarEvent = new CalendarEvent();
      calendarEvent.setTitle("New Event");
      String uniqueId = GenerateCalendarEventUniqueIdCommand.generateUniqueId(null, calendarEvent);
      Assertions.assertEquals("new-event", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForUpdatedCalendarEvent() {
    try (MockedStatic<CalendarEventRepository> calendarEventRepository = mockStatic(CalendarEventRepository.class)) {
      calendarEventRepository.when(() -> CalendarEventRepository.findByUniqueId(anyString())).thenReturn(null);

      CalendarEvent previousCalendarEvent = new CalendarEvent();
      previousCalendarEvent.setTitle("Existing Event");
      CalendarEvent calendarEvent = new CalendarEvent();
      calendarEvent.setTitle("Existing Event");
      String uniqueId = GenerateCalendarEventUniqueIdCommand.generateUniqueId(previousCalendarEvent, calendarEvent);
      Assertions.assertEquals("existing-event", uniqueId);
    }
  }

  @Test
  void anExistingEventKeepsItsUniqueIdWhenRenamed() {
    // This is the bug: renaming the title used to regenerate the slug from the new title,
    // silently changing the event's URL and breaking every link that pointed at the old one.
    CalendarEvent previousCalendarEvent = new CalendarEvent();
    previousCalendarEvent.setUniqueId("my-event");
    previousCalendarEvent.setTitle("My Event");
    previousCalendarEvent.setCalendarId(1L);

    CalendarEvent renamed = new CalendarEvent();
    renamed.setTitle("My Rescheduled Event");
    renamed.setCalendarId(1L);

    String uniqueId = GenerateCalendarEventUniqueIdCommand.generateUniqueId(previousCalendarEvent, renamed);
    Assertions.assertEquals("my-event", uniqueId,
        "an existing event's URL must not change when only its title changes");
  }

  @Test
  void anExistingEventKeepsItsUniqueIdWhenMovedToADifferentCalendar() {
    // CalendarEventDetailsWidget resolves the public event page by uniqueId alone, not scoped by
    // calendarId, so a calendar move must not regenerate the slug either.
    CalendarEvent previousCalendarEvent = new CalendarEvent();
    previousCalendarEvent.setUniqueId("my-event");
    previousCalendarEvent.setTitle("My Event");
    previousCalendarEvent.setCalendarId(1L);

    CalendarEvent moved = new CalendarEvent();
    moved.setTitle("My Event");
    moved.setCalendarId(2L);

    String uniqueId = GenerateCalendarEventUniqueIdCommand.generateUniqueId(previousCalendarEvent, moved);
    Assertions.assertEquals("my-event", uniqueId);
  }

  @Test
  void generateUniqueIdForDuplicateCalendarEvent() {
    String existingUniqueId = "my-event";
    CalendarEvent existingCalendarEvent = new CalendarEvent();
    existingCalendarEvent.setUniqueId(existingUniqueId);

    try (MockedStatic<CalendarEventRepository> calendarEventRepository = mockStatic(CalendarEventRepository.class)) {
      calendarEventRepository.when(() -> CalendarEventRepository.findByUniqueId(anyLong(), eq(existingUniqueId))).thenReturn(existingCalendarEvent);

      CalendarEvent calendarEvent = new CalendarEvent();
      calendarEvent.setTitle("My Event");
      String uniqueId = GenerateCalendarEventUniqueIdCommand.generateUniqueId(null, calendarEvent);
      Assertions.assertEquals("my-event-2", uniqueId);
    }
  }
}