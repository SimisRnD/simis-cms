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
 * Pure unit tests (no database) on {@link BlogPostRepository#createWhereStatement}'s built
 * {@link SqlUtils} output for the archived filter issue #427 adds -- mirrors the shape of
 * {@code CalendarEventRepositoryWhereClauseTest}.
 *
 * @author SimIS Inc.
 */
class BlogPostRepositoryWhereClauseTest {

  private static boolean whereContains(SqlUtils where, String fragment) {
    // Unlike CalendarEventRepository#createWhereStatement (which always allocates a SqlUtils),
    // BlogPostRepository#createWhereStatement returns null outright for a null specification --
    // pre-existing behavior, unrelated to the archived filter this test targets. Mirrors
    // WebPageRepositoryWhereClauseTest's identical guard.
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
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setArchivedOnly(true);

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NOT NULL"));
    assertFalse(whereContains(where, "archived IS NULL"));
  }

  @Test
  void archivedOnlyFalseAddsAnArchivedIsNullClause() {
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setArchivedOnly(false);

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedOnlyUndefinedByDefaultAddsNoArchivedClauseAtAll() {
    // The default for every pre-#427 caller (and every caller that never touches this field) --
    // proves the new filter is purely additive and does not change existing query behavior.
    BlogPostSpecification specification = new BlogPostSpecification();

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void archivedFilterCombinesWithPublishedFilterAsAnd() {
    // "Archived" is orthogonal to published/draft (an archived post may have been published or
    // still a draft), so both clauses must be present together, not one replacing the other.
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setPublishedOnly(true);
    specification.setArchivedOnly(true);

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "published IS NOT NULL"));
    assertTrue(whereContains(where, "archived IS NOT NULL"));
  }

  @Test
  void aNullSpecificationProducesNoArchivedClause() {
    SqlUtils where = BlogPostRepository.createWhereStatement(null);

    assertFalse(whereContains(where, "archived IS NULL"));
    assertFalse(whereContains(where, "archived IS NOT NULL"));
  }

  // --- date-range and author filters (issue #426, editorial calendar) ---

  @Test
  void bothDateRangeBoundsSetAddsTheOrAcrossStartDateAndEndDateClause() {
    // BlogPost has no publishAt/expiresAt columns -- start_date/end_date are this entity's real
    // scheduling-equivalent fields (see BlogPostSpecification's field comment).
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "start_date >= ? AND start_date < ?"));
    assertTrue(whereContains(where, "end_date >= ? AND end_date < ?"));
  }

  @Test
  void noDateRangeSetAddsNoDateRangeClause() {
    BlogPostSpecification specification = new BlogPostSpecification();

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "start_date"));
    assertFalse(whereContains(where, "end_date"));
  }

  @Test
  void createdByFilterAddsACreatedByClauseWhenSet() {
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setCreatedBy(42L);

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertTrue(whereContains(where, "created_by = ?"));
  }

  @Test
  void createdByUnsetByDefaultAddsNoCreatedByClauseAtAll() {
    BlogPostSpecification specification = new BlogPostSpecification();

    SqlUtils where = BlogPostRepository.createWhereStatement(specification);

    assertFalse(whereContains(where, "created_by"));
  }
}
