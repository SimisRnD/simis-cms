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

import static org.junit.jupiter.api.Assertions.assertTrue;
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
  void theStructuredAddressReachesTheSavedRecord() throws DataException {
    // Google Search Console reports "Missing field address (in location)" for an Event whose
    // location carries only a name. StructuredDataCommand already builds the PostalAddress from
    // these five fields -- nothing could populate them, because this mapping did not exist.
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L);
    bean.setLocation("Orange County Convention Center");
    bean.setStreet("9899 International Drive");
    bean.setCity("Orlando");
    bean.setState("FL");
    bean.setPostalCode("32819");
    bean.setCountry("US");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> CalendarEventRepository.findByUniqueId(any(), any())).thenReturn(null);
      repository.when(() -> CalendarEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveCalendarEventCommand.saveCalendarEvent(bean);

      repository.verify(() -> CalendarEventRepository.save(argThat(saved -> "9899 International Drive".equals(saved.getStreet())
          && "Orlando".equals(saved.getCity())
          && "FL".equals(saved.getState())
          && "32819".equals(saved.getPostalCode())
          && "US".equals(saved.getCountry()))));
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

  @Test
  void theOrganizerAndPerformerCreditsReachTheSavedRecord() throws DataException {
    // Exactly the failure theStructuredAddressReachesTheSavedRecord above documents, repeated for
    // the fields added for the "organizer"/"performer" Search Console warnings: the form posts
    // them, BeanUtils.populate puts them on the bean, the column exists and the query reads it --
    // and this command copies the bean field by field, so an entry missing here is dropped
    // silently between a save that reports success and a page that never shows the credit.
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L);
    bean.setOrganizerName("National Training and Simulation Association (NTSA)");
    bean.setOrganizerUrl("https://www.trainingsystems.org");
    bean.setPerformerName("Dr. Johnny Garcia");
    bean.setPerformerUrl("https://www.simisinc.com/about-us");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> CalendarEventRepository.findByUniqueId(any(), any())).thenReturn(null);
      repository.when(() -> CalendarEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveCalendarEventCommand.saveCalendarEvent(bean);

      repository.verify(() -> CalendarEventRepository.save(argThat(saved -> "National Training and Simulation Association (NTSA)"
          .equals(saved.getOrganizerName())
          && "https://www.trainingsystems.org".equals(saved.getOrganizerUrl())
          && "Dr. Johnny Garcia".equals(saved.getPerformerName())
          && "https://www.simisinc.com/about-us".equals(saved.getPerformerUrl()))));
    }
  }

  /**
   * Issue #1938. calendar_events.end_date is NOT NULL, so a missing end date used to reach
   * PostgreSQL and the insert died on the constraint -- the author lost the event and saw a system
   * error instead of a field they could fix. Neither date was checked before this.
   */
  @Test
  void aMissingEndDateIsRejectedBeforeItReachesTheDatabase() {
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L); // saveCalendarEvent rejects an unset submitter before it reaches the dates
    bean.setEndDate(null);

    DataException e = assertThrows(DataException.class, () -> SaveCalendarEventCommand.saveCalendarEvent(bean));
    assertTrue(e.getMessage().contains("An end date is required"), e.getMessage());
  }

  /**
   * The start date failed the opposite way, which was worse because it did not fail: a null was
   * backfilled with the publish time, so a date the converter could not read saved "successfully"
   * as today and the event silently moved. Same shape as the blog defect in #1351.
   */
  @Test
  void aMissingStartDateIsRejectedRatherThanBackfilledWithThePublishTime() {
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L); // saveCalendarEvent rejects an unset submitter before it reaches the dates
    bean.setStartDate(null);
    bean.setPublished(new Timestamp(System.currentTimeMillis()));

    DataException e = assertThrows(DataException.class, () -> SaveCalendarEventCommand.saveCalendarEvent(bean));
    assertTrue(e.getMessage().contains("A start date is required"), e.getMessage());
  }

  @Test
  void bothMissingDatesAreReportedTogether() {
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L); // saveCalendarEvent rejects an unset submitter before it reaches the dates
    bean.setStartDate(null);
    bean.setEndDate(null);

    DataException e = assertThrows(DataException.class, () -> SaveCalendarEventCommand.saveCalendarEvent(bean));
    assertTrue(e.getMessage().contains("A start date is required"), e.getMessage());
    assertTrue(e.getMessage().contains("An end date is required"), e.getMessage());
  }

  /** The pre-existing ordering check still applies, and still only when both dates are present. */
  @Test
  void anEndDateBeforeTheStartDateIsStillRejected() {
    CalendarEvent bean = newEventBean(1L);
    bean.setCreatedBy(42L); // saveCalendarEvent rejects an unset submitter before it reaches the dates
    Timestamp start = new Timestamp(System.currentTimeMillis());
    bean.setStartDate(start);
    bean.setEndDate(new Timestamp(start.getTime() - 3_600_000L));

    DataException e = assertThrows(DataException.class, () -> SaveCalendarEventCommand.saveCalendarEvent(bean));
    assertTrue(e.getMessage().contains("The end date needs to come after the start date"), e.getMessage());
  }
}
