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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;

/**
 * Fires a single, synchronous "test send" of a sample payload to a {@code webhook_subscription}'s
 * URL (issue #453's dry-run button). Deliberately NOT {@code AttemptWebhookDeliveryCommand}: that
 * command is written exclusively to run as a JobRunr background job against a real, persisted
 * {@code webhook_delivery} row, and on failure schedules a real retry chain (up to ~24 hours of
 * background attempts, see its own javadoc) that would misleadingly pollute the delivery log with
 * a row indistinguishable from a genuine subscriber delivery.
 *
 * <p>
 * This command instead calls the same two stateless primitives {@code
 * AttemptWebhookDeliveryCommand#sendAttempt} uses -- {@link SignWebhookPayloadCommand#signatureHeaderValue}
 * and {@link HttpPostCommand#executeUserUrlWithResponse} (SSRF-guarded, same as production) --
 * directly, once, with no retry and no persistence: no {@code webhook_delivery} row is created,
 * and no JobRunr job is ever scheduled. The signature and HTTP call are otherwise identical to a
 * real production delivery, so a passing test send is a genuine proof the subscription is
 * reachable and correctly signed.
 * </p>
 *
 * @author SimIS Inc.
 */
public class TestSendWebhookCommand {

  private static final Log LOG = LogFactory.getLog(TestSendWebhookCommand.class);

  private static final int RESPONSE_SNIPPET_MAX_LENGTH = 500;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** The outcome of one test send -- never backed by a persisted {@code webhook_delivery} row. */
  public static final class TestSendResult {
    private final boolean requestSent;
    private final Integer statusCode;
    private final String responseSnippet;
    private final String payload;
    private final String signatureHeaderValue;
    private final Timestamp sentAt;

    public TestSendResult(boolean requestSent, Integer statusCode, String responseSnippet, String payload,
        String signatureHeaderValue, Timestamp sentAt) {
      this.requestSent = requestSent;
      this.statusCode = statusCode;
      this.responseSnippet = responseSnippet;
      this.payload = payload;
      this.signatureHeaderValue = signatureHeaderValue;
      this.sentAt = sentAt;
    }

    /** True when a response was actually received (regardless of status code). */
    public boolean isRequestSent() {
      return requestSent;
    }

    public Integer getStatusCode() {
      return statusCode;
    }

    public String getResponseSnippet() {
      return responseSnippet;
    }

    public String getPayload() {
      return payload;
    }

    public String getSignatureHeaderValue() {
      return signatureHeaderValue;
    }

    public Timestamp getSentAt() {
      return sentAt;
    }
  }

  private TestSendWebhookCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param subscription the subscription to test (its current url and secret are used)
   * @param eventType the event type id to simulate in the sample payload's {@code event} field
   *        (e.g. one the subscription is subscribed to)
   * @return the result of the single synchronous attempt; never null
   */
  public static TestSendResult send(WebhookSubscription subscription, String eventType) {
    Timestamp sentAt = new Timestamp(System.currentTimeMillis());
    String payload = buildSamplePayload(subscription, eventType, sentAt);
    String signatureHeaderValue = SignWebhookPayloadCommand.signatureHeaderValue(payload, subscription.getSecret());

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    if (signatureHeaderValue != null) {
      headers.put(SignWebhookPayloadCommand.HEADER_NAME, signatureHeaderValue);
    }

    HttpPostCommand.HttpPostResult result;
    try {
      // SSRF-guarded, same as a real delivery attempt -- the url is admin-entered, untrusted
      // destination input.
      result = HttpPostCommand.executeUserUrlWithResponse(subscription.getUrl(), headers, payload,
          HttpPostCommand.POST);
    } catch (Exception e) {
      LOG.warn("Webhook test send to subscription " + subscription.getId() + " threw", e);
      result = null;
    }

    if (result == null) {
      return new TestSendResult(false, null, null, payload, signatureHeaderValue, sentAt);
    }
    return new TestSendResult(true, result.getStatusCode(), truncate(result.getBody()), payload, signatureHeaderValue,
        sentAt);
  }

  /**
   * A fixed, hand-crafted sample matching {@code BuildWebhookPayloadCommand}'s real {@code
   * {event, occurredOn, deliveryId, data}} shape -- one generic {@code data} body regardless of
   * event type, not a per-type replica of that command's 14 real branches (this is a test send,
   * not a production delivery; the receiver's own webhook handler is what's actually being
   * exercised, not this application's field-mapping logic).
   */
  private static String buildSamplePayload(WebhookSubscription subscription, String eventType, Timestamp sentAt) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("event", eventType);
    root.put("occurredOn", sentAt);
    root.put("deliveryId", UUID.randomUUID().toString());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("test", true);
    data.put("message", "This is a test delivery from SimIS CMS -- no action is required.");
    data.put("subscriptionId", subscription.getId());
    root.put("data", data);
    try {
      return OBJECT_MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      LOG.error("Could not serialize webhook test payload", e);
      return "{}";
    }
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
}
