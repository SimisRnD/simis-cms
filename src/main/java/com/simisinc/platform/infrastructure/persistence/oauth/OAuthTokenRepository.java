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

package com.simisinc.platform.infrastructure.persistence.oauth;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Persists and retrieves oauth token objects
 *
 * @author matt rajkowski
 * @created 4/22/22 6:59 AM
 */
public class OAuthTokenRepository {

  private static Log LOG = LogFactory.getLog(OAuthTokenRepository.class);

  private static String TABLE_NAME = "oauth_tokens";
  private static String PRIMARY_KEY[] = new String[]{"token_id"};

  public static OAuthToken findByUserTokenId(long userId, long userTokenId) {
    if (userId < 1) {
      return null;
    }
    if (userTokenId < 1) {
      return null;
    }
    return (OAuthToken) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("user_id = ?", userId)
            .add("user_token_id = ?", userTokenId),
        OAuthTokenRepository::buildRecord);
  }

  public static OAuthToken save(OAuthToken record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static OAuthToken add(OAuthToken record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_id", record.getUserId())
        .add("user_token_id", record.getUserTokenId())
        .add("provider", record.getProvider())
        .add("access_token", record.getAccessToken())
        .add("token_type", record.getTokenType())
        .add("expires_in", record.getExpiresIn())
        .addIfExists("refresh_token", record.getRefreshToken())
        .add("refresh_expires_in", record.getRefreshExpiresIn())
        .addIfExists("scope", record.getScope())
        .addIfExists("expires", record.getExpires())
        .addIfExists("refresh_expires", record.getRefreshExpires());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static OAuthToken update(OAuthToken record) {
    SqlUtils updateValues = new SqlUtils()
        .add("access_token", record.getAccessToken())
        .add("token_type", record.getTokenType())
        .add("expires_in", record.getExpiresIn())
        .add("refresh_token", record.getRefreshToken())
        .add("refresh_expires_in", record.getRefreshExpiresIn())
        .add("expires", record.getExpires())
        .add("refresh_expires", record.getRefreshExpires());
    SqlUtils where = new SqlUtils()
        .add("token_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static int remove(UserToken userToken) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("user_token_id = ?", userToken.getId()));
  }

  public static int removeAll(Connection connection, User user) throws SQLException {
    return DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("user_id = ?", user.getId()));
  }

  public static void deleteOldTokens() {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("refresh_expires IS NOT NULL AND refresh_expires < NOW() - INTERVAL '1 day'"));
  }

  private static OAuthToken buildRecord(ResultSet rs) {
    try {
      OAuthToken record = new OAuthToken();
      record.setId(rs.getLong("token_id"));
      record.setUserId(rs.getLong("user_id"));
      record.setUserTokenId(rs.getLong("user_token_id"));
      record.setProvider(rs.getString("provider"));
      record.setAccessToken(rs.getString("access_token"));
      record.setTokenType(rs.getString("token_type"));
      record.setExpiresIn(DB.getInt(rs, "expires_in", -1));
      record.setRefreshToken(rs.getString("refresh_token"));
      record.setRefreshExpiresIn(DB.getInt(rs, "refresh_expires_in", -1));
      record.setScope(rs.getString("scope"));
      record.setExpires(rs.getTimestamp("expires"));
      record.setRefreshExpires(rs.getTimestamp("refresh_expires"));
      record.setCreated(rs.getTimestamp("created"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
