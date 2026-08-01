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

package com.simisinc.platform.infrastructure.scheduler.webhooks;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import com.simisinc.platform.application.webhooks.DispatchWebhookDeliveriesCommand;
import com.simisinc.platform.domain.events.Event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fans a domain event out to every matching, enabled webhook subscription (issue #418).
 * Enqueued by {@code WorkflowManager.triggerWorkflowForEvent} alongside (not instead of)
 * {@code WorkflowEngineJob} -- see that method's javadoc for why webhook delivery runs
 * unconditionally for every event rather than depending on a playbook/YAML lookup succeeding.
 *
 * <p>
 * Shaped exactly like {@code WorkflowEngineJob}: it carries the actual polymorphic {@code Event}
 * (not just an id), which round-trips through JobRunr's JSON mapper because
 * {@code SchedulerManager.startup} already configures the mapper to trust the {@code Event}
 * hierarchy for {@code WorkflowEngineJob}'s sake -- no extra JobRunr configuration was needed
 * for this class.
 * </p>
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class WebhookDispatchJob implements JobRequest {

  @Getter
  @Setter
  private Event event = null;

  public WebhookDispatchJob(Event event) {
    this.event = event;
  }

  @Override
  public Class<WebhookDispatchJobRequestHandler> getJobRequestHandler() {
    return WebhookDispatchJobRequestHandler.class;
  }

  public static class WebhookDispatchJobRequestHandler implements JobRequestHandler<WebhookDispatchJob> {
    @Override
    @Job(name = "Dispatch webhooks for an event", retries = 1)
    public void run(WebhookDispatchJob jobRequest) {
      DispatchWebhookDeliveriesCommand.dispatch(jobRequest.getEvent());
    }
  }
}
