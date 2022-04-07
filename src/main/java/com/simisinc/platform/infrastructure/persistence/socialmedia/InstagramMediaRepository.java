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

package com.simisinc.platform.infrastructure.persistence.socialmedia;

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/15/19 9:15 AM
 */
public class InstagramMediaRepository {

  private static Log LOG = LogFactory.getLog(InstagramMediaRepository.class);

  private static String TABLE_NAME = "instagram_media";
  private static String PRIMARY_KEY[] = new String[]{"id"};

  public static InstagramMedia findByGraphId(String graphId) {
    if (StringUtils.isBlank(graphId)) {
      return null;
    }
    return (InstagramMedia) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("graph_id = ?", graphId),
        InstagramMediaRepository::buildRecord);
  }

  public static List<InstagramMedia> findAll() {
    return findAll(null, null);
  }

  public static List<InstagramMedia> findAll(InstagramMediaSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("media_type = ?", specification.getMediaType());
    }
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, constraints, InstagramMediaRepository::buildRecord);
    return (List<InstagramMedia>) result.getRecords();
  }

  public static InstagramMedia save(InstagramMedia record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static InstagramMedia add(InstagramMedia record) {
    SqlUtils insertValues = new SqlUtils()
        .add("graph_id", record.getGraphId())
        .add("permalink", StringUtils.trimToNull(record.getPermalink()))
        .add("media_type", StringUtils.trimToNull(record.getMediaType()))
        .add("media_url", StringUtils.trimToNull(record.getMediaUrl()))
        .add("caption", HtmlCommand.text(StringUtils.trimToNull(record.getCaption())))
        .add("short_code", StringUtils.trimToNull(record.getShortCode()))
        .add("timestamp", StringUtils.trimToNull(record.getTimestamp()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static InstagramMedia update(InstagramMedia record) {
    SqlUtils updateValues = new SqlUtils()
        .add("graph_id", record.getGraphId())
        .add("permalink", StringUtils.trimToNull(record.getPermalink()))
        .add("media_type", StringUtils.trimToNull(record.getMediaType()))
        .add("media_url", StringUtils.trimToNull(record.getMediaUrl()))
        .add("caption", HtmlCommand.text(StringUtils.trimToNull(record.getCaption())))
        .add("short_code", StringUtils.trimToNull(record.getShortCode()))
        .add("timestamp", StringUtils.trimToNull(record.getTimestamp()));
    SqlUtils where = new SqlUtils()
        .add("id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(InstagramMedia record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
//        ItemCategoryRepository.removeAll(connection, record);
//        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
//        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static InstagramMedia buildRecord(ResultSet rs) {
    try {
      InstagramMedia record = new InstagramMedia();
      record.setId(rs.getLong("id"));
      record.setGraphId(rs.getString("graph_id"));
      record.setPermalink(rs.getString("permalink"));
      record.setMediaType(rs.getString("media_type"));
      record.setMediaUrl(rs.getString("media_url"));
      record.setCaption(rs.getString("caption"));
      record.setShortCode(rs.getString("short_code"));
      record.setTimestamp(rs.getString("timestamp"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
