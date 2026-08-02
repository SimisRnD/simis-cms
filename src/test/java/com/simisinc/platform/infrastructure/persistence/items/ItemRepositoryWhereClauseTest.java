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

package com.simisinc.platform.infrastructure.persistence.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;

/**
 * Pure unit tests (no database) on {@link ItemRepository#createSearchWhereStatement}'s built
 * {@link SqlUtils}/{@link SqlValue} output -- specifically the multi-category IN-list issue #636
 * adds. Unlike {@link ItemRepositoryTest} (which needs a real PostgreSQL instance and is skipped
 * without Docker), these inspect the generated SQL text and bound parameter list directly, so they
 * always run and can prove the IN-list is genuinely parameterized: a DB-level test can't tell
 * parameterized `IN (?,?,?)` apart from unsafe string-concatenated `IN (11,22,33)` once the values
 * are already valid longs -- both return the same rows.
 *
 * @author SimIS Inc.
 */
class ItemRepositoryWhereClauseTest {

  private static SqlValue findCategoryClause(SqlUtils where) {
    for (SqlValue value : where.getValues()) {
      if (value.getFieldOrClause() != null && value.getFieldOrClause().contains("item_categories")) {
        return value;
      }
    }
    return null;
  }

  @Test
  void categoryInClauseIsParameterizedNotStringConcatenated() {
    // The candidate ids must never be baked into the SQL text itself -- one `?` placeholder per
    // selected id, with the actual values bound as real PreparedStatement parameters via SqlUtils'
    // Long[] overload (the same discipline ProductRepository already uses for its own dynamic
    // IN-list, e.g. products.product_unique_id IN (...)).
    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryIds(Arrays.asList(11L, 22L, 33L));

    SqlUtils where = ItemRepository.createSearchWhereStatement(specification);

    SqlValue categoryClause = findCategoryClause(where);
    assertNotNull(categoryClause, "expected an item_categories EXISTS clause to be present");
    String clauseText = categoryClause.getFieldOrClause();
    assertFalse(clauseText.contains("11") || clauseText.contains("22") || clauseText.contains("33"),
        "the candidate ids must never be concatenated into the SQL text itself: " + clauseText);
    assertEquals(3, StringUtils.countMatches(clauseText, "?"), "one ? placeholder per selected category id: " + clauseText);
    assertArrayEquals(new Long[] { 11L, 22L, 33L }, categoryClause.getLongValues(),
        "the ids must be bound as real PreparedStatement parameters, in order");
  }

  @Test
  void categoryInClauseHasOnePlaceholderForASingleLegacyCategoryId() {
    // A caller that only ever used the single-value setCategoryId() (every pre-#636 caller) must
    // still produce a valid, parameterized clause -- a one-element IN list, functionally identical
    // to the old `category_id = ?`.
    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(7L);

    SqlUtils where = ItemRepository.createSearchWhereStatement(specification);

    SqlValue categoryClause = findCategoryClause(where);
    assertNotNull(categoryClause);
    assertEquals(1, StringUtils.countMatches(categoryClause.getFieldOrClause(), "?"));
    assertArrayEquals(new Long[] { 7L }, categoryClause.getLongValues());
  }

  @Test
  void noCategoryClauseWhenNothingIsSelected() {
    ItemSpecification specification = new ItemSpecification();

    SqlUtils where = ItemRepository.createSearchWhereStatement(specification);

    assertNull(findCategoryClause(where), "no category filter should be added when nothing is selected");
  }
}
