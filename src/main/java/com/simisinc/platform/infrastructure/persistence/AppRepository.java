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
 * Description
 *
 * @author matt rajkowski
 * @created 4/17/18 7:48 PM
 */
public class AppRepository {

  private static Log LOG = LogFactory.getLog(AppRepository.class);

  private static String TABLE_NAME = "apps";
  private static String PRIMARY_KEY[] = new String[]{"app_id"};

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
        .add("private_key", record.getPrivateKey())
        .add("created_by", record.getCreatedBy());
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
        .add("summary", StringUtils.trimToNull(record.getSummary()));
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
      record.setPrivateKey(rs.getString("private_key"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
