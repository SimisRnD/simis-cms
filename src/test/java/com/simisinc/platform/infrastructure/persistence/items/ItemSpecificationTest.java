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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ItemSpecification#getEffectiveCategoryIds()} (issue #636), the one method
 * ItemRepository's WHERE-clause builder consults so the multi-select categoryIds list and the
 * legacy single-value categoryId field never drift apart.
 *
 * @author SimIS Inc.
 */
class ItemSpecificationTest {

  @Test
  void effectiveCategoryIdsIsEmptyWhenNeitherIsSet() {
    ItemSpecification specification = new ItemSpecification();
    assertTrue(specification.getEffectiveCategoryIds().isEmpty());
  }

  @Test
  void effectiveCategoryIdsFallsBackToTheLegacySingleValueField() {
    // Every caller that existed before #636 only ever called setCategoryId() -- this is what keeps
    // them working unchanged.
    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(42L);

    assertEquals(Collections.singletonList(42L), specification.getEffectiveCategoryIds());
  }

  @Test
  void effectiveCategoryIdsPrefersTheMultiSelectListWhenItIsSet() {
    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(42L); // e.g. a stale/unrelated legacy value
    specification.setCategoryIds(Arrays.asList(1L, 2L, 3L));

    assertEquals(Arrays.asList(1L, 2L, 3L), specification.getEffectiveCategoryIds());
  }

  @Test
  void effectiveCategoryIdsFallsBackToLegacyWhenTheMultiSelectListIsSetButEmpty() {
    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(42L);
    specification.setCategoryIds(Collections.emptyList());

    assertEquals(Collections.singletonList(42L), specification.getEffectiveCategoryIds());
  }
}
