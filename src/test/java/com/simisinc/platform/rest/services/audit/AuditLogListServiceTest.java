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

package com.simisinc.platform.rest.services.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Tests the queryable audit-log REST endpoint (issue #754): admin-only, filters map onto the
 * specification the same way the admin UI's do, and pagination is applied and clamped.
 *
 * @author SimIS Inc.
 */
class AuditLogListServiceTest {

  private ServiceContext contextWithParams(Map<String, String> params) {
    ServiceContext context = new ServiceContext();
    Map<String, String[]> parameterMap = new HashMap<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      parameterMap.put(entry.getKey(), new String[] { entry.getValue() });
    }
    context.setParameterMap(parameterMap);
    return context;
  }

  private User adminUser() {
    User user = new User();
    user.setId(5L);
    user.setEmail("admin@example.com");
    List<Role> roleList = new ArrayList<>();
    roleList.add(new Role("System Administrator", "admin"));
    user.setRoleList(roleList);
    return user;
  }

  @Test
  void aNonAdminRequestIsRejectedWithoutQueryingTheRepository() {
    ServiceContext context = contextWithParams(new HashMap<>());
    User user = new User();
    user.setId(9L);
    user.setRoleList(new ArrayList<>()); // logged in, but no admin role
    context.setUser(user);

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      ServiceResponse response = new AuditLogListService().get(context);

      assertEquals(403, response.getStatus());
      repository.verify(
          () -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)), never());
    }
  }

  @Test
  void aGuestRequestIsRejected() {
    // No bearer token -> RestRequestFilter demotes to a guest User with no roles and userId == -1
    ServiceContext context = contextWithParams(new HashMap<>());
    context.setUser(new User());

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class)) {
      ServiceResponse response = new AuditLogListService().get(context);

      assertEquals(403, response.getStatus());
      repository.verify(
          () -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)), never());
    }
  }

  @Test
  void filterParametersMapOntoTheSpecification() {
    Map<String, String> params = new HashMap<>();
    params.put("category", "user_management");
    params.put("eventType", "user.disable");
    params.put("outcome", "failure");
    params.put("actor", "jdoe@example.com");
    params.put("targetType", "user");
    params.put("fromDate", "2026-07-01");
    params.put("toDate", "2026-07-20");
    ServiceContext context = contextWithParams(params);
    context.setUser(adminUser());

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      ServiceResponse response = new AuditLogListService().get(context);

      assertEquals(200, response.getStatus());
      ArgumentCaptor<AuditLogSpecification> specCaptor = ArgumentCaptor.forClass(AuditLogSpecification.class);
      repository.verify(() -> AuditLogRepository.findAll(specCaptor.capture(), any(DataConstraints.class)));
      AuditLogSpecification spec = specCaptor.getValue();

      assertEquals("user_management", spec.getEventCategory());
      assertEquals("user.disable", spec.getEventType());
      assertEquals("failure", spec.getOutcome());
      assertEquals("jdoe@example.com", spec.getActorUsername());
      assertEquals("user", spec.getTargetType());
      assertEquals(Timestamp.valueOf("2026-07-01 00:00:00"), spec.getOccurredAfter());
      assertEquals(Timestamp.valueOf("2026-07-21 00:00:00"), spec.getOccurredBefore());

      // Querying the audit log via the API is itself audited
      auditEvent.verify(() -> SaveAuditEventCommand.recordAdminEvent(
          eq("data_access"), eq("audit_log.api_query"), eq("success"),
          eq(5L), eq("admin@example.com"), any(), any(), eq("audit_log"), eq("filtered"), any(), anyString()));
    }
  }

  @Test
  void defaultPaginationIsAppliedWhenNoPageParamsAreGiven() {
    ServiceContext context = contextWithParams(new HashMap<>());
    context.setUser(adminUser());

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new AuditLogListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      repository.verify(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), constraintsCaptor.capture()));
      assertEquals(1, constraintsCaptor.getValue().getPageNumber());
      assertEquals(50, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void pageSizeIsClampedToTheMaximum() {
    Map<String, String> params = new HashMap<>();
    params.put("size", "5000");
    ServiceContext context = contextWithParams(params);
    context.setUser(adminUser());

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      new AuditLogListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      repository.verify(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), constraintsCaptor.capture()));
      assertEquals(200, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void responseDataOmitsTheTamperEvidenceHashFields() {
    ServiceContext context = contextWithParams(new HashMap<>());
    context.setUser(adminUser());

    AuditLog record = new AuditLog();
    record.setId(101L);
    record.setOccurred(Timestamp.valueOf("2026-07-20 14:19:22"));
    record.setEventCategory("authentication");
    record.setEventType("authentication.login.success");
    record.setOutcome("success");
    record.setActorUserId(5L);
    record.setActorUsername("admin@example.com");
    record.setPreviousHash("deadbeef");
    record.setRecordHash("cafebabe");

    try (MockedStatic<AuditLogRepository> repository = mockStatic(AuditLogRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      repository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of(record));

      ServiceResponse response = new AuditLogListService().get(context);

      @SuppressWarnings("unchecked")
      List<AuditLogEntryResponse> data = (List<AuditLogEntryResponse>) response.getData();
      assertEquals(1, data.size());
      assertEquals(101L, data.get(0).getId());
      assertEquals("authentication", data.get(0).getEventCategory());
      // AuditLogEntryResponse has no getters for previousHash/recordHash at all -- the chain-integrity
      // internals are simply not part of the response shape. getDeclaredMethods() (not getMethods())
      // so this doesn't trip over the inherited, unrelated Object.hashCode().
      assertTrue(java.util.Arrays.stream(AuditLogEntryResponse.class.getDeclaredMethods())
          .noneMatch(m -> m.getName().toLowerCase().contains("hash")));
    }
  }
}
