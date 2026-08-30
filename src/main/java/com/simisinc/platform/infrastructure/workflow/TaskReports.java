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

import org.jeasy.flows.work.DefaultWorkReport;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkStatus;

/**
 * Builds the {@link WorkReport} a first-party workflow task returns when it cannot do its job.
 *
 * <p>
 * The workflow engine has one status for two different outcomes. {@code WhenTask} returns
 * {@code FAILED} when its condition is simply false -- that is how the library says "skip this" --
 * and a task returns {@code FAILED} when something actually went wrong. Both arrive at
 * {@link WorkflowManager#findAndRunWorkflow} as a bare {@code FAILED} with no error attached, and
 * nothing there could tell them apart.
 * </p>
 *
 * <p>
 * That cost real sends. The {@code form-submitted} playbook branches on three {@code when} guards;
 * a form with an address configured and submitter confirmation off leaves two of them false. Each
 * false guard reported {@code FAILED}, {@code findAndRunWorkflow} threw (issue 1124, so that a
 * genuine email failure would retry), and JobRunr's {@code retries = 1} ran the whole playbook a
 * second time -- re-sending the notification that had already gone out. Exactly two emails per
 * submission, every time. That is issue 1643.
 * </p>
 *
 * <p>
 * So a first-party task attaches an error to say "this is a real failure, retry it", and an
 * error-less {@code FAILED} is read as the library's own "condition declined, stop here". The
 * distinction lives here rather than at each of the ten return sites so it cannot drift.
 * </p>
 */
public final class TaskReports {

  private TaskReports() {
    // Static factory, not instantiated
  }

  /**
   * A failure a task is responsible for, which should be retried.
   *
   * @param workContext the context to return
   * @param reason what went wrong, in the same words the task logged
   * @return a FAILED report carrying an error, so it is distinguishable from a declined guard
   */
  public static WorkReport failure(WorkContext workContext, String reason) {
    return new DefaultWorkReport(WorkStatus.FAILED, workContext, new WorkflowTaskException(reason));
  }

  /**
   * A failure a task is responsible for, where an exception already describes it.
   *
   * <p>
   * The cause is attached as-is rather than wrapped. The marker exception exists only so an
   * error-LESS failure can be told apart from a declined guard; where a real exception is already
   * in hand it is a better error than anything wrapped around it, and callers and tests can still
   * match on the original type. {@code reason} is not discarded -- every caller logs it at the
   * point of failure, where the surrounding context is available.
   * </p>
   *
   * @param workContext the context to return
   * @param reason what went wrong, for the caller's own logging
   * @param cause the underlying exception, carried through unchanged
   * @return a FAILED report carrying the original error
   */
  public static WorkReport failure(WorkContext workContext, String reason, Throwable cause) {
    return new DefaultWorkReport(WorkStatus.FAILED, workContext, cause);
  }

  /** Marks a report as a genuine task failure rather than a declined {@code when} guard. */
  public static class WorkflowTaskException extends RuntimeException {
    public WorkflowTaskException(String message) {
      super(message);
    }

    public WorkflowTaskException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
