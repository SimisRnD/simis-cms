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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves bot user-agent signature objects
 *
 * @author elizabeth houser
 */
public class BotUserAgentRepository {

  private static Log LOG = LogFactory.getLog(BotUserAgentRepository.class);

  private static String TABLE_NAME = "bot_list";
  private static String[] PRIMARY_KEY = new String[]{"bot_list_id"};

  private static DataResult query(DataConstraints constraints) {
    return DB.selectAllFrom(TABLE_NAME, null, constraints, BotUserAgentRepository::buildRecord);
  }

  public static List<BotUserAgent> findAll() {
    return findAll(null);
  }

  public static List<BotUserAgent> findAll(DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(constraints);
    return (List<BotUserAgent>) result.getRecords();
  }

  public static BotUserAgent findById(long id) {
    if (id == -1) {
      return null;
    }
    return (BotUserAgent) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("bot_list_id = ?", id),
        BotUserAgentRepository::buildRecord);
  }

  public static BotUserAgent findByUserAgent(String userAgent) {
    if (StringUtils.isBlank(userAgent)) {
      return null;
    }
    return (BotUserAgent) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("user_agent = ?", userAgent),
        BotUserAgentRepository::buildRecord);
  }

  public static BotUserAgent save(BotUserAgent record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static BotUserAgent add(BotUserAgent record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_agent", StringUtils.trimToNull(record.getUserAgent()))
        .addIfExists("label", StringUtils.trimToNull(record.getLabel()))
        .addIfExists("created", record.getCreated());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static BotUserAgent update(BotUserAgent record) {
    SqlUtils updateValues = new SqlUtils()
        .add("user_agent", StringUtils.trimToNull(record.getUserAgent()))
        .addIfExists("label", StringUtils.trimToNull(record.getLabel()));
    SqlUtils where = new SqlUtils()
        .add("bot_list_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(BotUserAgent record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("bot_list_id = ?", record.getId()));
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static BotUserAgent buildRecord(ResultSet rs) {
    try {
      BotUserAgent record = new BotUserAgent();
      record.setId(rs.getLong("bot_list_id"));
      record.setUserAgent(rs.getString("user_agent"));
      record.setLabel(rs.getString("label"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  public static void export(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "user_agent AS \"Partial User Agent\"",
            "label AS \"Label\"",
            "created AS \"Date\""
        );
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("bot_list_id");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, null, null, null, constraints, file);
  }
}
