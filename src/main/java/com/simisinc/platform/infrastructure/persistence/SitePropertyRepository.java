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

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves site property objects
 *
 * @author matt rajkowski
 * @created 4/18/18 4:26 PM
 */
public class SitePropertyRepository {

  private static Log LOG = LogFactory.getLog(SitePropertyRepository.class);

  private static String TABLE_NAME = "site_properties";
  private static String[] PRIMARY_KEY = new String[]{"property_id"};


  public static SiteProperty findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (SiteProperty) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("property_name = ?", name),
        SitePropertyRepository::buildRecord);
  }

  public static List<SiteProperty> findAllByPrefix(String prefix) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("property_name LIKE ?", prefix + ".%"),
        new DataConstraints().setDefaultColumnToSortBy("property_order, property_name").setUseCount(false),
        SitePropertyRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<SiteProperty>) result.getRecords();
    }
    return null;
  }

  public static List<SiteProperty> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("property_id"),
        SitePropertyRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<SiteProperty>) result.getRecords();
    }
    return null;
  }

  private static PreparedStatement createPreparedStatementForUpdate(Connection connection, SiteProperty record) throws SQLException {
    String SQL_QUERY =
        "UPDATE site_properties " +
            "SET property_value = ? " +
            "WHERE property_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setString(++i, StringUtils.trimToEmpty(record.getValue()));
    pst.setInt(++i, record.getId());
    return pst;
  }

  public static SiteProperty save(SiteProperty record) {
    try {
      try (Connection connection = DB.getConnection();
           PreparedStatement pst = createPreparedStatementForUpdate(connection, record)) {
        if (pst.executeUpdate() > 0) {
          return record;
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  public static boolean saveAll(String prefix, List<SiteProperty> sitePropertyList) {
    // Save the validated entries
    for (SiteProperty siteProperty : sitePropertyList) {
      // Check the property type
      if ("disabled".equals(siteProperty.getType())) {
        // The system rule is to skip disabled properties
        continue;
      }
      // Update the property
      SiteProperty updated = SitePropertyRepository.save(siteProperty);
      if (updated == null) {
        return false;
      }
    }
    // Expire the cache for the prefixes
    String[] prefixList = prefix.split(",");
    for (String thisPrefix : prefixList) {
      // The cache is at the root level of the prefix
      if (thisPrefix.contains(".")) {
        thisPrefix = thisPrefix.substring(0, thisPrefix.indexOf("."));
      }
      LOG.debug("Resetting prefix: " + thisPrefix);
      CacheManager.invalidateKey(CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE, thisPrefix);
    }
    return true;
  }

  private static SiteProperty buildRecord(ResultSet rs) {
    try {
      SiteProperty record = new SiteProperty();
      record.setId(rs.getInt("property_id"));
      record.setLabel(rs.getString("property_label"));
      record.setName(rs.getString("property_name"));
      record.setValue(rs.getString("property_value"));
      record.setType(rs.getString("property_type"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
