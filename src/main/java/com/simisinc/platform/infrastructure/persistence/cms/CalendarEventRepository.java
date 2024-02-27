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
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves calendar event objects
 *
 * @author matt rajkowski
 * @created 10/29/18 1:27 PM
 */
public class CalendarEventRepository {

  private static Log LOG = LogFactory.getLog(CalendarEventRepository.class);

  private static String TABLE_NAME = "calendar_events";
  private static String[] PRIMARY_KEY = new String[]{"event_id"};

  private static SqlUtils createWhereStatement(CalendarEventSpecification specification) {
    SqlUtils where = new SqlUtils();
    if (specification != null) {
      where
          .addIfExists("event_id = ?", specification.getId(), -1)
          .addIfExists("calendar_id = ?", specification.getCalendarId(), -1)
          .addIfExists("event_unique_id = ?", specification.getUniqueId());
      if (specification.getPublishedOnly() != DataConstants.UNDEFINED) {
        if (specification.getPublishedOnly() == DataConstants.TRUE) {
          where.add("published IS NOT NULL");
        } else {
          where.add("published IS NULL");
        }
      }
      if (specification.getStartingDateRange() != null && specification.getEndingDateRange() != null) {
        where.add("((start_date >= ? AND start_date < ?) OR (end_date >= ? AND end_date < ?))",
            new Timestamp[]{specification.getStartingDateRange(), specification.getEndingDateRange(), specification.getStartingDateRange(), specification.getEndingDateRange()});
      } else if (specification.getStartingDateRange() != null) {
        where.add("start_date >= ?",
            new Timestamp[]{specification.getStartingDateRange()});
      } else if (specification.getEndingDateRange() != null) {
        where.add("(start_date < ? AND end_date < ?)",
            new Timestamp[]{specification.getEndingDateRange(), specification.getEndingDateRange()});
      }
    }
    return where;
  }

  private static DataResult query(CalendarEventSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = createWhereStatement(specification);
    SqlUtils orderBy = null;

    if (specification != null) {
      if (StringUtils.isNotBlank(specification.getSearchTerm())) {
        select.add("ts_rank_cd(tsv, websearch_to_tsquery('title_stem', ?)) AS rank", specification.getSearchTerm().trim());
        where.add("tsv @@ websearch_to_tsquery('title_stem', ?)", specification.getSearchTerm().trim());
        // Override the order by for rank first
        orderBy = new SqlUtils();
        if (specification.getStartingDateRange() != null) {
          // Show next occurring first
          orderBy.add("rank DESC, start_date");
        } else {
          // Show the future ones first
          orderBy.add("rank DESC, start_date DESC");
        }
      }
    }

    return DB.selectAllFrom(TABLE_NAME, select, where, orderBy, constraints, CalendarEventRepository::buildRecord);
  }

  public static CalendarEvent findByUniqueId(Long calendarId, String eventUniqueId) {
    if (StringUtils.isBlank(eventUniqueId)) {
      return null;
    }
    return (CalendarEvent) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("calendar_id = ?", calendarId)
            .add("event_unique_id = ?", eventUniqueId),
        CalendarEventRepository::buildRecord);
  }

  public static CalendarEvent findByUniqueId(String eventUniqueId) {
    if (StringUtils.isBlank(eventUniqueId)) {
      return null;
    }
    return (CalendarEvent) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("event_unique_id = ?", eventUniqueId),
        CalendarEventRepository::buildRecord);
  }

  public static CalendarEvent findById(Long calendarEventId) {
    if (calendarEventId == -1) {
      return null;
    }
    return (CalendarEvent) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("event_id = ?", calendarEventId),
        CalendarEventRepository::buildRecord);
  }

  public static List<CalendarEvent> findAll() {
    return findAll(null, null);
  }

  public static List<CalendarEvent> findAll(CalendarEventSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("start_date");
    DataResult result = query(specification, constraints);
    return (List<CalendarEvent>) result.getRecords();
  }

  public static long findCount(CalendarEventSpecification specification) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static CalendarEvent save(CalendarEvent record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static CalendarEvent add(CalendarEvent record) {
    SqlUtils insertValues = new SqlUtils()
        .add("calendar_id", record.getCalendarId())
        .add("event_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("all_day", record.getAllDay())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("details_url", StringUtils.trimToNull(record.getDetailsUrl()))
        .add("sign_up_url", StringUtils.trimToNull(record.getSignUpUrl()))
        .add("location_name", StringUtils.trimToNull(record.getLocation()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("published", record.getPublished())
        .add("archived", record.getArchived());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static CalendarEvent update(CalendarEvent record) {
    SqlUtils updateValues = new SqlUtils()
        .add("calendar_id", record.getCalendarId())
        .add("event_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("all_day", record.getAllDay())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("details_url", StringUtils.trimToNull(record.getDetailsUrl()))
        .add("sign_up_url", StringUtils.trimToNull(record.getSignUpUrl()))
        .add("location_name", StringUtils.trimToNull(record.getLocation()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("published", record.getPublished())
        .add("archived", record.getArchived());
    SqlUtils where = new SqlUtils()
        .add("event_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(CalendarEvent record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
//        ItemCategoryRepository.removeAll(connection, record);
//        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
//        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("event_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Calendar calendar) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("calendar_id = ?", calendar.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  private static CalendarEvent buildRecord(ResultSet rs) {
    try {
      CalendarEvent record = new CalendarEvent();
      record.setId(rs.getLong("event_id"));
      record.setCalendarId(rs.getLong("calendar_id"));
      record.setUniqueId(rs.getString("event_unique_id"));
      record.setTitle(rs.getString("title"));
      record.setBody(rs.getString("body"));
      record.setSummary(rs.getString("summary"));
      record.setAllDay(rs.getBoolean("all_day"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      record.setDetailsUrl(rs.getString("details_url"));
      record.setSignUpUrl(rs.getString("sign_up_url"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setPublished(rs.getTimestamp("published"));
      record.setArchived(rs.getTimestamp("archived"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setLocation(rs.getString("location_name"));
      record.setStreet(rs.getString("street"));
      record.setAddressLine2(rs.getString("address_line_2"));
      record.setAddressLine3(rs.getString("address_line_3"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setCountry(rs.getString("country"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setCounty(rs.getString("county"));
      record.setImageUrl(rs.getString("image_url"));
      record.setVideoUrl(rs.getString("video_url"));
      record.setVideoEmbed(rs.getString("video_embed"));
      record.setScriptEmbed(rs.getString("script_embed"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
