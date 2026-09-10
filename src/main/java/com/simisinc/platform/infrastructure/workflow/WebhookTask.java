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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.work.DefaultWorkReport;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.Work;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkStatus;

import com.simisinc.platform.application.webhooks.DispatchWebhookDeliveriesCommand;
import com.simisinc.platform.domain.events.Event;

import static com.simisinc.platform.infrastructure.workflow.WorkflowManager.EVENT_OBJECT;

/**
 * A workflow task ({@code id: webhook} in {@code workflow-task-library.yml}) that dispatches the
 * work context's event to every matching webhook subscription (issue #418) -- the same shape as
 * {@link EmailTask} and {@link HistoryTask}, so a playbook YAML author could add a
 * {@code - webhook:} step to any {@code *-workflows.yml} playbook if a specific event ever needs
 * playbook-conditional webhook delivery (e.g. gated behind a {@code when:} the same playbook
 * already uses for an email or history step).
 *
 * <p>
 * <b>This task is intentionally NOT wired into any playbook YAML in this codebase.</b>
 * {@code WorkflowManager.triggerWorkflowForEvent} already calls
 * {@link DispatchWebhookDeliveriesCommand#dispatch(Event)} directly and unconditionally for
 * every domain event that fires, regardless of whether a playbook exists for that event's
 * {@code getDomainEventType()} or what steps it lists -- see that method's javadoc for why
 * per-event YAML curation (adding a step to all 14 {@code *-workflows.yml} playbook entries, and
 * remembering to add a 15th for every future event) was rejected as brittle. If a
 * {@code - webhook:} step is ever added to a playbook, remove or scope the unconditional call
 * for that event type first -- otherwise the same event double-dispatches (two
 * {@code webhook_delivery} rows, two deliveries) once through this task and once through the
 * unconditional path.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookTask implements Work {

  private static final Log LOG = LogFactory.getLog(WebhookTask.class);

  @Override
  public WorkReport execute(WorkContext workContext, TaskContext taskContext) {
    Event event = (Event) workContext.get(EVENT_OBJECT);
    if (event == null) {
      LOG.error("No event found in the work context");
      return TaskReports.failure(workContext, "No event found in the work context");
    }
    try {
      DispatchWebhookDeliveriesCommand.dispatch(event);
      return new DefaultWorkReport(WorkStatus.COMPLETED, workContext);
    } catch (Exception e) {
      LOG.error("Webhook dispatch failed for event: " + event.getDomainEventType(), e);
      return TaskReports.failure(workContext, "Webhook dispatch failed", e);
    }
  }
}
