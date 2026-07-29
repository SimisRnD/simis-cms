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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves social media link objects (issue #516)
 *
 * @author SimIS Inc.
 */
public class SocialMediaLinkRepository {

  private static Log LOG = LogFactory.getLog(SocialMediaLinkRepository.class);

  private static String TABLE_NAME = "social_media_links";
  private static String[] PRIMARY_KEY = new String[]{"social_media_link_id"};

  private static DataResult query(DataConstraints constraints) {
    return DB.selectAllFrom(TABLE_NAME, null, constraints, SocialMediaLinkRepository::buildRecord);
  }

  public static List<SocialMediaLink> findAll() {
    DataConstraints constraints = new DataConstraints();
    constraints.setDefaultColumnToSortBy("link_order, platform_name");
    DataResult result = query(constraints);
    return (List<SocialMediaLink>) result.getRecords();
  }

  public static SocialMediaLink findById(long id) {
    if (id == -1) {
      return null;
    }
    return (SocialMediaLink) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("social_media_link_id = ?", id),
        SocialMediaLinkRepository::buildRecord);
  }

  public static SocialMediaLink save(SocialMediaLink record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static SocialMediaLink add(SocialMediaLink record) {
    SqlUtils insertValues = new SqlUtils()
        .add("platform_name", StringUtils.trimToNull(record.getPlatformName()))
        .add("url", StringUtils.trimToNull(record.getUrl()))
        .add("link_order", record.getLinkOrder());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static SocialMediaLink update(SocialMediaLink record) {
    SqlUtils updateValues = new SqlUtils()
        .add("platform_name", StringUtils.trimToNull(record.getPlatformName()))
        .add("url", StringUtils.trimToNull(record.getUrl()))
        .add("link_order", record.getLinkOrder());
    SqlUtils where = new SqlUtils()
        .add("social_media_link_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(SocialMediaLink record) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("social_media_link_id = ?", record.getId())) > 0;
  }

  private static SocialMediaLink buildRecord(ResultSet rs) {
    try {
      SocialMediaLink record = new SocialMediaLink();
      record.setId(rs.getLong("social_media_link_id"));
      record.setPlatformName(rs.getString("platform_name"));
      record.setUrl(rs.getString("url"));
      record.setLinkOrder(rs.getInt("link_order"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
