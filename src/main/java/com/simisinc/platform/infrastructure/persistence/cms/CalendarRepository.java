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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/29/18 2:02 PM
 */
public class CalendarRepository {

  private static Log LOG = LogFactory.getLog(CalendarRepository.class);

  private static String TABLE_NAME = "calendars";
  private static String PRIMARY_KEY[] = new String[]{"calendar_id"};

  private static DataResult query(CalendarSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("calendar_id = ?", specification.getId(), -1)
          .addIfExists("calendar_unique_id = ?", specification.getUniqueId());
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, CalendarRepository::buildRecord);
  }

  public static Calendar findById(long calendarId) {
    if (calendarId == -1) {
      return null;
    }
    return (Calendar) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("calendar_id = ?", calendarId),
        CalendarRepository::buildRecord);
  }

  public static Calendar findByUniqueId(String calendarUniqueId) {
    if (StringUtils.isBlank(calendarUniqueId)) {
      return null;
    }
    return (Calendar) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("calendar_unique_id = ?", calendarUniqueId),
        CalendarRepository::buildRecord);
  }

  public static List<Calendar> findAll() {
    return findAll(null, null);
  }

  public static List<Calendar> findAll(CalendarSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("calendar_id");
    DataResult result = query(specification, constraints);
    return (List<Calendar>) result.getRecords();
  }

  public static Calendar save(Calendar record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Calendar add(Calendar record) {
    SqlUtils insertValues = new SqlUtils()
        .add("calendar_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("color", StringUtils.trimToNull(record.getColor()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("enabled", record.getEnabled());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Calendar update(Calendar record) {
    SqlUtils updateValues = new SqlUtils()
        .add("calendar_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("color", StringUtils.trimToNull(record.getColor()))
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("calendar_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Calendar record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        CalendarEventRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("calendar_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static Calendar buildRecord(ResultSet rs) {
    try {
      Calendar record = new Calendar();
      record.setId(rs.getLong("calendar_id"));
      record.setUniqueId(rs.getString("calendar_unique_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setColor(rs.getString("color"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
