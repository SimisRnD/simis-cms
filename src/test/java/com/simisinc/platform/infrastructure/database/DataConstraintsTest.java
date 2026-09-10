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

package com.simisinc.platform.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The sort fields, and which writer wins.
 *
 * <p>
 * A repository sets its fallback ordering <em>after</em> it receives a caller's constraints. While
 * that write was unconditional, a caller who set the same field had their ordering discarded --
 * issue 1604, six callers, none of whom got the sort they asked for.
 * </p>
 *
 * @author elizabeth houser
 */
class DataConstraintsTest {

  @Test
  void theRepositoryDefaultAppliesWhenNothingElseAskedForOne() {
    // The ordinary case, and the one the name always described.
    DataConstraints constraints = new DataConstraints();
    constraints.setDefaultColumnToSortBy("post_id");
    assertEquals("post_id", constraints.getDefaultColumnToSortBy());
  }

  @Test
  void aCallerSetDefaultSurvivesTheRepositorySettingItsOwn() {
    // Exactly the sequence that produced issue 1604: the caller sets an order, hands the
    // constraints to a repository, and findAll sets its own default one line later.
    DataConstraints constraints = new DataConstraints();
    constraints.setDefaultColumnToSortBy("published DESC");

    constraints.setDefaultColumnToSortBy("post_id");

    assertEquals("published DESC", constraints.getDefaultColumnToSortBy(),
        "the repository's fallback must not displace an ordering that was already set");
  }

  @Test
  void theApplicationFacingSetterStillOutranksTheDefaultEntirely() {
    // Unchanged behaviour, asserted because it is what callers should be using: columnsToSortBy is
    // a different field, and DB#appendSortClause reads it before the default.
    DataConstraints constraints = new DataConstraints();
    constraints.setColumnsToSortBy(new String[] { "start_date DESC NULLS LAST", "post_id DESC" });

    constraints.setDefaultColumnToSortBy("post_id");

    assertArrayEquals(new String[] { "start_date DESC NULLS LAST", "post_id DESC" },
        constraints.getColumnsToSortBy());
    assertEquals("post_id", constraints.getDefaultColumnToSortBy(),
        "the default is still recorded; it is simply read second");
  }

  @Test
  void theDefaultStartsUnsetSoTheFirstWriterIsWhoeverAsksFirst() {
    assertNull(new DataConstraints().getDefaultColumnToSortBy());
  }

  @Test
  void settingTheDefaultStillChains() {
    // 165 repository call sites use the returned reference; a few chain off it.
    DataConstraints constraints = new DataConstraints();
    assertSame(constraints, constraints.setDefaultColumnToSortBy("post_id"));
  }

  @Test
  void theConstructorSortIsAnApplicationSortNotADefault() {
    // new DataConstraints(page, size, column) routes through setColumnToSortBy, so a repository's
    // later default cannot touch it. Asserted so the two paths are not conflated again.
    DataConstraints constraints = new DataConstraints(1, 20, "created", "desc");
    constraints.setDefaultColumnToSortBy("post_id");

    assertArrayEquals(new String[] { "created" }, constraints.getColumnsToSortBy());
    assertArrayEquals(new String[] { "desc" }, constraints.getSortOrder());
  }
}
