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

package com.simisinc.platform.domain.model.webhooks;

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * One delivery attempt-series for a {@link WebhookSubscription} against a single domain event
 * (issue #418). A row is created once when the event fires and is updated in place across
 * retries -- {@link #attemptCount} and {@link #status} track progress through the backoff
 * schedule ({@code AttemptWebhookDeliveryCommand}), rather than one row per HTTP attempt.
 *
 * <p>
 * {@link #deliveryUuid} is generated once and sent unchanged in the payload on every attempt of
 * this delivery (including retries), so a receiver can recognize a retried delivery as the same
 * logical event and de-duplicate it -- issue #456's idempotency requirement.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookDelivery extends Entity {

  public static final String PENDING = "pending";
  public static final String DELIVERED = "delivered";
  public static final String FAILED = "failed";
  public static final String EXHAUSTED = "exhausted";

  private Long id = -1L;
  private long webhookSubscriptionId = -1;
  private String eventType = null;
  private String deliveryUuid = null;
  private String payload = null;
  private int attemptCount = 0;
  private String status = PENDING;
  private Timestamp lastAttemptedAt = null;
  private Timestamp nextRetryAt = null;
  private Integer responseCode = null;
  private String responseSnippet = null;
  private Timestamp created = null;

  public WebhookDelivery() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getWebhookSubscriptionId() {
    return webhookSubscriptionId;
  }

  public void setWebhookSubscriptionId(long webhookSubscriptionId) {
    this.webhookSubscriptionId = webhookSubscriptionId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getDeliveryUuid() {
    return deliveryUuid;
  }

  public void setDeliveryUuid(String deliveryUuid) {
    this.deliveryUuid = deliveryUuid;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Timestamp getLastAttemptedAt() {
    return lastAttemptedAt;
  }

  public void setLastAttemptedAt(Timestamp lastAttemptedAt) {
    this.lastAttemptedAt = lastAttemptedAt;
  }

  public Timestamp getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(Timestamp nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
  }

  public Integer getResponseCode() {
    return responseCode;
  }

  public void setResponseCode(Integer responseCode) {
    this.responseCode = responseCode;
  }

  public String getResponseSnippet() {
    return responseSnippet;
  }

  public void setResponseSnippet(String responseSnippet) {
    this.responseSnippet = responseSnippet;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
