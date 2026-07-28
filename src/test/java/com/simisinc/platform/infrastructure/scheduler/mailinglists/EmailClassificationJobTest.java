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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.mailinglists.ZeroBounceApiClientCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Verifies {@link EmailClassificationJob#execute}'s control flow: the configuration/lock/backlog
 * guard clauses, and -- the main point of this test -- that a single address failing
 * classification (whether {@link ZeroBounceApiClientCommand#validateEmail} returns null or
 * throws) does not stop the rest of the batch from being processed.
 * <p>
 * {@link LoadSitePropertyCommand}, {@link LockManager}, {@link EmailRepository}, and {@link
 * ZeroBounceApiClientCommand} are all statically mocked, so nothing here touches a database or
 * the network -- this is purely the job's own orchestration logic.
 *
 * @author SimIS Inc.
 */
class EmailClassificationJobTest {

  private static final String API_KEY_PROPERTY = "mailing-list.zerobounce.apiKey";

  @Test
  void executeProcessesTheWholeBatchDespiteAPerEmailFailure() {
    Email succeeds1 = email(1L, "succeeds1@example.com");
    Email failsReturnsNull = email(2L, "fails-null@example.com");
    Email failsThrows = email(3L, "fails-throws@example.com");
    Email succeeds2 = email(4L, "succeeds2@example.com");
    List<Email> batch = Arrays.asList(succeeds1, failsReturnsNull, failsThrows, succeeds2);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class);
        MockedStatic<ZeroBounceApiClientCommand> zeroBounce = mockStatic(ZeroBounceApiClientCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(API_KEY_PROPERTY)).thenReturn("configured-key");
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.EMAIL_CLASSIFICATION_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      emailRepository.when(() -> EmailRepository.findUnvalidatedEmails(any(DataConstraints.class))).thenReturn(batch);

      JsonNode ok = JsonNodeFactory.instance.objectNode().put("status", "valid");
      zeroBounce.when(() -> ZeroBounceApiClientCommand.validateEmail(succeeds1)).thenReturn(ok);
      zeroBounce.when(() -> ZeroBounceApiClientCommand.validateEmail(failsReturnsNull)).thenReturn(null);
      zeroBounce.when(() -> ZeroBounceApiClientCommand.validateEmail(failsThrows))
          .thenThrow(new RuntimeException("simulated ZeroBounce failure"));
      zeroBounce.when(() -> ZeroBounceApiClientCommand.validateEmail(succeeds2)).thenReturn(ok);

      EmailClassificationJob.execute();

      // Every address in the batch was attempted, in particular succeeds2 - which comes after
      // both failure modes - proving neither a null return nor a thrown exception aborts the loop.
      zeroBounce.verify(() -> ZeroBounceApiClientCommand.validateEmail(succeeds1), times(1));
      zeroBounce.verify(() -> ZeroBounceApiClientCommand.validateEmail(failsReturnsNull), times(1));
      zeroBounce.verify(() -> ZeroBounceApiClientCommand.validateEmail(failsThrows), times(1));
      zeroBounce.verify(() -> ZeroBounceApiClientCommand.validateEmail(succeeds2), times(1));

      // The batch was pulled as a single bounded page (BATCH_SIZE = 200), oldest first
      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      emailRepository.verify(() -> EmailRepository.findUnvalidatedEmails(constraintsCaptor.capture()));
      assertEquals(1, constraintsCaptor.getValue().getPageNumber());
      assertEquals(200, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void executeSkipsCleanlyWhenApiKeyIsNotConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(API_KEY_PROPERTY)).thenReturn("");

      EmailClassificationJob.execute();

      // An unconfigured install must not touch the lock or the database at all
      lockManager.verifyNoInteractions();
      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void executeSkipsTheBatchWhenTheLockIsHeldByAnotherNode() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(API_KEY_PROPERTY)).thenReturn("configured-key");
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.EMAIL_CLASSIFICATION_JOB), any(Duration.class)))
          .thenReturn(null);

      EmailClassificationJob.execute();

      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void executeSkipsCleanlyWhenThereIsNoBacklog() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class);
        MockedStatic<ZeroBounceApiClientCommand> zeroBounce = mockStatic(ZeroBounceApiClientCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(API_KEY_PROPERTY)).thenReturn("configured-key");
      lockManager.when(() -> LockManager.lock(eq(SchedulerManager.EMAIL_CLASSIFICATION_JOB), any(Duration.class)))
          .thenReturn("lock-uuid");
      emailRepository.when(() -> EmailRepository.findUnvalidatedEmails(any(DataConstraints.class))).thenReturn(null);

      EmailClassificationJob.execute();

      zeroBounce.verifyNoInteractions();
    }
  }

  private static Email email(long id, String address) {
    Email email = new Email();
    email.setId(id);
    email.setEmail(address);
    return email;
  }
}
