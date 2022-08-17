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

import com.simisinc.platform.application.CustomFieldListJSONCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.UserProfile;
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
 * Persists and retrieves user profile objects
 *
 * @author matt rajkowski
 * @created 7/17/22 8:08 AM
 */
public class UserProfileRepository {

  private static Log LOG = LogFactory.getLog(UserProfileRepository.class);

  private static String TABLE_NAME = "users";
  private static String[] PRIMARY_KEY = new String[]{"user_id"};

  private static DataResult query(UserSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, UserProfileRepository::buildRecord);
  }

  public static UserProfile findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (UserProfile) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("unique_id = ?", uniqueId),
        UserProfileRepository::buildRecord);
  }

  public static UserProfile findByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    return (UserProfile) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("user_id = ?", userId),
        UserProfileRepository::buildRecord);
  }

  public static List<UserProfile> findAll(UserSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("first_name, last_name");
    DataResult result = query(specification, constraints);
    return (List<UserProfile>) result.getRecords();
  }

  public static UserProfile save(UserProfile record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return null;
  }

  private static UserProfile update(UserProfile record) {
    SqlUtils updateValues = new SqlUtils()
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("first_name", StringUtils.trimToNull(record.getFirstName()))
        .add("last_name", StringUtils.trimToNull(record.getLastName()))
        .add("organization", StringUtils.trimToNull(record.getOrganization()))
        .add("nickname", StringUtils.trimToNull(record.getNickname()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("description_text", HtmlCommand.text(StringUtils.trimToNull(record.getDescription())))
        .add("email", StringUtils.trimToNull(record.getEmail()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("department", StringUtils.trimToNull(record.getDepartment()))
        .add("timezone", StringUtils.trimToNull(record.getTimeZone()))
        .add("city", StringUtils.trimToNull(record.getCity()))
        .add("state", StringUtils.trimToNull(record.getState()))
        .add("country", StringUtils.trimToNull(record.getCountry()))
        .add("postal_code", StringUtils.trimToNull(record.getPostalCode()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("video_url", StringUtils.trimToNull(record.getVideoUrl()))
        .add("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    if (record.hasGeoPoint()) {
      updateValues.add("latitude", record.getLatitude());
      updateValues.add("longitude", record.getLongitude());
      updateValues.addGeomPoint("geom", record.getLatitude(), record.getLongitude());
    } else {
      updateValues.add("latitude", 0L, 0L);
      updateValues.add("longitude", 0L, 0L);
      updateValues.addGeomPoint("geom", 0, 0);
    }
    // Handle custom fields
    if (record.getCustomFieldList() != null && !record.getCustomFieldList().isEmpty()) {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, CustomFieldListJSONCommand.createJSONString(record.getCustomFieldList())));
    } else {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, null));
    }
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  private static UserProfile buildRecord(ResultSet rs) {
    try {
      UserProfile record = new UserProfile();
      record.setId(rs.getLong("user_id"));
      record.setUniqueId(rs.getString("unique_id"));
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setOrganization(rs.getString("organization"));
      record.setNickname(rs.getString("nickname"));
      record.setEmail(rs.getString("email"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setTitle(rs.getString("title"));
      record.setDepartment(rs.getString("department"));
      record.setTimeZone(rs.getString("timezone"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setCountry(rs.getString("country"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setDescription(rs.getString("description"));
      record.setImageUrl(rs.getString("image_url"));
      record.setVideoUrl(rs.getString("video_url"));
      record.setCustomFieldList(CustomFieldListJSONCommand.populateFromJSONString(rs.getString("field_values")));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
