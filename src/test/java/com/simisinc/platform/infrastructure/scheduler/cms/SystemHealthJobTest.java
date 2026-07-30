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

package com.simisinc.platform.infrastructure.scheduler.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.SystemHealthCheckRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Verifies {@link SystemHealthJob#execute}'s control flow: the lock guard, and that every
 * HealthCommand check result gets persisted with the right status mapping. {@link LockManager},
 * {@link HealthCommand}, and {@link SystemHealthCheckRepository} are all statically mocked, so
 * nothing here touches a database.
 *
 * @author SimIS
 * @created 7/30/2026
 */
class SystemHealthJobTest {

  @Test
  void executeSavesOneRecordPerCheckResult() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<HealthCommand> healthCommand = mockStatic(HealthCommand.class);
        MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.SYSTEM_HEALTH_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      healthCommand.when(HealthCommand::runAllChecks).thenReturn(List.of(
          new HealthCommand.CheckResult(HealthCommand.DATABASE_SERVICE, true, 12, null),
          new HealthCommand.CheckResult(HealthCommand.FILESYSTEM_SERVICE, false, 5, "not writable")));

      SystemHealthJob.execute();

      repository.verify(() -> SystemHealthCheckRepository.save(argThat(
          record -> HealthCommand.DATABASE_SERVICE.equals(record.getServiceName())
              && record.isUp() && record.getResponseTimeMs() == 12 && record.getErrorMessage() == null)));
      repository.verify(() -> SystemHealthCheckRepository.save(argThat(
          record -> HealthCommand.FILESYSTEM_SERVICE.equals(record.getServiceName())
              && !record.isUp() && "not writable".equals(record.getErrorMessage()))));
      repository.verify(() -> SystemHealthCheckRepository.save(any(SystemHealthCheck.class)), times(2));
    }
  }

  @Test
  void executeSkipsEntirelyWhenTheLockIsHeldByAnotherNode() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<HealthCommand> healthCommand = mockStatic(HealthCommand.class)) {
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.SYSTEM_HEALTH_JOB), any(Duration.class)))
          .thenReturn(null);

      SystemHealthJob.execute();

      healthCommand.verifyNoInteractions();
    }
  }
}
