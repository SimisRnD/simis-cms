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

package com.simisinc.platform.infrastructure.scheduler.mailinglists;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Verifies {@link MailingListQuarantineJob#execute}'s control flow: the lock guard, and -- the
 * main point of this test -- that a quarantine action is recorded as an audit event only when it
 * actually quarantined something (issue #564's "cleanup activity in the audit log" requirement),
 * not on every run. {@link LockManager}, {@link MailingListMemberRepository}, and {@link
 * SaveAuditEventCommand} are all statically mocked, so nothing here touches a database.
 *
 * @author SimIS Inc.
 */
class MailingListQuarantineJobTest {

  @Test
  void executeRecordsAnAuditEventWhenMembershipsAreQuarantined() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.MAILING_LIST_QUARANTINE_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      repository.when(MailingListMemberRepository::quarantineFlaggedMembers).thenReturn(3);

      MailingListQuarantineJob.execute();

      auditEvent.verify(() -> SaveAuditEventCommand.recordAdminEvent(anyString(), anyString(), anyString(),
          anyLong(), anyString(), isNull(), isNull(), anyString(), isNull(), isNull(), contains("3")), times(1));
    }
  }

  @Test
  void executeDoesNotRecordAnAuditEventWhenNothingWasQuarantined() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<SaveAuditEventCommand> auditEvent = mockStatic(SaveAuditEventCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.MAILING_LIST_QUARANTINE_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      repository.when(MailingListMemberRepository::quarantineFlaggedMembers).thenReturn(0);

      MailingListQuarantineJob.execute();

      auditEvent.verifyNoInteractions();
    }
  }

  @Test
  void executeSkipsEntirelyWhenTheLockIsHeldByAnotherNode() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.MAILING_LIST_QUARANTINE_JOB), any(Duration.class)))
          .thenReturn(null);

      MailingListQuarantineJob.execute();

      repository.verifyNoInteractions();
    }
  }
}
