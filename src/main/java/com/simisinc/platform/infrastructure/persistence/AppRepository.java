/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves app objects
 *
 * @author matt rajkowski
 * @created 4/17/18 7:48 PM
 */
public class AppRepository {

  private static Log LOG = LogFactory.getLog(AppRepository.class);

  private static String TABLE_NAME = "apps";
  private static String[] PRIMARY_KEY = new String[]{"app_id"};

  private static DataResult query(AppSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("app_id = ?", specification.getId(), -1);
      if (specification.getPublicKey() != null) {
        where.add("public_key = ?", specification.getPublicKey());
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, AppRepository::buildRecord);
  }

  public static App findByPublicKey(String publicKey) {
    if (StringUtils.isBlank(publicKey)) {
      return null;
    }
    return (App) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("public_key = ?", publicKey),
        AppRepository::buildRecord);
  }

  public static App findById(long id) {
    if (id == -1) {
      return null;
    }
    return (App) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("app_id = ?", id),
        AppRepository::buildRecord);
  }

  public static List<App> findAll() {
    return findAll(null, null);
  }

  public static List<App> findAll(AppSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("name");
    DataResult result = query(specification, constraints);
    return (List<App>) result.getRecords();
  }

  public static App save(App record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static App add(App record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("public_key", record.getPublicKey())
        .add("private_key", encryptPrivateKey(record.getPrivateKey()))
        .add("created_by", record.getCreatedBy())
        .add("enabled", record.isEnabled());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static App update(App record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("private_key", encryptPrivateKey(record.getPrivateKey()))
        .add("enabled", record.isEnabled());
    SqlUtils where = new SqlUtils()
        .add("app_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      CacheManager.invalidateKey(CacheManager.APP_CACHE, record.getPublicKey());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  /**
   * Permanently removes an app record (issue: no in-app remediation path for a leaked Client ID).
   * The cache is invalidated the same way {@link #update} does, so a deleted app's key stops
   * authenticating immediately rather than lingering in {@code CacheManager.APP_CACHE} until it
   * expires -- {@code RestRequestFilter} looks up every API request's key through that cache.
   */
  public static boolean remove(App record) {
    // sessions.app_id is a plain FK with no ON DELETE clause (NEW_10000__new_database.sql), and
    // every authenticated REST call writes a sessions row with app_id set (RestRequestFilter),
    // so any App that has ever been used has permanent referencing rows -- exactly the class of
    // App (a leaked, actively-used credential) the admin UI's Delete action exists to remediate.
    // Without this, DB.deleteFrom() below would fail the FK constraint and silently no-op,
    // leaving the leaked credential fully intact despite the confirmation dialog promising it
    // "will stop working immediately." Session rows themselves are untouched -- only their
    // now-dangling app_id back-reference is cleared.
    DB.update("sessions", "app_id = NULL", new SqlUtils().add("app_id = ?", record.getId()));
    if (DB.deleteFrom(TABLE_NAME, new SqlUtils().add("app_id = ?", record.getId())) > 0) {
      CacheManager.invalidateKey(CacheManager.APP_CACHE, record.getPublicKey());
      return true;
    }
    return false;
  }

  /**
   * Encrypts {@code privateKey} at rest via {@link SecretCryptoCommand}, mirroring the
   * encrypt-on-write pattern {@code WebhookSubscriptionRepository} uses for its {@code secret}
   * column. Unlike that caller, this does NOT call {@link SecretCryptoCommand#encrypt} unguarded:
   * since issue #16, {@code encrypt()} fails closed (throws {@code IllegalStateException}) when no
   * {@code CMS_SECRET_KEY} is configured, and App creation/update is core admin functionality (not
   * an opt-in feature like MFA or webhooks) for a field (privateKey) that is confirmed unused
   * anywhere else in this codebase. Making every App save newly depend on an unrelated environment
   * variable would be a regression, so this checks {@link SecretCryptoCommand#isEnabled()} first and
   * falls back to storing the value as-is (matching pre-encryption behavior) when no key is
   * configured -- the same "skip rather than throw" precedent
   * {@code V20260719_1004__reencrypt_secret_properties} uses for the identical reason.
   */
  private static String encryptPrivateKey(String privateKey) {
    if (!SecretCryptoCommand.isEnabled()) {
      return privateKey;
    }
    return SecretCryptoCommand.encrypt(privateKey);
  }

  /**
   * Build the record from the database
   *
   * @param rs
   * @return
   * @throws SQLException
   */
  private static App buildRecord(ResultSet rs) {
    try {
      App record = new App();
      record.setId(rs.getLong("app_id"));
      record.setName(rs.getString("name"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setPublicKey(rs.getString("public_key"));
      // Decrypt on read (symmetric with encryptPrivateKey() above); legacy plaintext rows and
      // blank values pass through decrypt() unchanged, so pre-existing rows saved before this
      // change keep working exactly as before.
      record.setPrivateKey(SecretCryptoCommand.decrypt(rs.getString("private_key")));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
