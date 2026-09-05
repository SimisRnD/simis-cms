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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.FileDownload;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and queries the per-download record behind the Downloads report.
 *
 * @author SimIS Inc.
 */
public class FileDownloadRepository {

  private static Log LOG = LogFactory.getLog(FileDownloadRepository.class);

  private static String TABLE_NAME = "file_downloads";
  private static String PRIMARY_KEY[] = new String[] { "file_download_id" };

  /**
   * A download is not worth failing a download over: this returns false and logs rather than
   * throwing, so a reporting write can never stop the file itself being delivered.
   */
  public static boolean save(FileDownload record) {
    SqlUtils insertValues = new SqlUtils()
        .add("file_id", record.getFileId())
        .add("version_id", record.getVersionId(), -1)
        .add("download_by", record.getDownloadBy(), -1)
        .add("session_id", record.getSessionId());
    try {
      return DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY) > -1;
    } catch (Exception e) {
      LOG.error("The file download could not be recorded: " + e.getMessage());
      return false;
    }
  }

  /**
   * The most-downloaded files over a window, labelled with the filename an admin would recognise
   * rather than the numeric id.
   *
   * <p>Bots are excluded the same way every other report on the Content Analytics page excludes
   * them -- by the session's is_bot flag -- because that page states up front that every number on
   * it already has crawlers filtered out. A download with no session at all is kept: it is not
   * known to be a bot, and dropping it would quietly under-count.
   *
   * <p>Joined to files rather than reading the path, so a renamed file reports under its current
   * name; a download of a file that has since been deleted drops out of the report entirely, which
   * is the same behaviour the folder listings have.
   */
  public static List<StatisticsData> findTopDownloads(int value, char intervalType, int recordLimit) {
    String SQL_QUERY =
        "SELECT f.filename, count(fd.file_download_id) AS download_count " +
            "FROM file_downloads fd " +
            "JOIN files f ON (f.file_id = fd.file_id) " +
            "WHERE fd.download_date > NOW() - INTERVAL '" + value + " " + DB.intervalUnit(intervalType) + "' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = fd.session_id AND is_bot = TRUE) " +
            "GROUP BY f.filename " +
            "ORDER BY download_count desc " +
            "LIMIT " + recordLimit;
    return query(SQL_QUERY);
  }

  private static List<StatisticsData> query(String sql) {
    List<StatisticsData> records = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql);
        ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString(1));
        data.setValue(String.valueOf(rs.getLong(2)));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Prunes download history on the same analytics.retentionDays window that governs web_page_hits,
   * reusing that repository's parser so the two windows cannot drift apart. Called from
   * WebPageHitsCleanupJob rather than a job of its own, which keeps both prunes under one nightly
   * distributed lock and makes it obvious they are meant to stay in step.
   */
  public static int deleteOldDownloads() {
    int days = WebPageHitRepository
        .resolveRetentionDays(LoadSitePropertyCommand.loadByName("analytics.retentionDays"));
    return deleteOlderThan(days);
  }

  /**
   * Removes download history older than the given number of days. Returns the number of rows
   * removed. The value is an int before it reaches the interval, so it cannot inject SQL.
   */
  static int deleteOlderThan(int days) {
    String SQL_QUERY = "DELETE FROM file_downloads WHERE download_date < NOW() - INTERVAL '" + days + " days'";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return 0;
    }
  }
}
