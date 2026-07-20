/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests parsing of the configurable analytics retention window
 *
 * @author elizabeth houser
 */
class WebPageHitRepositoryTest {

  @Test
  void resolveRetentionDaysParsesAndBounds() {
    assertEquals(30, WebPageHitRepository.resolveRetentionDays("30"));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("365"));
    assertEquals(90, WebPageHitRepository.resolveRetentionDays("  90  "));
    // Defaults when blank or non-numeric (the value comes from a site property, so it must not inject SQL)
    assertEquals(365, WebPageHitRepository.resolveRetentionDays(""));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays(null));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("30; DROP TABLE web_page_hits"));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("abc"));
    // Bounded to a sane range
    assertEquals(1, WebPageHitRepository.resolveRetentionDays("0"));
    assertEquals(1, WebPageHitRepository.resolveRetentionDays("-5"));
    assertEquals(3650, WebPageHitRepository.resolveRetentionDays("999999"));
  }
}
