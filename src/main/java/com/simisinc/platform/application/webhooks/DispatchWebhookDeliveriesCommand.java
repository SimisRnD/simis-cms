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

package com.simisinc.platform.application.webhooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.infrastructure.scheduler.webhooks.WebhookDeliveryAttemptJob;

/**
 * Creates a {@code webhook_delivery} row (and enqueues its first delivery attempt) for every
 * enabled {@link WebhookSubscription} matching a domain event's type (issue #418). This is the
 * single place a domain event fans out to webhook subscribers -- called both from
 * {@link com.simisinc.platform.infrastructure.workflow.WebhookTask} (when reached as a playbook
 * step) and directly, unconditionally, from {@code WorkflowManager.triggerWorkflowForEvent} for
 * every event regardless of whether a playbook/YAML entry exists for it. See
 * {@code WebhookTask}'s javadoc for why coverage is guaranteed at that second call site rather
 * than by curating a {@code - webhook:} step into every {@code *-workflows.yml} playbook.
 *
 * @author SimIS Inc.
 */
public class DispatchWebhookDeliveriesCommand {

  private static final Log LOG = LogFactory.getLog(DispatchWebhookDeliveriesCommand.class);

  /**
   * Visible for tests: swap this to observe (or avoid) the real JobRunr enqueue call, which
   * requires a live configured {@code JobScheduler} that a plain unit test does not stand up.
   */
  static LongConsumer enqueueFirstAttempt = DispatchWebhookDeliveriesCommand::enqueueViaJobRunr;

  /**
   * Dispatches {@code event} to every enabled, matching subscription. Returns the delivery
   * records created (possibly empty, never null) so callers/tests can assert on what happened
   * without a second repository round-trip.
   */
  public static List<WebhookDelivery> dispatch(Event event) {
    List<WebhookDelivery> created = new ArrayList<>();
    if (event == null || StringUtils.isBlank(event.getDomainEventType())) {
      LOG.warn("Cannot dispatch webhooks for a null event or blank domain event type");
      return created;
    }

    List<WebhookSubscription> subscriptions =
        WebhookSubscriptionRepository.findEnabledBySubscribedEventType(event.getDomainEventType());
    if (subscriptions.isEmpty()) {
      return created;
    }

    for (WebhookSubscription subscription : subscriptions) {
      String deliveryId = UUID.randomUUID().toString();
      String payload = BuildWebhookPayloadCommand.build(event, deliveryId);
      if (payload == null) {
        LOG.error("Skipping webhook delivery for subscription " + subscription.getId()
            + " -- payload could not be built for event type " + event.getDomainEventType());
        continue;
      }

      WebhookDelivery delivery = new WebhookDelivery();
      delivery.setWebhookSubscriptionId(subscription.getId());
      delivery.setEventType(event.getDomainEventType());
      delivery.setDeliveryUuid(deliveryId);
      delivery.setPayload(payload);
      delivery.setStatus(WebhookDelivery.PENDING);

      WebhookDelivery saved = WebhookDeliveryRepository.add(delivery);
      if (saved == null) {
        LOG.error("Could not save webhook_delivery row for subscription " + subscription.getId());
        continue;
      }
      created.add(saved);
      enqueueFirstAttempt.accept(saved.getId());
    }
    return created;
  }

  private static void enqueueViaJobRunr(long webhookDeliveryId) {
    BackgroundJobRequest.enqueue(new WebhookDeliveryAttemptJob(webhookDeliveryId));
  }
}
