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
}
