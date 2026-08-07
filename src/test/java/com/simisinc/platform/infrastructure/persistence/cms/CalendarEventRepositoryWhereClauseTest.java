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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;

/**
 * Pure unit tests (no database) on {@link CalendarEventRepository#createWhereStatement}'s built
 * {@link SqlUtils} output for the archived filter issue #882 adds -- mirrors the shape of
 * {@code ItemRepositoryWhereClauseTest}. These always run, unlike {@link CalendarEventRepositoryTest}
 * (which needs a real PostgreSQL instance and is skipped without Docker).
 *
 * @author SimIS Inc.
 */
class CalendarEventRepositoryWhereClauseTest {

  private static boolean whereContains(SqlUtils where, String fragment) {
    for (SqlValue value : where.getValues()) {
      if (value.getFieldOrClause() != null && value.getFieldOrClause().contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void archivedOnlyTrueAddsAnArchivedIsNotNullClause() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setArchivedOnly(true);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NOT NULL"));
    assertFalse(whereContains(where, "archived IS NULL"));
  }

  @Test
  void archivedOnlyFalseAddsAnArchivedIsNullClause() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setArchivedOnly(false);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedOnlyUndefinedByDefaultAddsNoArchivedClauseAtAll() {
    // The default for every pre-#882 caller (and every caller that never touches this field) --
    // proves the new filter is purely additive and does not change existing query behavior.
    CalendarEventSpecification specification = new CalendarEventSpecification();

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedFilterCombinesWithPublishedFilterAsAnd() {
    // "Archived" is orthogonal to published/draft (an archived event may have been published or
    // still a draft), so both clauses must be present together, not one replacing the other.
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setPublishedOnly(true);
    specification.setArchivedOnly(true);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "published IS NOT NULL"));
    assertTrue(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void aNullSpecificationProducesNoArchivedClause() {
    SqlUtils where = CalendarEventRepository.createWhereStatement(null);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  // --- date range (pre-existing, PR #911) and author filter (issue #426, editorial calendar) ---

  @Test
  void bothDateRangeBoundsSetAddsTheOrAcrossStartDateAndEndDateClause() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "start_date >= ? AND start_date < ?"));
    assertTrue(whereContains(where, "end_date >= ? AND end_date < ?"));
  }

  @Test
  void createdByFilterAddsACreatedByClauseWhenSet() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setCreatedBy(42L);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "created_by = ?"));
  }

  @Test
  void createdByUnsetByDefaultAddsNoCreatedByClauseAtAll() {
    CalendarEventSpecification specification = new CalendarEventSpecification();

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "created_by"));
  }

  // --- undatedOnly (issue #996, editorial calendar "Drafts with no dates" feed) ---

  @Test
  void undatedOnlyTrueAddsTheStartDateIsNullClause() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setUndatedOnly(true);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "start_date IS NULL"));
  }

  @Test
  void undatedOnlyFalseByDefaultAddsNoUndatedClauseAtAll() {
    CalendarEventSpecification specification = new CalendarEventSpecification();

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "start_date IS NULL"));
  }

  @Test
  void undatedOnlyTakesPrecedenceOverADateRangeSetOnTheSameSpecification() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setUndatedOnly(true);
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "start_date IS NULL"));
    assertFalse(whereContains(where, "start_date >= ?"));
  }

  @Test
  void undatedOnlyCombinesWithArchivedOnlyAndCreatedByAsAnd() {
    // Mirrors how EditorialCalendarAjax.addUndatedEvents() actually builds this specification.
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setUndatedOnly(true);
    specification.setArchivedOnly(false);
    specification.setCreatedBy(7L);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "start_date IS NULL"));
    assertTrue(whereContains(where, "archived IS NULL"));
    assertTrue(whereContains(where, "created_by = ?"));
  }

  // --- calendarEnabledOnly (a calendar's "Online?" checkbox gating its events off public
  // list/feed surfaces -- CalendarAjaxEvents, CalendarSearchResultsWidget, UpcomingCalendarEventsWidget) ---

  @Test
  void calendarEnabledOnlyTrueAddsTheEnabledCalendarSubqueryClause() {
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setCalendarEnabledOnly(true);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "calendar_id IN (SELECT calendar_id FROM calendars WHERE enabled = true)"));
  }

  @Test
  void calendarEnabledOnlyFalseByDefaultAddsNoEnabledCalendarClauseAtAll() {
    // The default for every existing caller (admin-side event management, editorial calendar) --
    // proves the new filter is purely additive and does not change existing query behavior.
    CalendarEventSpecification specification = new CalendarEventSpecification();

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "calendars"));
  }

  @Test
  void calendarEnabledOnlyCombinesWithPublishedOnlyAndArchivedOnlyAsAnd() {
    // Mirrors how CalendarAjaxEvents/CalendarSearchResultsWidget/UpcomingCalendarEventsWidget
    // actually build this specification for a non-previewing visitor.
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setPublishedOnly(true);
    specification.setArchivedOnly(false);
    specification.setCalendarEnabledOnly(true);

    SqlUtils where = CalendarEventRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "published IS NOT NULL"));
    assertTrue(whereContains(where, "archived IS NULL"));
    assertTrue(whereContains(where, "calendar_id IN (SELECT calendar_id FROM calendars WHERE enabled = true)"));
  }
}
