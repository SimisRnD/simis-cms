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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the calendarId facet added to CalendarSearchResultsWidget (issue #634), mirroring the
 * coverage ItemsSearchResultsWidgetTest has for the categoryId/dateFacet facets.
 *
 * @author SimIS Inc.
 */
class CalendarSearchResultsWidgetTest extends WidgetBase {

  private static Calendar calendar(long id, String name) {
    Calendar calendar = new Calendar();
    calendar.setId(id);
    calendar.setName(name);
    return calendar;
  }

  private static CalendarEvent event(long id) {
    CalendarEvent event = new CalendarEvent();
    event.setId(id);
    event.setUniqueId("event-" + id);
    event.setTitle("Event " + id);
    return event;
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeAppliesTheCalendarIdParamAndOnlyListsCalendarsWithResultsOrSelected() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "calendarId", "5");

    List<CalendarEvent> eventList = new ArrayList<>();
    eventList.add(event(1L));

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // CalendarSearchResultsWidget now reads the site timezone via FormatDateCommand.getSiteZoneId(),
      // which calls the two-arg loadByName(name, defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(eventList);
      calendarRepository.when(CalendarRepository::findAll).thenReturn(List.of(calendar(5, "Team Events"), calendar(6, "Holidays")));
      calendarRepository.when(() -> CalendarRepository.findById(5L)).thenReturn(calendar(5, "Team Events"));
      // Calendar 5 has results, calendar 6 does not and is not selected -- omitted entirely
      repository.when(() -> CalendarEventRepository.findCount(any(CalendarEventSpecification.class)))
          .thenAnswer(invocation -> {
            CalendarEventSpecification spec = invocation.getArgument(0);
            return spec.getCalendarId() == 5L ? 3L : 0L;
          });

      WidgetContext result = new CalendarSearchResultsWidget().execute(widgetContext);

      List<FacetUrlCommand.FacetOption> calendarFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("calendarFacets");
      assertEquals(1, calendarFacets.size(), "calendar 6 has a 0 count and is not selected, so it must not be listed");
      assertEquals("Team Events", calendarFacets.get(0).getLabel());
      assertEquals(3L, calendarFacets.get(0).getCount());
      assertTrue(calendarFacets.get(0).isSelected());

      List<FacetUrlCommand.ActiveFacetFilter> activeFilters = (List<FacetUrlCommand.ActiveFacetFilter>) result.getRequest().getAttribute("activeFilters");
      assertEquals(1, activeFilters.size());
      assertEquals("Calendar", activeFilters.get(0).getFacetLabel());
      assertEquals("Team Events", activeFilters.get(0).getValueLabel());
    }
  }

  @Test
  void executeWithNoCalendarIdSelectedHasNoActiveFilters() {
    addQueryParameter(widgetContext, "query", "widgets");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // CalendarSearchResultsWidget now reads the site timezone via FormatDateCommand.getSiteZoneId(),
      // which calls the two-arg loadByName(name, defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      WidgetContext result = new CalendarSearchResultsWidget().execute(widgetContext);

      assertTrue(((List<?>) result.getRequest().getAttribute("activeFilters")).isEmpty());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeGivesEveryCalendarsOwnStandaloneCountRegardlessOfSelection() {
    // Selecting calendar 5 must not zero out calendar 6's own count -- each is counted standalone
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "calendarId", "5");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      // CalendarSearchResultsWidget now reads the site timezone via FormatDateCommand.getSiteZoneId(),
      // which calls the two-arg loadByName(name, defaultValue) overload.
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(List.of(calendar(5, "Team Events"), calendar(6, "Holidays")));
      calendarRepository.when(() -> CalendarRepository.findById(5L)).thenReturn(calendar(5, "Team Events"));
      repository.when(() -> CalendarEventRepository.findCount(any(CalendarEventSpecification.class)))
          .thenAnswer(invocation -> {
            CalendarEventSpecification spec = invocation.getArgument(0);
            return spec.getCalendarId() == 6L ? 4L : 2L;
          });

      WidgetContext result = new CalendarSearchResultsWidget().execute(widgetContext);

      List<FacetUrlCommand.FacetOption> calendarFacets = (List<FacetUrlCommand.FacetOption>) result.getRequest().getAttribute("calendarFacets");
      assertEquals(2, calendarFacets.size());
      assertEquals(4L, calendarFacets.get(1).getCount(), "calendar 6's own count must not be affected by calendar 5 being selected");
    }
  }

  @Test
  void executeReturnsNullForABlankQuery() {
    assertNull(new CalendarSearchResultsWidget().execute(widgetContext));
  }

  /**
   * Regression test: a calendar's "Online?" checkbox (Calendar.enabled) is meant to take its
   * events off search results for a regular visitor, mirroring CalendarEventDetailsWidget's
   * existing admin/content-manager bypass for the single-event details page.
   */
  @Test
  @SuppressWarnings("unchecked")
  void executeRequestsTheEnabledCalendarFilterForANonAdminVisitor() {
    addQueryParameter(widgetContext, "query", "widgets");

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarSearchResultsWidget().execute(widgetContext);

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
    addQueryParameter(widgetContext, "query", "widgets");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class);
        MockedStatic<CalendarRepository> calendarRepository = mockStatic(CalendarRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
      repository.when(() -> CalendarEventRepository.findAll(any(CalendarEventSpecification.class), any())).thenReturn(new ArrayList<>());
      calendarRepository.when(CalendarRepository::findAll).thenReturn(new ArrayList<>());

      new CalendarSearchResultsWidget().execute(widgetContext);

      ArgumentCaptor<CalendarEventSpecification> specCaptor = ArgumentCaptor.forClass(CalendarEventSpecification.class);
      repository.verify(() -> CalendarEventRepository.findAll(specCaptor.capture(), any()));
      assertFalse(specCaptor.getValue().isCalendarEnabledOnly());
    }
  }
}
