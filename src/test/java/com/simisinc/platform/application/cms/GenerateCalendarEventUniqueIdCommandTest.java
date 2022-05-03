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

public class GenerateCalendarEventUniqueIdCommandTest {

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