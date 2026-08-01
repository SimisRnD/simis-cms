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

package com.simisinc.platform.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;

/**
 * Tests the shared audit-log filter-parsing logic used by the REST API (AuditLogListService).
 *
 * @author SimIS Inc.
 */
class BuildAuditLogSpecificationCommandTest {

  @Test
  void blankParametersLeaveEveryFieldUnset() {
    AuditLogSpecification spec = BuildAuditLogSpecificationCommand.build(
        null, "", "  ", null, null, null, null, null, null, null);

    assertNull(spec.getEventCategory());
    assertNull(spec.getEventType());
    assertNull(spec.getOutcome());
    assertNull(spec.getActorUsername());
    assertNull(spec.getSourceIp());
    assertNull(spec.getTargetType());
    assertNull(spec.getTargetLabel());
    assertNull(spec.getOccurredAfter());
    assertNull(spec.getOccurredBefore());
  }

  @Test
  void everyFilterMapsOntoTheSpecification() {
    AuditLogSpecification spec = BuildAuditLogSpecificationCommand.build(
        "user_management", "user.disable", "failure", "Admin@Example.com", "203.0.113.4",
        "user", "203.0.113.5", null, "2026-07-01", "2026-07-20");

    assertEquals("user_management", spec.getEventCategory());
    assertEquals("user.disable", spec.getEventType());
    assertEquals("failure", spec.getOutcome());
    assertEquals("Admin@Example.com", spec.getActorUsername());
    assertEquals("203.0.113.4", spec.getSourceIp());
    assertEquals("user", spec.getTargetType());
    assertEquals("203.0.113.5", spec.getTargetLabel());
    assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-01").atStartOfDay()), spec.getOccurredAfter());
    // The "to" bound is half-open: the start of the day AFTER the picked date, so that whole day is included
    assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-21").atStartOfDay()), spec.getOccurredBefore());
  }

  @Test
  void aQuickRangePresetTakesPrecedenceOverAnExplicitDateRange() {
    Instant before = Instant.now().minus(24, ChronoUnit.HOURS);
    AuditLogSpecification spec = BuildAuditLogSpecificationCommand.build(
        null, null, null, null, null, null, null, "24h", "2020-01-01", "2020-01-02");
    Instant after = Instant.now().minus(24, ChronoUnit.HOURS);

    assertTrue(!spec.getOccurredAfter().toInstant().isBefore(before) && !spec.getOccurredAfter().toInstant().isAfter(after),
        "expected a cutoff around 24h ago, got " + spec.getOccurredAfter());
    assertNull(spec.getOccurredBefore(), "a range preset has no upper bound, unlike the explicit date-range fields");
  }

  @Test
  void resolveRangeCutoffRecognizesEveryPreset() {
    Instant now = Instant.now();
    assertTrue(BuildAuditLogSpecificationCommand.resolveRangeCutoff("1h").toInstant().isBefore(now));
    assertTrue(BuildAuditLogSpecificationCommand.resolveRangeCutoff("24h").toInstant().isBefore(now.minus(1, ChronoUnit.HOURS)));
    assertTrue(BuildAuditLogSpecificationCommand.resolveRangeCutoff("7d").toInstant().isBefore(now.minus(6, ChronoUnit.DAYS)));
    assertTrue(BuildAuditLogSpecificationCommand.resolveRangeCutoff("30d").toInstant().isBefore(now.minus(29, ChronoUnit.DAYS)));
  }

  @Test
  void resolveRangeCutoffReturnsNullForBlankOrUnrecognizedValues() {
    assertNull(BuildAuditLogSpecificationCommand.resolveRangeCutoff(null));
    assertNull(BuildAuditLogSpecificationCommand.resolveRangeCutoff(""));
    assertNull(BuildAuditLogSpecificationCommand.resolveRangeCutoff("3 weeks ago"));
  }

  @Test
  void malformedDatesAreIgnoredRatherThanThrowing() {
    AuditLogSpecification spec = BuildAuditLogSpecificationCommand.build(
        null, null, null, null, null, null, null, null, "not-a-date", "also-not-a-date");
    assertNull(spec.getOccurredAfter());
    assertNull(spec.getOccurredBefore());
  }
}
