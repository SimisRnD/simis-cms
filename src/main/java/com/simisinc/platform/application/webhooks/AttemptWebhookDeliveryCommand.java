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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.infrastructure.scheduler.webhooks.WebhookDeliveryAttemptJob;

/**
 * Performs one HTTP attempt for a {@code webhook_delivery} row and decides what happens next
 * (issue #418 / #456): success marks it {@code delivered}; failure with attempts remaining
 * re-schedules the next attempt at the next exponential-backoff instant and marks it {@code
 * failed}; failure on the final attempt marks it {@code exhausted} -- never retried again, and
 * deliberately NOT a dead-letter queue or circuit-breaker (both explicitly out of scope for
 * #418).
 *
 * <p>
 * The backoff schedule -- 5s, 30s, 5m, 30m before attempts 2 through 5, then exhausted -- is
 * genuinely new scheduling in this codebase: nothing else here does multi-attempt backoff for
 * outbound HTTP (checked {@code ZeroBounceApiClientCommand}, {@code MailChimpCommand} -- both
 * single-attempt), and JobRunr's own {@code retries=N} job annotation is a single fixed
 * whole-job retry count, not a backoff schedule. Each retry is instead a fresh
 * {@link WebhookDeliveryAttemptJob} scheduled via {@code BackgroundJobRequest.schedule(Instant,
 * ...)} at the row's own {@code next_retry_at} -- this job's {@code @Job(retries = 1)} means
 * JobRunr itself never auto-retries; every retry decision is made here, once, from the
 * database's current state.
 * </p>
 *
 * <p>
 * <b>Idempotency (issue #456):</b> a delivery already in a terminal state ({@code delivered} or
 * {@code exhausted}) is never attempted again, even if a duplicate attempt job is somehow
 * enqueued or run twice for the same {@code webhook_delivery} row. The payload sent is also
 * byte-for-byte identical on every attempt of a given delivery (same stored snapshot, same
 * {@code deliveryId} -- see {@link BuildWebhookPayloadCommand}), so a receiver that already
 * processed an earlier attempt of this delivery can recognize and ignore a retry.
 * </p>
 *
 * <p>
 * That terminal-state check alone does not protect against two concurrent executions of the
 * <em>same</em> non-terminal ({@code pending}/{@code failed}) delivery -- e.g. JobRunr recovery
 * re-running a job that appears crashed but is still finishing its HTTP call. Both would read
 * the same starting state and, without a lock, whichever wrote last would silently overwrite
 * the other's outcome. {@link WebhookDeliveryRepository#recordAttempt} guards against this with
 * an optimistic-concurrency check on {@code attempt_count}; when it reports that this
 * execution's write lost the race, {@link #attempt} logs and stops rather than acting on a
 * result that was never actually persisted.
 * </p>
 *
 * @author SimIS Inc.
 */
public class AttemptWebhookDeliveryCommand {

  private static final Log LOG = LogFactory.getLog(AttemptWebhookDeliveryCommand.class);

  public static final int MAX_ATTEMPTS = 5;

  /** Delay, in seconds, before attempts 2 through 5 -- 5s, 30s, 5m, 30m (issue #418 / #456). */
  static final long[] BACKOFF_SECONDS = { 5, 30, 300, 1800 };

  private static final int RESPONSE_SNIPPET_MAX_LENGTH = 500;

  /**
   * Visible for tests: swap this to observe (or avoid) the real JobRunr schedule call, which
   * requires a live configured {@code JobScheduler} that a plain unit test does not stand up.
   * Receives the instant the retry was scheduled for and the delivery id.
   */
  static BiConsumer<Instant, Long> scheduleRetry = AttemptWebhookDeliveryCommand::scheduleRetryViaJobRunr;

  public static void attempt(long webhookDeliveryId) {
    WebhookDelivery delivery = WebhookDeliveryRepository.findById(webhookDeliveryId);
    if (delivery == null) {
      LOG.warn("No webhook_delivery found for id " + webhookDeliveryId);
      return;
    }

    // Idempotency guard: a delivery already in a terminal state must never be attempted again,
    // even if a duplicate job was enqueued for it (issue #456).
    if (WebhookDelivery.DELIVERED.equals(delivery.getStatus()) || WebhookDelivery.EXHAUSTED.equals(delivery.getStatus())) {
      LOG.info("Skipping webhook delivery " + webhookDeliveryId + " -- already terminal (" + delivery.getStatus() + ")");
      return;
    }

    // The attempt_count read here, before any local mutation, doubles as an optimistic-lock
    // guard on the eventual write -- see WebhookDeliveryRepository#recordAttempt.
    int expectedAttemptCount = delivery.getAttemptCount();

    WebhookSubscription subscription = WebhookSubscriptionRepository.findById(delivery.getWebhookSubscriptionId());
    if (subscription == null || !subscription.getEnabled()) {
      delivery.setLastAttemptedAt(now());
      delivery.setStatus(WebhookDelivery.EXHAUSTED);
      delivery.setNextRetryAt(null);
      delivery.setResponseSnippet("Subscription disabled or removed before delivery completed");
      recordAttempt(delivery, expectedAttemptCount, webhookDeliveryId);
      return;
    }

    delivery.setAttemptCount(delivery.getAttemptCount() + 1);
    delivery.setLastAttemptedAt(now());

    HttpPostCommand.HttpPostResult result = sendAttempt(subscription, delivery);
    boolean success = result != null && result.isSuccess();

    if (result != null) {
      delivery.setResponseCode(result.getStatusCode());
      delivery.setResponseSnippet(truncate(result.getBody()));
    } else {
      // No response was actually received on this attempt (SSRF-blocked, unreachable, timed
      // out). Clear any response_code still sitting on this in-memory object from findById()
      // loading a prior attempt's result -- otherwise recordAttempt would persist a stale,
      // real-looking status code for an attempt that never reached the network.
      delivery.setResponseCode(null);
      delivery.setResponseSnippet(
          "Request failed -- blocked by the SSRF guard, or the endpoint was unreachable/timed out");
    }

    if (success) {
      delivery.setStatus(WebhookDelivery.DELIVERED);
      delivery.setNextRetryAt(null);
      recordAttempt(delivery, expectedAttemptCount, webhookDeliveryId);
      return;
    }

    if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
      delivery.setStatus(WebhookDelivery.EXHAUSTED);
      delivery.setNextRetryAt(null);
      recordAttempt(delivery, expectedAttemptCount, webhookDeliveryId);
      LOG.warn("Webhook delivery " + webhookDeliveryId + " exhausted after " + delivery.getAttemptCount()
          + " attempts to subscription " + subscription.getId());
      return;
    }

    long delaySeconds = BACKOFF_SECONDS[delivery.getAttemptCount() - 1];
    Instant nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    delivery.setStatus(WebhookDelivery.FAILED);
    delivery.setNextRetryAt(Timestamp.from(nextAttemptAt));
    if (!recordAttempt(delivery, expectedAttemptCount, webhookDeliveryId)) {
      // Lost the race to a concurrent execution of this same delivery -- our failed-attempt
      // outcome was not persisted, so scheduling a retry here would risk a duplicate job on top
      // of whatever the winning execution already scheduled (or didn't need to).
      return;
    }

    scheduleRetry.accept(nextAttemptAt, webhookDeliveryId);
  }

  /**
   * Wraps {@link WebhookDeliveryRepository#recordAttempt} with the logging every call site
   * needs: a {@code false} return means this execution lost an optimistic-concurrency race to
   * another concurrent execution of the same delivery, and whatever outcome was computed here
   * was not persisted.
   */
  private static boolean recordAttempt(WebhookDelivery delivery, int expectedAttemptCount, long webhookDeliveryId) {
    boolean recorded = WebhookDeliveryRepository.recordAttempt(delivery, expectedAttemptCount);
    if (!recorded) {
      LOG.warn("Webhook delivery " + webhookDeliveryId
          + " lost a race with a concurrent execution of the same delivery (attempt_count no longer "
          + expectedAttemptCount + " in the database) -- this attempt's outcome was not persisted");
    }
    return recorded;
  }

  private static HttpPostCommand.HttpPostResult sendAttempt(WebhookSubscription subscription, WebhookDelivery delivery) {
    try {
      String signatureHeader = SignWebhookPayloadCommand.signatureHeaderValue(delivery.getPayload(), subscription.getSecret());
      Map<String, String> headers = new HashMap<>();
      headers.put("Content-Type", "application/json");
      if (signatureHeader != null) {
        headers.put(SignWebhookPayloadCommand.HEADER_NAME, signatureHeader);
      }
      // SSRF-guarded: a subscription's url is admin-entered, untrusted destination input (issue
      // #418/#760) -- never the unguarded HttpPostCommand.execute().
      return HttpPostCommand.executeUserUrlWithResponse(subscription.getUrl(), headers, delivery.getPayload(),
          HttpPostCommand.POST);
    } catch (Exception e) {
      LOG.warn("Webhook delivery attempt " + delivery.getAttemptCount() + " to subscription " + subscription.getId()
          + " threw", e);
      return null;
    }
  }

  private static void scheduleRetryViaJobRunr(Instant when, Long webhookDeliveryId) {
    BackgroundJobRequest.schedule(when, new WebhookDeliveryAttemptJob(webhookDeliveryId));
  }

  private static String truncate(String body) {
    if (body == null) {
      return null;
    }
    if (body.length() <= RESPONSE_SNIPPET_MAX_LENGTH) {
      return body;
    }
    return body.substring(0, RESPONSE_SNIPPET_MAX_LENGTH);
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }
}
