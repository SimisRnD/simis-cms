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

import com.simisinc.platform.application.webhooks.AttemptWebhookDeliveryCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Runs a single webhook delivery attempt (issue #418). Carries only the {@code webhook_delivery}
 * row's id -- not the event or payload -- so retries always re-read the current row (attempt
 * count, status) from the database rather than a stale in-memory copy, and so this job's JobRunr
 * JSON parameters stay a plain long with no polymorphic-type configuration needed (unlike
 * {@code WorkflowEngineJob}, which must carry the actual {@code Event}).
 *
 * <p>
 * Retries are NOT handled by JobRunr's own {@code retries} mechanism -- {@code retries = 1} here
 * means JobRunr itself does not retry a thrown exception, because
 * {@link AttemptWebhookDeliveryCommand#attempt(long)} already catches every failure (HTTP error,
 * non-2xx, network exception) and re-schedules the next attempt itself via
 * {@code BackgroundJobRequest.schedule(...)} at the computed backoff instant. This is the
 * "self-rescheduling job" approach issue #418 calls for, since JobRunr's built-in retry count is
 * a single fixed whole-job retry, not a multi-step backoff schedule.
 * </p>
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class WebhookDeliveryAttemptJob implements JobRequest {

  @Getter
  @Setter
  private long webhookDeliveryId = -1;

  public WebhookDeliveryAttemptJob(long webhookDeliveryId) {
    this.webhookDeliveryId = webhookDeliveryId;
  }

  @Override
  public Class<WebhookDeliveryAttemptJobRequestHandler> getJobRequestHandler() {
    return WebhookDeliveryAttemptJobRequestHandler.class;
  }

  public static class WebhookDeliveryAttemptJobRequestHandler implements JobRequestHandler<WebhookDeliveryAttemptJob> {
    @Override
    @Job(name = "Attempt a webhook delivery", retries = 1)
    public void run(WebhookDeliveryAttemptJob jobRequest) {
      AttemptWebhookDeliveryCommand.attempt(jobRequest.getWebhookDeliveryId());
    }
  }
}
