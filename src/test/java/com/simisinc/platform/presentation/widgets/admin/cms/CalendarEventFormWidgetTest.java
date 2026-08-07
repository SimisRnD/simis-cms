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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.SaveCalendarEventCommand;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Issue #426: the admin calendar-event-list edit link
 * ({@code ${ctx}/admin/calendar-event?calendarEventId=${event.id}&returnPage=/admin/calendars}) has
 * always sent {@code calendarEventId}, but execute()'s GET path only understood {@code calendarId}
 * (for pre-selecting a calendar on a brand-new event) -- the load-by-id branch was commented out as
 * "not yet implemented". So clicking an edit link landed on a blank create form: the
 * {@code calendarEvent} bean defaulted to id=-1 via calendar-event-form.jsp's
 * {@code <jsp:useBean>}, and because the hidden field is {@code name="id"} and
 * CalendarEventRepository.save() branches add-vs-update on {@code id > -1}, submitting that blank
 * form silently created a duplicate event instead of updating the original.
 *
 * These tests guard the fix: execute() now loads the existing event by {@code calendarEventId} (the
 * same param name the link already sends) and populates the form bean from it, following the same
 * load-existing-record pattern as WebPageFormWidget.execute() (webPageId -> findById -> null-check).
 */
class CalendarEventFormWidgetTest extends WidgetBase {

  @Test
  void executeWithCalendarEventIdLoadsTheExistingEventForEditing() {
    CalendarEvent existing = new CalendarEvent();
    existing.setId(42L);
    existing.setCalendarId(3L);
    existing.setTitle("Town Hall");
    existing.setSummary("Quarterly update");
    existing.setStartDate(Timestamp.valueOf("2026-09-01 18:00:00"));
    existing.setEndDate(Timestamp.valueOf("2026-09-01 19:30:00"));

    addQueryParameter(widgetContext, "calendarEventId", "42");
    addQueryParameter(widgetContext, "returnPage", "/admin/calendars");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(existing);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verify(() -> CalendarEventRepository.findById(42L), times(1));
      CalendarEvent formBean = (CalendarEvent) result.getRequest().getAttribute("calendarEvent");
      assertEquals(42L, formBean.getId());
      assertEquals(3L, formBean.getCalendarId());
      assertEquals("Town Hall", formBean.getTitle());
      assertEquals("Quarterly update", formBean.getSummary());
      assertEquals(Timestamp.valueOf("2026-09-01 18:00:00"), formBean.getStartDate());
      assertEquals(Timestamp.valueOf("2026-09-01 19:30:00"), formBean.getEndDate());
    }
  }

  /**
   * If the id doesn't resolve to a record (e.g. a stale or tampered link), no bean is published to
   * the request rather than silently exposing a blank id=-1 "new event" form under an edit link --
   * calendar-event-form.jsp's own &lt;jsp:useBean&gt; fallback then takes over, same as if
   * calendarEventId had never been passed at all.
   */
  @Test
  void executeWithUnknownCalendarEventIdPublishesNoBean() {
    addQueryParameter(widgetContext, "calendarEventId", "999");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      assertNull(result.getRequest().getAttribute("calendarEvent"));
    }
  }

  /**
   * The create flow (no calendarEventId at all -- e.g. "New Event" from the calendar list, or from a
   * specific calendar's page) must keep working exactly as before: no lookup happens, and when a
   * calendarId is given the bean is a fresh CalendarEvent pre-populated with just that calendar, so
   * the hidden id field renders as -1 and CalendarEventRepository.save() takes the add path.
   */
  @Test
  void executeWithOnlyCalendarIdStillPreSelectsTheCalendarForANewEvent() {
    addQueryParameter(widgetContext, "calendarId", "3");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      CalendarEvent formBean = (CalendarEvent) result.getRequest().getAttribute("calendarEvent");
      assertEquals(-1L, formBean.getId());
      assertEquals(3L, formBean.getCalendarId());
    }
  }

  /**
   * With neither calendarEventId nor calendarId (e.g. a bare "New Event" link with no calendar
   * context), no bean is published and no repository lookup happens -- calendar-event-form.jsp's
   * &lt;jsp:useBean&gt; fallback supplies a blank CalendarEvent.
   */
  @Test
  void executeWithNoIdParametersPublishesNoBeanAndDoesNotQueryTheRepository() {
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertNull(result.getRequest().getAttribute("calendarEvent"));
    }
  }

  /**
   * When post() sent the widget back here after a validation failure, execute() must render the
   * request object it was handed (the just-submitted, still-invalid bean) rather than re-loading
   * from the repository -- otherwise the user's in-progress edits would vanish.
   */
  @Test
  void executePrefersAPendingRequestObjectOverLoadingById() {
    CalendarEvent rejected = new CalendarEvent();
    rejected.setId(42L);
    rejected.setTitle("Unsaved edits");
    widgetContext.setRequestObject(rejected);

    addQueryParameter(widgetContext, "calendarEventId", "42");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      repository.verifyNoInteractions();
      assertEquals(rejected, result.getRequest().getAttribute("calendarEvent"));
    }
  }

  /**
   * The tagsList input is a single comma-separated text field, but the bean stores it as a
   * String[] -- execute() pre-joins it into a request attribute so the JSP can render it through
   * one c:out rather than escaping-then-reassembling an array inside an HTML attribute.
   */
  @Test
  void executePreJoinsAnExistingEventsTagsListForTheTextInput() {
    CalendarEvent existing = new CalendarEvent();
    existing.setId(42L);
    existing.setTagsList(new String[] { "conference", "quarterly", "all-hands" });

    addQueryParameter(widgetContext, "calendarEventId", "42");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(existing);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      assertEquals("conference, quarterly, all-hands", result.getRequest().getAttribute("tagsListValue"));
    }
  }

  @Test
  void executeSetsNoTagsListValueWhenTheEventHasNoTags() {
    CalendarEvent existing = new CalendarEvent();
    existing.setId(42L);

    addQueryParameter(widgetContext, "calendarEventId", "42");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(existing);

      WidgetContext result = new CalendarEventFormWidget().execute(widgetContext);

      assertNull(result.getRequest().getAttribute("tagsListValue"));
    }
  }

  // --- permission gate (post()) ---

  /**
   * CalendarEventFormWidget.post() previously had no in-widget role check at all -- mutating a
   * calendar event relied solely on the page-level role gate in admin-layout.xml. This mirrors
   * CalendarWidget.post()'s identical admin/content-manager pairing for the same CalendarEvent
   * entity.
   */
  @Test
  void communityManagerCannotSaveAnEvent() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      new CalendarEventFormWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
    }
  }

  @Test
  void contentManagerCanSaveAnEvent() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");

    CalendarEvent saved = new CalendarEvent();
    saved.setId(50L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      WidgetContext result = new CalendarEventFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(any()), times(1));
      assertEquals("Event was saved", result.getSuccessMessage());
    }
  }

  // --- the critical bug fix: full field parity on save ---

  /**
   * The core regression this fix closes: previously, saving through this form only ever
   * submitted id/calendarId/title/summary/allDay/startDate/endDate, so SaveCalendarEventCommand's
   * unconditional full-overwrite semantics silently reset location/links/tags/published to
   * blank/draft on every save. This proves a fully-populated submission now reaches the save
   * command with every one of those fields intact.
   */
  @Test
  void postSavesEveryFieldTheFullCalendarEditorAlsoSubmits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");
    addQueryParameter(widgetContext, "summary", "Quarterly update");
    addQueryParameter(widgetContext, "location", "Main Auditorium");
    addQueryParameter(widgetContext, "detailsUrl", "https://example.org/details");
    addQueryParameter(widgetContext, "signUpUrl", "https://example.org/signup");
    addQueryParameter(widgetContext, "videoUrl", "https://example.org/meet");
    addQueryParameter(widgetContext, "tagsList", "conference, quarterly, all-hands");
    addQueryParameter(widgetContext, "enabled", "true");

    CalendarEvent saved = new CalendarEvent();
    saved.setId(42L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      new CalendarEventFormWidget().post(widgetContext);

      ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(captor.capture()));
      CalendarEvent bean = captor.getValue();

      assertEquals("Town Hall", bean.getTitle());
      assertEquals("Quarterly update", bean.getSummary());
      assertEquals("Main Auditorium", bean.getLocation());
      assertEquals("https://example.org/details", bean.getDetailsUrl());
      assertEquals("https://example.org/signup", bean.getSignUpUrl());
      assertEquals("https://example.org/meet", bean.getVideoUrl());
      assertArrayEquals(new String[] { "conference", "quarterly", "all-hands" }, bean.getTagsList());
      assertNotNull(bean.getPublished());
    }
  }

  @Test
  void postWithTheEnabledCheckboxUncheckedSavesAsUnpublished() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");
    // No "enabled" parameter at all -- mirrors an unchecked HTML checkbox, which submits nothing.

    CalendarEvent saved = new CalendarEvent();
    saved.setId(42L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      new CalendarEventFormWidget().post(widgetContext);

      ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(captor.capture()));
      assertNull(captor.getValue().getPublished());
    }
  }

  @Test
  void postWithNoTagsListParameterClearsTags() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");
    // No "tagsList" parameter at all -- e.g. every tag was removed from the field.

    CalendarEvent saved = new CalendarEvent();
    saved.setId(42L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      new CalendarEventFormWidget().post(widgetContext);

      ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(captor.capture()));
      assertNull(captor.getValue().getTagsList());
    }
  }

  @Test
  void postTrimsAndDropsEmptyEntriesFromTheTagsListField() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall");
    addQueryParameter(widgetContext, "tagsList", " conference ,, quarterly ,  ");

    CalendarEvent saved = new CalendarEvent();
    saved.setId(42L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      new CalendarEventFormWidget().post(widgetContext);

      ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(captor.capture()));
      assertArrayEquals(new String[] { "conference", "quarterly" }, captor.getValue().getTagsList());
    }
  }

  /**
   * Simulates what the JSP now does after this fix: an edit form pre-filled with an existing
   * event's full state (not just the handful of fields the old narrow form exposed), with only
   * the title changed by the user. Proves the round-trip preserves everything else instead of
   * silently wiping it -- the entire point of bringing this form up to field parity.
   */
  @Test
  void editingOnlyTheTitleThroughAFullyPrefilledFormPreservesEveryOtherField() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "calendarId", "3");
    addQueryParameter(widgetContext, "title", "Town Hall (rescheduled)");
    addQueryParameter(widgetContext, "summary", "Quarterly update");
    addQueryParameter(widgetContext, "location", "Main Auditorium");
    addQueryParameter(widgetContext, "detailsUrl", "https://example.org/details");
    addQueryParameter(widgetContext, "signUpUrl", "https://example.org/signup");
    addQueryParameter(widgetContext, "videoUrl", "https://example.org/meet");
    addQueryParameter(widgetContext, "tagsList", "quarterly, all-hands");
    addQueryParameter(widgetContext, "enabled", "true");

    CalendarEvent saved = new CalendarEvent();
    saved.setId(42L);

    try (MockedStatic<SaveCalendarEventCommand> saveCommand = mockStatic(SaveCalendarEventCommand.class)) {
      saveCommand.when(() -> SaveCalendarEventCommand.saveCalendarEvent(any())).thenReturn(saved);

      new CalendarEventFormWidget().post(widgetContext);

      ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
      saveCommand.verify(() -> SaveCalendarEventCommand.saveCalendarEvent(captor.capture()));
      CalendarEvent bean = captor.getValue();

      assertEquals("Town Hall (rescheduled)", bean.getTitle());
      assertEquals("Quarterly update", bean.getSummary());
      assertEquals("Main Auditorium", bean.getLocation());
      assertEquals("https://example.org/details", bean.getDetailsUrl());
      assertEquals("https://example.org/signup", bean.getSignUpUrl());
      assertEquals("https://example.org/meet", bean.getVideoUrl());
      assertArrayEquals(new String[] { "quarterly", "all-hands" }, bean.getTagsList());
      assertNotNull(bean.getPublished());
    }
  }
}
