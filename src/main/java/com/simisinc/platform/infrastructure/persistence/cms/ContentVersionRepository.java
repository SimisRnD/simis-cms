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

package com.simisinc.platform.infrastructure.persistence.cms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.ContentVersion;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves content block version snapshots (#406) -- each row is the content that was
 * overwritten by a publish, so a prior published state can be listed, diffed, or restored.
 *
 * @author elizabeth houser
 */
public class ContentVersionRepository {

  private static Log LOG = LogFactory.getLog(ContentVersionRepository.class);

  private static String TABLE_NAME = "content_versions";
  private static String[] PRIMARY_KEY = new String[]{"content_version_id"};

  public static ContentVersion findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ContentVersion) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("content_version_id = ?", id),
        ContentVersionRepository::buildRecord);
  }

  public static List<ContentVersion> findByContentId(long contentId, DataConstraints constraints) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("content_id = ?", contentId),
        (constraints != null ? constraints : new DataConstraints()).setDefaultColumnToSortBy("published_at DESC"),
        ContentVersionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ContentVersion>) result.getRecords();
    }
    return null;
  }

  /** Inserts a version row within the caller's transaction. Returns the new id, or -1 on failure. */
  public static long insert(Connection connection, ContentVersion record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("content_id", record.getContentId())
        .add("content", record.getContent())
        .add("approved_by", record.getApprovedBy(), -1)
        .add("release_reference", record.getReleaseReference());
    long id = DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    if (id == -1) {
      LOG.error("A content version was not saved");
    }
    return id;
  }

  /**
   * Deletes all but the {@code keepCount} most recent versions for the content block, within the
   * caller's transaction. Called right after {@link #insert}, so the just-inserted row is included in
   * the kept set as long as {@code keepCount >= 1}.
   */
  public static void pruneOldest(Connection connection, long contentId, int keepCount) {
    String sql = "DELETE FROM " + TABLE_NAME + " WHERE content_id = ? AND content_version_id NOT IN ("
        + "SELECT content_version_id FROM " + TABLE_NAME + " WHERE content_id = ? "
        + "ORDER BY published_at DESC, content_version_id DESC LIMIT ?)";
    try (PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setLong(1, contentId);
      pst.setLong(2, contentId);
      pst.setInt(3, Math.max(keepCount, 0));
      pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
  }

  private static ContentVersion buildRecord(ResultSet rs) {
    try {
      ContentVersion record = new ContentVersion();
      record.setId(rs.getLong("content_version_id"));
      record.setContentId(rs.getLong("content_id"));
      record.setContent(rs.getString("content"));
      record.setApprovedBy(DB.getLong(rs, "approved_by", -1));
      Timestamp publishedAt = rs.getTimestamp("published_at");
      record.setPublishedAt(publishedAt);
      record.setReleaseReference(rs.getString("release_reference"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
