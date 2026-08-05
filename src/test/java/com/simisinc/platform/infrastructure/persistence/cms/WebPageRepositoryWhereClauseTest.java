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
 * Pure unit tests (no database) on {@link WebPageRepository#createWhereStatement}'s built
 * {@link SqlUtils} output for the archived filter issue #427 adds -- mirrors the shape of
 * {@code CalendarEventRepositoryWhereClauseTest}.
 *
 * @author SimIS Inc.
 */
class WebPageRepositoryWhereClauseTest {

  private static boolean whereContains(SqlUtils where, String fragment) {
    // Unlike CalendarEventRepository#createWhereStatement (which always allocates a SqlUtils),
    // WebPageRepository#createWhereStatement returns null outright for a null specification --
    // pre-existing behavior, unrelated to the archived filter this test targets.
    if (where == null) {
      return false;
    }
    for (SqlValue value : where.getValues()) {
      if (value.getFieldOrClause() != null && value.getFieldOrClause().contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void archivedOnlyTrueAddsAnArchivedIsNotNullClause() {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setArchivedOnly(true);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NOT NULL"));
    assertFalse(whereContains(where, "archived IS NULL"));
  }

  @Test
  void archivedOnlyFalseAddsAnArchivedIsNullClause() {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setArchivedOnly(false);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedOnlyUndefinedByDefaultAddsNoArchivedClauseAtAll() {
    // The default for every pre-#427 caller (and every caller that never touches this field) --
    // proves the new filter is purely additive and does not change existing query behavior.
    WebPageSpecification specification = new WebPageSpecification();

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedFilterCombinesWithDraftFilterAsAnd() {
    // "Archived" is orthogonal to draft/live (an archived page may have been live or still a
    // draft), so both clauses must be present together, not one replacing the other.
    WebPageSpecification specification = new WebPageSpecification();
    specification.setDraft(true);
    specification.setArchivedOnly(true);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "draft = ?"));
    assertTrue(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void aNullSpecificationProducesNoArchivedClause() {
    SqlUtils where = WebPageRepository.createWhereStatement(null);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  // --- date-range and author filters (issue #426, editorial calendar) ---

  @Test
  void bothDateRangeBoundsSetAddsTheOrAcrossPublishAtAndExpiresAtClause() {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "publish_at >= ? AND publish_at < ?"));
    assertTrue(whereContains(where, "expires_at >= ? AND expires_at < ?"));
  }

  @Test
  void noDateRangeSetAddsNoDateRangeClause() {
    WebPageSpecification specification = new WebPageSpecification();

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "publish_at"));
    assertFalse(whereContains(where, "expires_at"));
  }

  @Test
  void createdByFilterAddsACreatedByClauseWhenSet() {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setCreatedBy(42L);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "created_by = ?"));
  }

  @Test
  void createdByUnsetByDefaultAddsNoCreatedByClauseAtAll() {
    // -1 is every *Specification's unset sentinel for a long field -- proves the new author filter
    // is purely additive, matching every pre-#426 caller that never touches this field.
    WebPageSpecification specification = new WebPageSpecification();

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "created_by"));
  }

  // --- undatedOnly (issue #996, editorial calendar "Drafts with no dates" feed) ---

  @Test
  void undatedOnlyTrueAddsThePublishAtAndExpiresAtIsNullClause() {
    WebPageSpecification specification = new WebPageSpecification();
    specification.setUndatedOnly(true);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "publish_at IS NULL AND expires_at IS NULL"));
  }

  @Test
  void undatedOnlyFalseByDefaultAddsNoUndatedClauseAtAll() {
    // false is the default -- proves the new filter is purely additive, matching every pre-#996
    // caller that never touches this field.
    WebPageSpecification specification = new WebPageSpecification();

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "publish_at IS NULL"));
    assertFalse(whereContains(where, "expires_at IS NULL"));
  }

  @Test
  void undatedOnlyTakesPrecedenceOverADateRangeSetOnTheSameSpecification() {
    // EditorialCalendarAjax never sets both on the same specification, but the WHERE-building
    // itself should still resolve unambiguously if it ever happened: the undated-only clause
    // entirely replaces the date-range clause rather than combining with it.
    WebPageSpecification specification = new WebPageSpecification();
    specification.setUndatedOnly(true);
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "publish_at IS NULL AND expires_at IS NULL"));
    assertFalse(whereContains(where, "publish_at >= ?"));
  }

  @Test
  void undatedOnlyCombinesWithArchivedOnlyAndCreatedByAsAnd() {
    // Mirrors how EditorialCalendarAjax.addUndatedPages() actually builds this specification --
    // all three clauses must be present together.
    WebPageSpecification specification = new WebPageSpecification();
    specification.setUndatedOnly(true);
    specification.setArchivedOnly(false);
    specification.setCreatedBy(7L);

    SqlUtils where = WebPageRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "publish_at IS NULL AND expires_at IS NULL"));
    assertTrue(whereContains(where, "archived IS NULL"));
    assertTrue(whereContains(where, "created_by = ?"));
  }
}
