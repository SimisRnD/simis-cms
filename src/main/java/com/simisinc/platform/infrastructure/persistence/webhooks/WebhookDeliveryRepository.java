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

package com.simisinc.platform.infrastructure.persistence.webhooks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves webhook delivery records (issue #418). One row per delivery
 * attempt-series -- {@code AttemptWebhookDeliveryCommand} updates a row in place across retries
 * rather than inserting a new one per attempt.
 *
 * @author SimIS Inc.
 */
public class WebhookDeliveryRepository {

  private static Log LOG = LogFactory.getLog(WebhookDeliveryRepository.class);

  private static String TABLE_NAME = "webhook_delivery";
  private static String[] PRIMARY_KEY = new String[] { "webhook_delivery_id" };

  public static WebhookDelivery findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebhookDelivery) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("webhook_delivery_id = ?", id),
        WebhookDeliveryRepository::buildRecord);
  }

  public static WebhookDelivery findByDeliveryUuid(String deliveryUuid) {
    if (deliveryUuid == null) {
      return null;
    }
    return (WebhookDelivery) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("delivery_uuid = ?", deliveryUuid),
        WebhookDeliveryRepository::buildRecord);
  }

  public static List<WebhookDelivery> findBySubscriptionId(long webhookSubscriptionId) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, new SqlUtils().add("webhook_subscription_id = ?", webhookSubscriptionId),
        new DataConstraints().setDefaultColumnToSortBy("webhook_delivery_id DESC").setUseCount(false),
        WebhookDeliveryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<WebhookDelivery>) result.getRecords();
    }
    return new ArrayList<>();
  }

  public static WebhookDelivery add(WebhookDelivery record) {
    SqlUtils insertValues = new SqlUtils()
        .add("webhook_subscription_id", record.getWebhookSubscriptionId())
        .add("event_type", record.getEventType())
        .add("delivery_uuid", record.getDeliveryUuid())
        .add("payload", record.getPayload())
        .add("attempt_count", record.getAttemptCount())
        .add("status", record.getStatus());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /**
   * Persists the outcome of a delivery attempt: attempt count, status, timestamps, and the
   * response recorded for this attempt. Called by {@code AttemptWebhookDeliveryCommand} after
   * every attempt, success or failure.
   */
  public static boolean recordAttempt(WebhookDelivery record) {
    SqlUtils updateValues = new SqlUtils()
        .add("attempt_count", record.getAttemptCount())
        .add("status", record.getStatus())
        .add("last_attempted_at", record.getLastAttemptedAt())
        .add("next_retry_at", record.getNextRetryAt());
    if (record.getResponseCode() != null) {
      updateValues.add("response_code", record.getResponseCode());
    }
    if (record.getResponseSnippet() != null) {
      updateValues.add("response_snippet", record.getResponseSnippet(), 1000);
    }
    SqlUtils where = new SqlUtils().add("webhook_delivery_id = ?", record.getId());
    return DB.update(TABLE_NAME, updateValues, where);
  }

  private static WebhookDelivery buildRecord(ResultSet rs) {
    try {
      WebhookDelivery record = new WebhookDelivery();
      record.setId(rs.getLong("webhook_delivery_id"));
      record.setWebhookSubscriptionId(rs.getLong("webhook_subscription_id"));
      record.setEventType(rs.getString("event_type"));
      record.setDeliveryUuid(rs.getString("delivery_uuid"));
      record.setPayload(rs.getString("payload"));
      record.setAttemptCount(rs.getInt("attempt_count"));
      record.setStatus(rs.getString("status"));
      record.setLastAttemptedAt(rs.getTimestamp("last_attempted_at"));
      record.setNextRetryAt(rs.getTimestamp("next_retry_at"));
      int responseCode = rs.getInt("response_code");
      record.setResponseCode(rs.wasNull() ? null : responseCode);
      record.setResponseSnippet(rs.getString("response_snippet"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
