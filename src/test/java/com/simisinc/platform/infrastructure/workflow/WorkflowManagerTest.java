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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.jeasy.flows.playbook.Playbook;
import org.jeasy.flows.playbook.PlaybookManager;
import org.jeasy.flows.work.DefaultWorkReport;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WhenTask;
import org.jeasy.flows.work.TaskContext;
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

  @Test
  void aDeclinedGuardIsNotAFailureAndIsNotRetried() {
    // The regression this pair exists for (issue 1643). WhenTask reports FAILED when its condition
    // is merely false -- that is the library's way of saying "skip". form-submitted branches on
    // three such guards, so a form with an address configured and confirmation off left two of
    // them false and reported FAILED on its ordinary, successful path. Throwing here sent the
    // playbook back through JobRunr (retries = 1), which re-ran the block that had already sent,
    // and the notification went out twice on every submission.
    Playbook playbook = new Playbook();
    playbook.setId(EVENT_TYPE);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<PlaybookManager> playbookManager = mockStatic(PlaybookManager.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      playbookManager.when(() -> PlaybookManager.getPlaybook(EVENT_TYPE)).thenReturn(playbook);
      // FAILED with no error attached -- what a declined condition looks like
      playbookManager.when(() -> PlaybookManager.run(eq(playbook), any()))
          .thenReturn(new DefaultWorkReport(WorkStatus.FAILED, null));

      assertDoesNotThrow(() -> WorkflowManager.findAndRunWorkflow(new TestEvent()),
          "a declined condition must not be reported as a job failure, or the playbook is retried "
              + "and every side effect it already performed happens again");
    }
  }

  @Test
  void aTaskFailureCarriesAnErrorSoTheJobStillRetries() {
    // The other half: distinguishing declined guards must not stop a genuine failure retrying,
    // which is what issue 1124 added the throw for. Every first-party task routes its failures
    // through TaskReports precisely so they remain distinguishable.
    WorkReport report = TaskReports.failure(null, "No email addresses were found");
    assertEquals(WorkStatus.FAILED, report.getStatus());
    assertNotNull(report.getError(), "a task failure must carry an error to survive as a failure");
    assertEquals("No email addresses were found", report.getError().getMessage());
  }

  @Test
  void theLibraryStillReportsAFalseConditionAsAnErrorLessFailure() {
    // Pins the library behaviour the distinction above depends on. If a future easy-flows upgrade
    // makes a false `when` report COMPLETED, or starts attaching an error to it, this fails loudly
    // rather than letting the retry bug quietly return.
    WhenTask whenTask = new WhenTask();
    TaskContext taskContext = new TaskContext(whenTask);
    taskContext.setWhen("false");

    WorkReport report = whenTask.execute(new WorkContext(), taskContext);
    assertEquals(WorkStatus.FAILED, report.getStatus(),
        "a false condition is still signalled as FAILED by the library");
    assertNull(report.getError(),
        "a declined condition attaches no error -- that is what separates it from a real failure");
  }
}
