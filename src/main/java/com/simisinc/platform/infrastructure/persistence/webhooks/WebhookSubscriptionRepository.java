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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves webhook subscriptions (issue #418). CRUD only -- the admin UI is issue
 * #453.
 *
 * <p>
 * {@code secret} is encrypted at rest via {@link SecretCryptoCommand} -- the same
 * encrypt-on-write/decrypt-on-read pattern {@code UserRepository#saveMfaSecret}/{@code
 * #buildRecord} use for the TOTP seed -- so a database dump alone does not yield a usable HMAC
 * secret. This is transparent to every caller: {@link #add}/{@link #update} encrypt on the way
 * in, {@link #buildRecord} decrypts on the way out, so {@link WebhookSubscription#getSecret()}
 * always returns plaintext to callers such as {@code AttemptWebhookDeliveryCommand} and {@code
 * SignWebhookPayloadCommand}.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookSubscriptionRepository {

  private static Log LOG = LogFactory.getLog(WebhookSubscriptionRepository.class);

  private static String TABLE_NAME = "webhook_subscription";
  private static String[] PRIMARY_KEY = new String[] { "webhook_subscription_id" };

  public static List<WebhookSubscription> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, null, new DataConstraints().setDefaultColumnToSortBy("webhook_subscription_id"),
        WebhookSubscriptionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<WebhookSubscription>) result.getRecords();
    }
    return new ArrayList<>();
  }

  public static WebhookSubscription findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebhookSubscription) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("webhook_subscription_id = ?", id),
        WebhookSubscriptionRepository::buildRecord);
  }

  /**
   * Every enabled subscription whose event type list contains {@code eventType}. Filtering is
   * done in Java rather than SQL (see {@link WebhookSubscription#matchesEventType(String)}) --
   * subscriptions are admin-managed and expected to be few, so a full scan of enabled rows is
   * simpler and safer than a LIKE-based CSV match.
   */
  public static List<WebhookSubscription> findEnabledBySubscribedEventType(String eventType) {
    List<WebhookSubscription> matches = new ArrayList<>();
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, new SqlUtils().add("enabled = ?", true),
        new DataConstraints().setDefaultColumnToSortBy("webhook_subscription_id").setUseCount(false),
        WebhookSubscriptionRepository::buildRecord);
    if (!result.hasRecords()) {
      return matches;
    }
    for (Object record : result.getRecords()) {
      WebhookSubscription subscription = (WebhookSubscription) record;
      if (subscription.matchesEventType(eventType)) {
        matches.add(subscription);
      }
    }
    return matches;
  }

  public static WebhookSubscription save(WebhookSubscription record) {
    if (record.getId() != null && record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static WebhookSubscription add(WebhookSubscription record) {
    SqlUtils insertValues = new SqlUtils()
        .add("url", SecretCryptoCommand.encrypt(record.getUrl()))
        .add("event_types", record.getEventTypes())
        .add("secret", SecretCryptoCommand.encrypt(record.getSecret()))
        .add("enabled", record.getEnabled())
        .add("integration_id", record.getIntegrationId())
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static WebhookSubscription update(WebhookSubscription record) {
    SqlUtils updateValues = new SqlUtils()
        .add("url", SecretCryptoCommand.encrypt(record.getUrl()))
        .add("event_types", record.getEventTypes())
        .add("secret", SecretCryptoCommand.encrypt(record.getSecret()))
        .add("enabled", record.getEnabled())
        .add("integration_id", record.getIntegrationId())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils().add("webhook_subscription_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(WebhookSubscription record) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("webhook_subscription_id = ?", record.getId())) > 0;
  }

  /** Every subscription tagged as created by the given registry integration id (issue #455's one-click install). */
  public static List<WebhookSubscription> findByIntegrationId(String integrationId) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, new SqlUtils().add("integration_id = ?", integrationId),
        new DataConstraints().setDefaultColumnToSortBy("webhook_subscription_id").setUseCount(false),
        WebhookSubscriptionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<WebhookSubscription>) result.getRecords();
    }
    return new ArrayList<>();
  }

  private static WebhookSubscription buildRecord(ResultSet rs) {
    try {
      WebhookSubscription record = new WebhookSubscription();
      record.setId(rs.getLong("webhook_subscription_id"));
      record.setUrl(SecretCryptoCommand.decrypt(rs.getString("url")));
      record.setEventTypes(rs.getString("event_types"));
      record.setSecret(SecretCryptoCommand.decrypt(rs.getString("secret")));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setIntegrationId(rs.getString("integration_id"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
