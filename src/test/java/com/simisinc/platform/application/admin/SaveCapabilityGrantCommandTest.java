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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * The only runtime path that mutates capability_grants (issue #702) - covers the required-reason
 * validation, the friendlier duplicate-active-grant refusal (vs. letting the DB's unique index
 * surface a raw constraint violation), and that both grant/revoke produce an audit event.
 *
 * @author elizabeth houser
 */
class SaveCapabilityGrantCommandTest {

  private static User user(String username, long id) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  private static CapabilityGrant grant(long id, long userId, long capabilityId, String capabilityCode) {
    CapabilityGrant grant = new CapabilityGrant();
    grant.setId(id);
    grant.setUserId(userId);
    grant.setCapabilityId(capabilityId);
    grant.setCapabilityCode(capabilityCode);
    return grant;
  }

  @Test
  void grantRequiresAReason() {
    User targetUser = user("jsmith", 10L);
    Capability capability = capability("reports:export", 3L);

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class)) {
      DataException e = assertThrows(DataException.class,
          () -> SaveCapabilityGrantCommand.grant(null, targetUser, capability, 1L, "  ", null));
      assertEquals("A reason is required when granting a capability", e.getMessage());
      repo.verifyNoInteractions();
    }
  }

  @Test
  void grantRefusesADuplicateActiveGrant() {
    User targetUser = user("jsmith", 10L);
    Capability capability = capability("reports:export", 3L);
    CapabilityGrant existing = grant(1L, 10L, 3L, "reports:export");

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class)) {
      repo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(List.of(existing));

      DataException e = assertThrows(DataException.class,
          () -> SaveCapabilityGrantCommand.grant(null, targetUser, capability, 1L, "Contractor access", null));
      assertEquals(true, e.getMessage().contains("already has an active grant"));
      repo.verify(() -> CapabilityGrantRepository.add(any()), never());
    }
  }

  @Test
  void grantSavesAndRecordsAnAuditEvent() throws Exception {
    User targetUser = user("jsmith", 10L);
    Capability capability = capability("reports:export", 3L);
    Timestamp expiresAt = Timestamp.valueOf("2026-08-01 00:00:00");

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(null);
      repo.when(() -> CapabilityGrantRepository.add(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      CapabilityGrant saved = SaveCapabilityGrantCommand.grant(null, targetUser, capability, 1L,
          "Temporary contractor access", expiresAt);

      assertEquals(10L, saved.getUserId());
      assertEquals(3L, saved.getCapabilityId());
      assertEquals(1L, saved.getGrantedBy());
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.AUTHORIZATION),
          eq("capability_grant.grant"), eq(AuditEventCommand.SUCCESS), eq("capability_grant"),
          eq("reports:export"), eq("jsmith"), eq("Temporary contractor access")));
    }
  }

  @Test
  void revokeRequiresAReason() {
    User targetUser = user("jsmith", 10L);
    CapabilityGrant existing = grant(1L, 10L, 3L, "reports:export");

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class)) {
      DataException e = assertThrows(DataException.class,
          () -> SaveCapabilityGrantCommand.revoke(null, existing, targetUser, " "));
      assertEquals("A reason is required when revoking a capability grant", e.getMessage());
      repo.verifyNoInteractions();
    }
  }

  @Test
  void revokeUpdatesTheRecordAndRecordsAnAuditEvent() throws Exception {
    User targetUser = user("jsmith", 10L);
    CapabilityGrant existing = grant(1L, 10L, 3L, "reports:export");

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repo.when(() -> CapabilityGrantRepository.revoke(1L)).thenReturn(true);

      SaveCapabilityGrantCommand.revoke(null, existing, targetUser, "No longer needed");

      repo.verify(() -> CapabilityGrantRepository.revoke(1L));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.AUTHORIZATION),
          eq("capability_grant.revoke"), eq(AuditEventCommand.SUCCESS), eq("capability_grant"),
          eq("reports:export"), eq("jsmith"), eq("No longer needed")));
    }
  }

  @Test
  void revokeThrowsWhenTheUpdateFails() {
    User targetUser = user("jsmith", 10L);
    CapabilityGrant existing = grant(1L, 10L, 3L, "reports:export");

    try (MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class)) {
      repo.when(() -> CapabilityGrantRepository.revoke(anyLong())).thenReturn(false);

      assertThrows(DataException.class,
          () -> SaveCapabilityGrantCommand.revoke(null, existing, targetUser, "No longer needed"));
    }
  }
}
