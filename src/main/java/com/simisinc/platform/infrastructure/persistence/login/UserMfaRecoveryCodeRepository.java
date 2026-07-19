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

package com.simisinc.platform.infrastructure.persistence.login;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.login.UserMfaRecoveryCode;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves multi-factor authentication recovery codes. Codes are stored only as SHA-256 hashes and are
 * looked up by hash, so verification is a single indexed query rather than a per-row comparison.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class UserMfaRecoveryCodeRepository {

  private static Log LOG = LogFactory.getLog(UserMfaRecoveryCodeRepository.class);

  private static String TABLE_NAME = "user_mfa_recovery_codes";
  private static String[] PRIMARY_KEY = new String[]{"recovery_code_id"};

  public static UserMfaRecoveryCode add(UserMfaRecoveryCode record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_id", record.getUserId())
        .add("code_hash", record.getCodeHash())
        .add("used", false);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static UserMfaRecoveryCode findUnusedByUserIdAndHash(long userId, String codeHash) {
    if (userId < 1 || StringUtils.isBlank(codeHash)) {
      return null;
    }
    return (UserMfaRecoveryCode) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("user_id = ?", userId)
            .add("code_hash = ?", codeHash)
            .add("used = false"),
        UserMfaRecoveryCodeRepository::buildRecord);
  }

  /**
   * Atomically consumes a recovery code: flips it to used only if it is still unused. The
   * "used = false" guard makes single-use race-safe -- if two requests submit the same code
   * concurrently, only the update that actually flips the row affects a row (the other matches
   * none), so exactly one login can succeed.
   *
   * @param record the code to consume
   * @return true only if this call flipped the row from unused to used
   */
  public static boolean markUsed(UserMfaRecoveryCode record) {
    if (record == null || record.getId() < 1) {
      return false;
    }
    return DB.update(
        TABLE_NAME,
        new SqlUtils().add("used", true),
        new SqlUtils()
            .add("recovery_code_id = ?", record.getId())
            .add("used = ?", false));
  }

  public static long countUnusedByUserId(long userId) {
    if (userId < 1) {
      return 0;
    }
    return DB.selectCountFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("user_id = ?", userId)
            .add("used = false"));
  }

  public static void removeAll(long userId) {
    if (userId < 1) {
      return;
    }
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("user_id = ?", userId));
  }

  private static UserMfaRecoveryCode buildRecord(ResultSet rs) {
    try {
      UserMfaRecoveryCode record = new UserMfaRecoveryCode();
      record.setId(rs.getLong("recovery_code_id"));
      record.setUserId(rs.getLong("user_id"));
      record.setCodeHash(rs.getString("code_hash"));
      record.setUsed(rs.getBoolean("used"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
