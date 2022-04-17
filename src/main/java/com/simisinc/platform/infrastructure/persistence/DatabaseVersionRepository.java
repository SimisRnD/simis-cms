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

import com.simisinc.platform.DatabaseVersion;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves database version objects
 *
 * @author matt rajkowski
 * @created 6/21/18 2:40 PM
 */
public class DatabaseVersionRepository {

  private static Log LOG = LogFactory.getLog(DatabaseVersionRepository.class);

  private static String TABLE_NAME = "database_version";
  private static String PRIMARY_KEY[] = new String[]{"version_id"};

  public static long count() {
    return DB.selectCountFrom(TABLE_NAME);
  }

  public static DatabaseVersion findByVersion(String version) {
    if (StringUtils.isBlank(version)) {
      return null;
    }
    return (DatabaseVersion) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("version = ?", version),
        DatabaseVersionRepository::buildRecord);
  }

  public static List<DatabaseVersion> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy(PRIMARY_KEY[0]),
        DatabaseVersionRepository::buildRecord);
    return (List<DatabaseVersion>) result.getRecords();
  }

  public static DatabaseVersion save(DatabaseVersion record) {
    return add(record);
  }

  private static DatabaseVersion add(DatabaseVersion record) {
    SqlUtils insertValues = new SqlUtils()
        .add("file", StringUtils.trimToNull(record.getFile()))
        .add("version", StringUtils.trimToNull(record.getVersion()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static DatabaseVersion buildRecord(ResultSet rs) {
    try {
      DatabaseVersion record = new DatabaseVersion();
      record.setId(rs.getInt("version_id"));
      record.setVersion(rs.getString("version"));
      record.setFile(rs.getString("file"));
      record.setInstalled(rs.getTimestamp("installed"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
