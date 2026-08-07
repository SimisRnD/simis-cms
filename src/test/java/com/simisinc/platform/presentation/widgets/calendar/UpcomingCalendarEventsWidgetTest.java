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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers two independently-added concerns for this widget:
 *
 * <p>1. A calendar's "Online?" checkbox (Calendar.enabled) gating its events off the
 * upcoming-events widget for a regular visitor, mirroring CalendarEventDetailsWidget's existing
 * admin/content-manager bypass for the single-event details page.
 *
 * <p>2. Timezone resolution. execute() used to compute its date-range query with
 * {@code ZoneId.of(LoadSitePropertyCommand.loadByName("site.timezone"))} directly -- a null
 * (unset) site.timezone would NPE immediately, since {@code ZoneId.of(null)} throws. It now routes
 * through the shared {@code FormatDateCommand.getSiteZoneId()} helper (mirroring
 * CalendarSearchResultsWidget/ItemDateFacetCommand), which falls back to the JVM's default zone.
 *
 * <p>Note the site.timezone stub below uses the two-argument
 * {@code loadByName(name, defaultValue)} overload, because that is the one
 * {@code FormatDateCommand.getSiteZoneId()} calls. Stubbing only the single-argument overload
 * leaves the two-argument one returning null under mockStatic, which resurfaces as a
 * {@code ZoneId.of(null)} NPE reading simply "zoneId".
 *
 * @author SimIS Inc.
 */
class UpcomingCalendarEventsWidgetTest extends WidgetBase {

  @Test
  @SuppressWarnings("unchecked")
  void executeRequestsTheEnabledCalendarFilterForANonAdminVisitor() {
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());

      new UpcomingCalendarEventsWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
      assertTrue(specCaptor.getValue().isCalendarEnabledOnly());
    }
  }

  /**
   * The inverse of the above: an admin/content-manager previewing the site must still see events
   * on a currently-offline calendar (same bypass as CalendarEventDetailsWidget).
   */
  @Test
  @SuppressWarnings("unchecked")
  void executeDoesNotRequestTheEnabledCalendarFilterForAnAdminVisitor() {
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());

      new UpcomingCalendarEventsWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
      assertFalse(specCaptor.getValue().isCalendarEnabledOnly());
    }
  }

  /**
   * includeLastEvent's separate "find the last event" query (insertPastEvent) must honor the same
   * visitor-role bypass as the main query, not silently reveal an offline calendar's last event.
   */
  @Test
  @SuppressWarnings("unchecked")
  void includeLastEventAlsoRequestsTheEnabledCalendarFilterForANonAdminVisitor() {
    preferences.put("includeLastEvent", "true");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());

      new UpcomingCalendarEventsWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      // First the main query, then insertPastEvent's own query -- both must request the filter.
      repository.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()), times(2));
      for (CalendarEventSpecification specification : specCaptor.getAllValues()) {
        assertTrue(specification.isCalendarEnabledOnly());
      }
    }
  }

  @Test
  void executeQueriesUsingTheConfiguredSiteTimezone() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"upcomingCalendarEvents\" />");

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      WidgetContext result = new UpcomingCalendarEventsWidget().execute(widgetContext);

      assertEquals(UpcomingCalendarEventsWidget.JSP, result.getJsp());
      events.verify(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)));
    }
  }

  @Test
  void executeDoesNotThrowWhenSiteTimezoneIsUnset() {
    // getSiteZoneId() falls back to the JVM's default zone rather than NPEing on ZoneId.of(null)
    // the way the old direct ZoneId.of(LoadSitePropertyCommand.loadByName(...)) call would have.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"upcomingCalendarEvents\" />");

    try (MockedStatic<CalendarEventRepository> events = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      // loadByName(name, defaultValue) falls back to the passed-through default when unset
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any()))
          .thenAnswer(invocation -> invocation.getArgument(1));
      events.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      assertDoesNotThrow(() -> new UpcomingCalendarEventsWidget().execute(widgetContext));
    }
  }
}
