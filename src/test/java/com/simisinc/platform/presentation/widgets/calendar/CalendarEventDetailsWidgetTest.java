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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadCalendarCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * The single-event details page resolves its event with
 * {@code CalendarEventRepository.findByUniqueId(String)}, a lookup that applies no visibility
 * filtering, and used to gate only on the parent calendar's "Online?" flag. A draft or archived
 * event on an enabled calendar therefore rendered in full to any visitor who knew its uniqueId,
 * while every list/feed surface correctly hid it.
 *
 * <p>These cover the two states the widget now checks, and the difference between them:
 *
 * <ul>
 * <li>unpublished ({@code published IS NULL}) is a preview gate -- hidden from a guest, still
 * visible to admin/content-manager, matching CalendarEventAjax and the CalendarAjaxEvents grid
 * feed, which will show a draft event to a previewer and link here;
 * <li>archived ({@code archived IS NOT NULL}) is not a preview gate -- per issue #882 it is a
 * distinct "no longer relevant" state and is honored for every visitor, previewer included.
 * </ul>
 *
 * <p>The pre-existing calendar-{@code enabled} bypass is pinned here too, so a later change cannot
 * quietly drop it while adding to this block.
 *
 * @author SimIS Inc.
 */
class CalendarEventDetailsWidgetTest extends WidgetBase {

  private static final String EVENT_UNIQUE_ID = "summer-fair";

  private static CalendarEvent event(Timestamp published, Timestamp archived) {
    CalendarEvent calendarEvent = new CalendarEvent();
    calendarEvent.setId(10L);
    calendarEvent.setCalendarId(5L);
    calendarEvent.setUniqueId(EVENT_UNIQUE_ID);
    calendarEvent.setTitle("Summer Fair");
    calendarEvent.setPublished(published);
    calendarEvent.setArchived(archived);
    return calendarEvent;
  }

  private static Calendar calendar(boolean enabled) {
    Calendar calendar = new Calendar();
    calendar.setId(5L);
    calendar.setName("Community");
    calendar.setEnabled(enabled);
    return calendar;
  }

  /**
   * Runs the widget against the given event/calendar pair. The event is built by the caller before
   * the static mocks open, so no stubbing happens inside another stub's argument.
   */
  private WidgetContext execute(CalendarEvent calendarEvent, Calendar calendar) {
    when(request.getRequestURI()).thenReturn("/calendar-event/" + EVENT_UNIQUE_ID);
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadCalendarCommand> calendarCommand = mockStatic(LoadCalendarCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> CalendarEventRepository.findByUniqueId(anyString())).thenReturn(calendarEvent);
      calendarCommand.when(() -> LoadCalendarCommand.loadCalendarById(anyLong())).thenReturn(calendar);
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      return new CalendarEventDetailsWidget().execute(widgetContext);
    }
  }

  /** The defect: a draft event on an enabled calendar, fetched by direct URL as an anonymous visitor. */
  @Test
  void executeHidesAnUnpublishedEventFromAGuest() {
    logout(widgetContext);
    CalendarEvent calendarEvent = event(null, null);

    assertNull(execute(calendarEvent, calendar(true)));
    assertNull(request.getAttribute("calendarEvent"));
  }

  /** A logged-in visitor with no content roles is no more privileged than a guest here. */
  @Test
  void executeHidesAnUnpublishedEventFromASignedInVisitorWithoutAContentRole() {
    CalendarEvent calendarEvent = event(null, null);

    assertNull(execute(calendarEvent, calendar(true)));
  }

  /** The preview path a content-manager needs before publishing; must survive the fix above. */
  @Test
  void executeShowsAnUnpublishedEventToAContentManager() {
    setRoles(widgetContext, CONTENT_MANAGER);
    CalendarEvent calendarEvent = event(null, null);

    WidgetContext result = execute(calendarEvent, calendar(true));

    assertNotNull(result);
    assertSame(calendarEvent, request.getAttribute("calendarEvent"));
    assertNotNull(result.getJsp());
  }

  @Test
  void executeShowsAnUnpublishedEventToAnAdmin() {
    setRoles(widgetContext, ADMIN);
    CalendarEvent calendarEvent = event(null, null);

    assertNotNull(execute(calendarEvent, calendar(true)));
  }

  /** The ordinary public page: published, not archived, calendar online. */
  @Test
  void anEventWithArtworkBecomesItsOwnSocialCard() {
    // Without this bridge main.jsp falls back to site.image, so every event ever shared showed the
    // same generic site card. WebContainerCommand copies pageImageUrl onto pageRenderInfo, which is
    // what og:image reads -- the same one line BlogPostWidget uses.
    logout(widgetContext);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()), null);
    calendarEvent.setImageUrl("/assets/img/20260101000000-1/iitsec.png");

    WidgetContext result = execute(calendarEvent, calendar(true));

    assertNotNull(result);
    Assertions.assertEquals("/assets/img/20260101000000-1/iitsec.png", result.getPageImageUrl());
  }

  @Test
  void anEventWithoutArtworkLeavesTheSiteDefaultInPlace() {
    // Blank must not be bridged: setting it would replace the site-wide og:image with an empty
    // string rather than falling back to it.
    logout(widgetContext);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()), null);

    WidgetContext result = execute(calendarEvent, calendar(true));

    assertNotNull(result);
    assertNull(result.getPageImageUrl());
  }

  @Test
  void executeShowsAPublishedEventToAGuest() {
    logout(widgetContext);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()), null);

    WidgetContext result = execute(calendarEvent, calendar(true));

    assertNotNull(result);
    assertSame(calendarEvent, request.getAttribute("calendarEvent"));
  }

  @Test
  void executeHidesAnArchivedEventFromAGuest() {
    logout(widgetContext);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()),
        new Timestamp(System.currentTimeMillis()));

    assertNull(execute(calendarEvent, calendar(true)));
  }

  /**
   * Issue #882's rule, and the one place this widget deliberately does not grant the preview
   * bypass: archived is a content-lifecycle state, not a publishing gate, so an archived event is
   * unreachable here even for an admin -- the same unconditional setArchivedOnly(false) the four
   * sibling calendar surfaces apply.
   */
  @Test
  void executeHidesAnArchivedEventFromAContentManager() {
    setRoles(widgetContext, CONTENT_MANAGER);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()),
        new Timestamp(System.currentTimeMillis()));

    assertNull(execute(calendarEvent, calendar(true)));
  }

  @Test
  void executeHidesAnArchivedEventFromAnAdmin() {
    setRoles(widgetContext, ADMIN);
    CalendarEvent calendarEvent = event(null, new Timestamp(System.currentTimeMillis()));

    assertNull(execute(calendarEvent, calendar(true)));
  }

  /** Pre-existing behavior: a calendar switched offline hides its published events from a guest. */
  @Test
  void executeHidesAPublishedEventOnADisabledCalendarFromAGuest() {
    logout(widgetContext);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()), null);

    assertNull(execute(calendarEvent, calendar(false)));
  }

  /** Pre-existing bypass: an admin still previews an offline calendar's published event. */
  @Test
  void executeShowsAPublishedEventOnADisabledCalendarToAnAdmin() {
    setRoles(widgetContext, ADMIN);
    CalendarEvent calendarEvent = event(new Timestamp(System.currentTimeMillis()), null);

    assertNotNull(execute(calendarEvent, calendar(false)));
  }

  /** An unknown uniqueId is still a plain miss, not an error. */
  @Test
  void executeReturnsNullWhenTheEventDoesNotExist() {
    logout(widgetContext);

    assertNull(execute(null, calendar(true)));
  }
}
