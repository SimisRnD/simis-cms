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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the 3 bulk actions on /admin/calendars (issue #882, mirroring PR #731's
 * UsersListWidgetBulkActionsTest for /admin/users): a batch over MAX_BULK_SELECTION is rejected
 * outright rather than truncated, an empty selection is rejected, one id that no longer resolves
 * never aborts the rest of the batch, bulkDelete triggers the same CalendarEventRemovedEvent
 * workflow the single-event public delete path (CalendarWidget#delete) already triggers, and only
 * admin/content-manager (the same pairing CalendarWidget's own post()/delete() already require) can
 * reach any of this.
 *
 * @author SimIS Inc.
 */
class CalendarEventListWidgetBulkActionsTest extends WidgetBase {

  private static CalendarEvent eventWithId(long id) {
    CalendarEvent event = new CalendarEvent();
    event.setId(id);
    event.setTitle("Event " + id);
    event.setCalendarId(1L);
    return event;
  }

  private static Calendar calendarWithId(long id) {
    Calendar calendar = new Calendar();
    calendar.setId(id);
    calendar.setName("Calendar " + id);
    return calendar;
  }

  private void multiValue(String name, String... values) {
    widgetContext.getParameterMap().put(name, values);
  }

  // --- Permission gate ---

  @Test
  void nonAdminNonContentManagerCannotReachBulkActionsAtAll() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    multiValue("eventId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class)) {
      new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.findById(any()), never());
      repo.verify(() -> CalendarEventRepository.remove(any()), never());
    }
  }

  @Test
  void contentManagerCanReachBulkActions() {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("eventId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    CalendarEvent event = eventWithId(5L);
    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(event);
      repo.when(() -> CalendarEventRepository.update(event)).thenReturn(event);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.update(event), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- Selection bounds (shared shape across all 3 commands; exercised once each) ---

  @Test
  void bulkArchiveOverCapIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    String[] tooMany = new String[CalendarEventListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 100);
    }
    multiValue("eventId", tooMany);
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.findById(any()), never());
      assertTrue(result.getErrorMessage().contains("Too many events"));
    }
  }

  @Test
  void emptySelectionIsRejectedForBulkDelete() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "bulkDelete");
    // No eventId parameters at all

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.findById(any()), never());
      assertEquals("No events were selected", result.getErrorMessage());
    }
  }

  // --- bulkArchive ---

  @Test
  void bulkArchiveSetsTheArchivedTimestampOnEachResolvedEvent() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    CalendarEvent first = eventWithId(5L);
    CalendarEvent second = eventWithId(6L);

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(first);
      repo.when(() -> CalendarEventRepository.findById(6L)).thenReturn(second);
      repo.when(() -> CalendarEventRepository.update(first)).thenReturn(first);
      repo.when(() -> CalendarEventRepository.update(second)).thenReturn(second);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      assertNull(first.getPublished(), "archiving must not touch published");
      assertNotNull(first.getArchived());
      assertNotNull(second.getArchived());
      repo.verify(() -> CalendarEventRepository.update(first), times(1));
      repo.verify(() -> CalendarEventRepository.update(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("calendarEvent.archive"),
          eq("success"), anyLong(), any(), any(), any(), eq("calendarEvent"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkArchiveSkipsAnIdThatNoLongerResolvesButContinues() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    CalendarEvent found = eventWithId(5L);

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(found);
      repo.when(() -> CalendarEventRepository.findById(6L)).thenReturn(null); // deleted concurrently / tampered id
      repo.when(() -> CalendarEventRepository.update(found)).thenReturn(found);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.update(any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Not found: 1"));
    }
  }

  // --- bulkMove ---

  @Test
  void bulkMoveWithoutAResolvableDestinationCalendarIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "calendarId", "999");

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calRepo = mockStatic(CalendarRepository.class)) {
      calRepo.when(() -> CalendarRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      // Rejected before any event is even loaded -- the destination is resolved first
      repo.verify(() -> CalendarEventRepository.findById(any()), never());
      assertEquals("The destination calendar was not found", result.getErrorMessage());
    }
  }

  @Test
  void bulkMoveUpdatesTheCalendarIdOnEachResolvedEvent() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "calendarId", "42");

    Calendar destination = calendarWithId(42L);
    CalendarEvent first = eventWithId(5L);
    CalendarEvent second = eventWithId(6L);

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calRepo = mockStatic(CalendarRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      calRepo.when(() -> CalendarRepository.findById(42L)).thenReturn(destination);
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(first);
      repo.when(() -> CalendarEventRepository.findById(6L)).thenReturn(second);
      repo.when(() -> CalendarEventRepository.update(first)).thenReturn(first);
      repo.when(() -> CalendarEventRepository.update(second)).thenReturn(second);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      assertEquals(42L, first.getCalendarId());
      assertEquals(42L, second.getCalendarId());
      repo.verify(() -> CalendarEventRepository.update(first), times(1));
      repo.verify(() -> CalendarEventRepository.update(second), times(1));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
      assertTrue(result.getSuccessMessage().contains("Calendar 42"));
    }
  }

  // --- bulkDelete ---

  @Test
  void bulkDeleteRemovesEachResolvedEventAndTriggersTheRemovedWorkflow() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    CalendarEvent first = eventWithId(5L);
    CalendarEvent second = eventWithId(6L);

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(first);
      repo.when(() -> CalendarEventRepository.findById(6L)).thenReturn(second);
      repo.when(() -> CalendarEventRepository.remove(first)).thenReturn(true);
      repo.when(() -> CalendarEventRepository.remove(second)).thenReturn(true);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      repo.verify(() -> CalendarEventRepository.remove(first), times(1));
      repo.verify(() -> CalendarEventRepository.remove(second), times(1));
      // Matches CalendarWidget#delete's single-event path: one workflow trigger per removed event
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(2));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("calendarEvent.delete"),
          eq("success"), anyLong(), any(), any(), any(), eq("calendarEvent"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkDeleteDoesNotTriggerTheWorkflowForAFailedRemoval() {
    setRoles(widgetContext, ADMIN);
    multiValue("eventId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    CalendarEvent event = eventWithId(5L);

    try (MockedStatic<CalendarEventRepository> repo = mockStatic(CalendarEventRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> CalendarEventRepository.findById(5L)).thenReturn(event);
      repo.when(() -> CalendarEventRepository.remove(event)).thenReturn(false);

      WidgetContext result = new CalendarEventListWidget().post(widgetContext);

      workflow.verifyNoInteractions();
      assertEquals("0 of 1 selected event deleted. Failed: 1.", result.getErrorMessage());
    }
  }
}
