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

package com.simisinc.platform.infrastructure.scheduler.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.SendAdminEmailCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Verifies the two independent sweeps in {@link CapabilityGrantExpirationJob#execute} (issue
 * #702): past-due grants are revoked and audited one event each, and grants expiring soon are
 * batched into a single admin-email digest (not one email per grant) and marked notified so the
 * next hourly run doesn't re-send. Everything reached is statically mocked, so nothing here
 * touches a database or sends real mail.
 *
 * @author elizabeth houser
 */
class CapabilityGrantExpirationJobTest {

  private static CapabilityGrant grant(long id, long userId, String capabilityCode) {
    CapabilityGrant grant = new CapabilityGrant();
    grant.setId(id);
    grant.setUserId(userId);
    grant.setCapabilityCode(capabilityCode);
    return grant;
  }

  private static User user(long id, String username) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  @Test
  void executeSkipsEntirelyWhenTheLockIsHeldByAnotherNode() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.CAPABILITY_GRANT_EXPIRATION_JOB), any(Duration.class)))
          .thenReturn(null);

      CapabilityGrantExpirationJob.execute();

      repo.verifyNoInteractions();
    }
  }

  @Test
  void executeRevokesExpiredGrantsAndRecordsAnAuditEventPerGrant() {
    CapabilityGrant expired = grant(1L, 10L, "reports:export");

    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<SendAdminEmailCommand> adminEmail = mockStatic(SendAdminEmailCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.CAPABILITY_GRANT_EXPIRATION_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      repo.when(CapabilityGrantRepository::findExpired).thenReturn(List.of(expired));
      repo.when(() -> CapabilityGrantRepository.revoke(1L)).thenReturn(true);
      repo.when(() -> CapabilityGrantRepository.findExpiringWithinDaysNotYetNotified(anyInt())).thenReturn(null);
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(user(10L, "jsmith"));

      CapabilityGrantExpirationJob.execute();

      repo.verify(() -> CapabilityGrantRepository.revoke(1L));
      auditEvent.verify(() -> SaveAuditEventCommand.recordAdminEvent(anyString(), eq("capability_grant.expire"),
          anyString(), eq(-1L), eq("system"), isNull(), isNull(), eq("capability_grant"),
          eq("reports:export"), eq("jsmith"), contains("expired")), times(1));
      adminEmail.verifyNoInteractions();
    }
  }

  @Test
  void executeDoesNothingWhenNoGrantsAreExpiredOrExpiring() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<SendAdminEmailCommand> adminEmail = mockStatic(SendAdminEmailCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.CAPABILITY_GRANT_EXPIRATION_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      repo.when(CapabilityGrantRepository::findExpired).thenReturn(null);
      repo.when(() -> CapabilityGrantRepository.findExpiringWithinDaysNotYetNotified(anyInt())).thenReturn(null);

      CapabilityGrantExpirationJob.execute();

      auditEvent.verifyNoInteractions();
      adminEmail.verifyNoInteractions();
    }
  }

  @Test
  void executeSendsOneBatchedDigestForAllExpiringGrantsAndMarksEachNotified() {
    CapabilityGrant expiringA = grant(2L, 11L, "reports:export");
    CapabilityGrant expiringB = grant(3L, 12L, "billing:manage");

    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<CapabilityGrantRepository> repo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SendAdminEmailCommand> adminEmail = mockStatic(SendAdminEmailCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.CAPABILITY_GRANT_EXPIRATION_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      repo.when(CapabilityGrantRepository::findExpired).thenReturn(null);
      repo.when(() -> CapabilityGrantRepository.findExpiringWithinDaysNotYetNotified(7))
          .thenReturn(List.of(expiringA, expiringB));
      userRepo.when(() -> UserRepository.findByUserId(11L)).thenReturn(user(11L, "asmith"));
      userRepo.when(() -> UserRepository.findByUserId(12L)).thenReturn(user(12L, "bsmith"));

      CapabilityGrantExpirationJob.execute();

      adminEmail.verify(() -> SendAdminEmailCommand.sendMessage(anyString(), anyString(), anyString()), times(1));
      repo.verify(() -> CapabilityGrantRepository.markExpirationNotified(2L));
      repo.verify(() -> CapabilityGrantRepository.markExpirationNotified(3L));
    }
  }
}
