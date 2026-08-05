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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * saveCalendarEvent() used to unconditionally overwrite createdBy on every save, including an
 * edit of an existing record -- so editing a calendar event (e.g. fixing a typo) silently
 * reassigned its original creator to whoever happened to be editing it that day. modifiedBy is
 * correctly re-set on every save; createdBy must be set once, only when the record is genuinely
 * new.
 *
 * @author elizabeth houser
 */
class SaveCalendarEventCommandTest {

  private static CalendarEvent newEventBean(long calendarId) {
    CalendarEvent bean = new CalendarEvent();
    bean.setCalendarId(calendarId);
    bean.setTitle("Town Hall");
    Timestamp start = new Timestamp(System.currentTimeMillis());
    bean.setStartDate(start);
    bean.setEndDate(start);
    return bean;
  }

  @Test
  void newRecordGetsCreatedByFromTheSubmitter() throws DataException {
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L); // the current submitter, per CalendarEventFormWidget.post()

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> CalendarEventRepository.findByUniqueId(any(), any())).thenReturn(null);
      repository.when(() -> CalendarEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveCalendarEventCommand.saveCalendarEvent(bean);

      repository.verify(() -> CalendarEventRepository.save(argThat(saved -> saved.getCreatedBy() == 42L)));
    }
  }

  @Test
  void editingAnExistingRecordDoesNotChangeItsOriginalCreatedBy() throws DataException {
    CalendarEvent existing = new CalendarEvent();
    existing.setId(1L);
    existing.setCalendarId(1L);
    existing.setUniqueId("town-hall");
    existing.setTitle("Town Hall");
    existing.setCreatedBy(7L); // the original creator
    Timestamp start = new Timestamp(System.currentTimeMillis());
    existing.setStartDate(start);
    existing.setEndDate(start);

    CalendarEvent bean = newEventBean(1L);
    bean.setId(1L);
    bean.setStartDate(start);
    bean.setEndDate(start);
    bean.setCreatedBy(42L); // a different user editing it today

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> CalendarEventRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> CalendarEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveCalendarEventCommand.saveCalendarEvent(bean);

      repository.verify(() -> CalendarEventRepository.save(argThat(saved -> saved.getCreatedBy() == 7L)));
    }
  }

  @Test
  void editingAnExistingRecordStillUpdatesModifiedBy() throws DataException {
    // modifiedBy is a different field with different, correct semantics -- must keep working
    CalendarEvent existing = new CalendarEvent();
    existing.setId(1L);
    existing.setCalendarId(1L);
    existing.setUniqueId("town-hall");
    existing.setTitle("Town Hall");
    existing.setCreatedBy(7L);
    existing.setModifiedBy(7L);
    Timestamp start = new Timestamp(System.currentTimeMillis());
    existing.setStartDate(start);
    existing.setEndDate(start);

    CalendarEvent bean = newEventBean(1L);
    bean.setId(1L);
    bean.setStartDate(start);
    bean.setEndDate(start);
    bean.setCreatedBy(42L);
    bean.setModifiedBy(42L);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> CalendarEventRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> CalendarEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveCalendarEventCommand.saveCalendarEvent(bean);

      repository.verify(() -> CalendarEventRepository.save(argThat(saved -> saved.getModifiedBy() == 42L)));
    }
  }

  @Test
  void editingAMissingRecordThrowsBeforeTouchingCreatedBy() {
    CalendarEvent bean = newEventBean(1L);
    bean.setId(99L);
    bean.setCreatedBy(42L);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(99L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveCalendarEventCommand.saveCalendarEvent(bean));

      repository.verify(() -> CalendarEventRepository.save(any()), never());
    }
  }
}
