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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;

/**
 * Regression coverage for a calendar's "Online?" checkbox (Calendar.enabled) gating its events
 * off the upcoming-events widget for a regular visitor, mirroring CalendarEventDetailsWidget's
 * existing admin/content-manager bypass for the single-event details page.
 *
 * @author SimIS Inc.
 */
class UpcomingCalendarEventsWidgetTest extends WidgetBase {

  @Test
  @SuppressWarnings("unchecked")
  void executeRequestsTheEnabledCalendarFilterForANonAdminVisitor() {
    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
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
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
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
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
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
}
