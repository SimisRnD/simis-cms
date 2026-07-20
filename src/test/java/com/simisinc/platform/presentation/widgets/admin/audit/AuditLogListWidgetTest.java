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

package com.simisinc.platform.presentation.widgets.admin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;

/**
 * Tests the audit review widget: filters map onto the query specification, and the log is admin-only.
 *
 * @author SimIS Inc.
 */
class AuditLogListWidgetTest extends WidgetBase {

  @Test
  void filterParametersMapOntoTheSpecification() {
    setRoles(widgetContext, "admin");
    addQueryParameter(widgetContext, "category", "user_management");
    addQueryParameter(widgetContext, "eventType", "user.disable");
    addQueryParameter(widgetContext, "outcome", "failure");
    addQueryParameter(widgetContext, "actor", "Admin@Example.com");
    addQueryParameter(widgetContext, "fromDate", "2026-07-01");
    addQueryParameter(widgetContext, "toDate", "2026-07-20");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new AuditLogListWidget().execute(widgetContext);

      ArgumentCaptor<AuditLogSpecification> captor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(captor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = captor.getValue();

      assertEquals("user_management", spec.getEventCategory());
      assertEquals("user.disable", spec.getEventType());
      assertEquals("failure", spec.getOutcome());
      assertEquals("Admin@Example.com", spec.getActorUsername());
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-01").atStartOfDay()), spec.getOccurredAfter());
      // The "to" bound is half-open: the start of the day AFTER the picked date, so that whole day is included
      assertEquals(Timestamp.valueOf(LocalDate.parse("2026-07-21").atStartOfDay()), spec.getOccurredBefore());

      // Pagination must carry the filters forward (URL-encoded) so page 2+ stays filtered
      String pagingParams = (String) widgetContext.getRequest().getAttribute("recordPagingParams");
      assertTrue(pagingParams.contains("category=user_management"));
      assertTrue(pagingParams.contains("eventType=user.disable"));
      assertTrue(pagingParams.contains("outcome=failure"));
      assertTrue(pagingParams.contains("actor=Admin%40Example.com")); // '@' is URL-encoded
      assertTrue(pagingParams.contains("fromDate=2026-07-01"));
      assertTrue(pagingParams.contains("toDate=2026-07-20"));
    }
  }

  @Test
  void aNonAdminIsNotShownTheAuditLog() {
    setRoles(widgetContext); // logged in, but no admin role

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      new AuditLogListWidget().execute(widgetContext);

      // The widget must return before querying or exposing any records
      repository.verify(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)),
          never());
      assertNull(widgetContext.getRequest().getAttribute("auditLogList"));
    }
  }
}
