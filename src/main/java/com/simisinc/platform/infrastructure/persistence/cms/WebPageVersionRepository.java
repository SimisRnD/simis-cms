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

import com.simisinc.platform.domain.model.cms.WebPageVersion;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves web page version snapshots (#405) -- each row is the page_xml that was
 * overwritten by a publish, so a prior published state can be listed, viewed, or restored.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class WebPageVersionRepository {

  private static Log LOG = LogFactory.getLog(WebPageVersionRepository.class);

  private static String TABLE_NAME = "web_page_versions";
  private static String[] PRIMARY_KEY = new String[]{"web_page_version_id"};

  public static WebPageVersion findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebPageVersion) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("web_page_version_id = ?", id),
        WebPageVersionRepository::buildRecord);
  }

  public static List<WebPageVersion> findByWebPageId(long webPageId, DataConstraints constraints) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("web_page_id = ?", webPageId),
        (constraints != null ? constraints : new DataConstraints()).setDefaultColumnToSortBy("published_at DESC"),
        WebPageVersionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<WebPageVersion>) result.getRecords();
    }
    return null;
  }

  /** Inserts a version row within the caller's transaction. Returns the new id, or -1 on failure. */
  public static long insert(Connection connection, WebPageVersion record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("web_page_id", record.getWebPageId())
        .add("page_xml", record.getPageXml())
        .add("published_by", record.getPublishedBy(), -1)
        .add("label", record.getLabel());
    long id = DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    if (id == -1) {
      LOG.error("A web page version was not saved");
    }
    return id;
  }

  /**
   * Deletes all but the {@code keepCount} most recent versions for the page, within the caller's
   * transaction. Called right after {@link #insert}, so the just-inserted row is included in the
   * kept set as long as {@code keepCount >= 1}.
   */
  public static void pruneOldest(Connection connection, long webPageId, int keepCount) {
    String sql = "DELETE FROM " + TABLE_NAME + " WHERE web_page_id = ? AND web_page_version_id NOT IN ("
        + "SELECT web_page_version_id FROM " + TABLE_NAME + " WHERE web_page_id = ? "
        + "ORDER BY published_at DESC, web_page_version_id DESC LIMIT ?)";
    try (PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setLong(1, webPageId);
      pst.setLong(2, webPageId);
      pst.setInt(3, Math.max(keepCount, 0));
      pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
  }

  private static WebPageVersion buildRecord(ResultSet rs) {
    try {
      WebPageVersion record = new WebPageVersion();
      record.setId(rs.getLong("web_page_version_id"));
      record.setWebPageId(rs.getLong("web_page_id"));
      record.setPageXml(rs.getString("page_xml"));
      record.setPublishedBy(DB.getLong(rs, "published_by", -1));
      Timestamp publishedAt = rs.getTimestamp("published_at");
      record.setPublishedAt(publishedAt);
      record.setLabel(rs.getString("label"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
