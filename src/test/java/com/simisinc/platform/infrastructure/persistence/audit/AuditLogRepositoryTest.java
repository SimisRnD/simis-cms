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

package com.simisinc.platform.infrastructure.persistence.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests parsing of the configurable audit retention window. The floor is high (90 days) so a misconfigured
 * or hostile value cannot turn the retention job into a tool for erasing recent evidence.
 *
 * @author SimIS Inc.
 */
class AuditLogRepositoryTest {

  @Test
  void resolveRetentionDaysParsesAndBounds() {
    assertEquals(2555, AuditLogRepository.resolveRetentionDays("2555"));
    assertEquals(365, AuditLogRepository.resolveRetentionDays("365"));
    assertEquals(90, AuditLogRepository.resolveRetentionDays("  90  "));
    // Defaults when blank or non-numeric (the value comes from a site property, so it must not inject SQL)
    assertEquals(2555, AuditLogRepository.resolveRetentionDays(""));
    assertEquals(2555, AuditLogRepository.resolveRetentionDays(null));
    assertEquals(2555, AuditLogRepository.resolveRetentionDays("90; DROP TABLE audit_log"));
    assertEquals(2555, AuditLogRepository.resolveRetentionDays("abc"));
    // Bounded: floored at 90 so retention cannot be set low enough to erase recent evidence
    assertEquals(90, AuditLogRepository.resolveRetentionDays("30"));
    assertEquals(90, AuditLogRepository.resolveRetentionDays("0"));
    assertEquals(90, AuditLogRepository.resolveRetentionDays("-5"));
    // Capped at ~10 years to avoid an unbounded interval
    assertEquals(3650, AuditLogRepository.resolveRetentionDays("999999"));
  }
}
