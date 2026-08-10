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

package com.simisinc.platform.infrastructure.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.jeasy.flows.playbook.Playbook;
import org.jeasy.flows.playbook.PlaybookManager;
import org.jeasy.flows.work.DefaultWorkReport;
import org.jeasy.flows.work.WorkStatus;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.events.Event;

/**
 * Verifies {@link WorkflowManager#findAndRunWorkflow} propagates a step's reported failure as a
 * thrown exception (issue #1124). Before this fix, {@code PlaybookManager.run()}'s return value
 * was discarded entirely -- a step reporting {@code WorkStatus.FAILED} (e.g. {@link EmailTask}'s
 * {@code send()} throwing) never reached the enclosing JobRunr job
 * ({@code WorkflowEngineJobRequestHandler}, {@code retries = 1}), so its retry never fired.
 *
 * @author SimIS Inc.
 */
class WorkflowManagerTest {

  private static final String EVENT_TYPE = "test-event";

  private static class TestEvent extends Event {
    @Override
    public String getDomainEventType() {
      return EVENT_TYPE;
    }
  }

  @Test
  void throwsWhenAPlaybookStepReportsFailure() {
    Playbook playbook = new Playbook();
    playbook.setId(EVENT_TYPE);
    RuntimeException cause = new RuntimeException("simulated step failure");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<PlaybookManager> playbookManager = mockStatic(PlaybookManager.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      playbookManager.when(() -> PlaybookManager.getPlaybook(EVENT_TYPE)).thenReturn(playbook);
      playbookManager.when(() -> PlaybookManager.run(eq(playbook), any()))
          .thenReturn(new DefaultWorkReport(WorkStatus.FAILED, null, cause));

      RuntimeException thrown = assertThrows(RuntimeException.class,
          () -> WorkflowManager.findAndRunWorkflow(new TestEvent()));
      assertEquals(cause, thrown.getCause());
    }
  }

  @Test
  void doesNotThrowWhenEveryPlaybookStepCompletes() {
    Playbook playbook = new Playbook();
    playbook.setId(EVENT_TYPE);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<PlaybookManager> playbookManager = mockStatic(PlaybookManager.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      playbookManager.when(() -> PlaybookManager.getPlaybook(EVENT_TYPE)).thenReturn(playbook);
      playbookManager.when(() -> PlaybookManager.run(eq(playbook), any()))
          .thenReturn(new DefaultWorkReport(WorkStatus.COMPLETED, null));

      assertDoesNotThrow(() -> WorkflowManager.findAndRunWorkflow(new TestEvent()));
    }
  }
}
