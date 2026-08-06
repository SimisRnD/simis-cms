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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * No test previously covered this widget at all. execute() used to compute its date-range query
 * with {@code ZoneId.of(LoadSitePropertyCommand.loadByName("site.timezone"))} directly -- a null
 * (unset) site.timezone would NPE immediately, since {@code ZoneId.of(null)} throws. It now routes
 * through the shared {@code FormatDateCommand.getSiteZoneId()} helper (mirroring
 * CalendarSearchResultsWidget/ItemDateFacetCommand), which falls back to the JVM's default zone
 * instead.
 */
class UpcomingCalendarEventsWidgetTest extends WidgetBase {

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
