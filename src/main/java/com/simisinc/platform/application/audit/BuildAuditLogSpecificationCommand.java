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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;

/**
 * Builds an {@link AuditLogSpecification} from raw, untyped filter values (query/form parameters). Shared
 * filter-parsing logic for anywhere the audit log can be queried -- currently the REST API
 * ({@code AuditLogListService}); the in-app review UI ({@code AuditLogListWidget}) predates this command
 * and has its own equivalent, unmigrated copy.
 *
 * <p>Every parameter is optional; a blank value leaves the corresponding specification field unset. A
 * quick range preset (1h/24h/7d/30d) takes precedence over an explicit fromDate/toDate range -- it is finer
 * grained (hour precision) than the date-only fields can express.
 *
 * @author SimIS Inc.
 */
public class BuildAuditLogSpecificationCommand {

  private BuildAuditLogSpecificationCommand() {
    // Static utility
  }

  public static AuditLogSpecification build(String eventCategory, String eventType, String outcome,
      String actor, String sourceIp, String targetType, String targetLabel,
      String range, String fromDate, String toDate) {

    AuditLogSpecification specification = new AuditLogSpecification();
    if (StringUtils.isNotBlank(eventCategory)) {
      specification.setEventCategory(eventCategory.trim());
    }
    if (StringUtils.isNotBlank(eventType)) {
      specification.setEventType(eventType.trim());
    }
    if (StringUtils.isNotBlank(outcome)) {
      specification.setOutcome(outcome.trim());
    }
    if (StringUtils.isNotBlank(actor)) {
      specification.setActorUsername(actor.trim());
    }
    if (StringUtils.isNotBlank(sourceIp)) {
      specification.setSourceIp(sourceIp.trim());
    }
    if (StringUtils.isNotBlank(targetType)) {
      specification.setTargetType(targetType.trim());
    }
    if (StringUtils.isNotBlank(targetLabel)) {
      specification.setTargetLabel(targetLabel.trim());
    }

    Timestamp rangeCutoff = resolveRangeCutoff(range);
    if (rangeCutoff != null) {
      specification.setOccurredAfter(rangeCutoff);
    } else {
      // Parse the yyyy-MM-dd date range: from = start of that day, to = start of the day AFTER (half-open)
      Timestamp from = parseDate(fromDate, 0);
      Timestamp to = parseDate(toDate, 1);
      if (from != null) {
        specification.setOccurredAfter(from);
      }
      if (to != null) {
        specification.setOccurredBefore(to);
      }
    }
    return specification;
  }

  /** Parses a yyyy-MM-dd string to a start-of-day Timestamp plus {@code plusDays}; null when blank/invalid. */
  static Timestamp parseDate(String value, int plusDays) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(value.trim()).plusDays(plusDays);
      return Timestamp.valueOf(date.atStartOfDay());
    } catch (Exception e) {
      return null;
    }
  }

  /** Resolves a quick range preset to an "occurred after" cutoff; null when absent or unrecognized. */
  static Timestamp resolveRangeCutoff(String range) {
    if (StringUtils.isBlank(range)) {
      return null;
    }
    Instant now = Instant.now();
    switch (range.trim()) {
      case "1h":
        return Timestamp.from(now.minus(1, ChronoUnit.HOURS));
      case "24h":
        return Timestamp.from(now.minus(24, ChronoUnit.HOURS));
      case "7d":
        return Timestamp.from(now.minus(7, ChronoUnit.DAYS));
      case "30d":
        return Timestamp.from(now.minus(30, ChronoUnit.DAYS));
      default:
        return null;
    }
  }
}
