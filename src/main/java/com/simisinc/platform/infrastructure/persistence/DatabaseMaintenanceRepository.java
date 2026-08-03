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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;

/**
 * Read-only introspection of PostgreSQL's own statistics catalogs (pg_stat_user_tables,
 * pg_stat_user_indexes, pg_stat_activity), plus a guarded VACUUM (ANALYZE) trigger, for the
 * admin-facing database maintenance dashboard (issue #469).
 *
 * <p>
 * Deliberately scoped to what's queryable with no extra Postgres extension and no locking risk:
 * REINDEX, query-plan/slow-query analysis (needs pg_stat_statements), and bloat estimation (needs
 * pgstattuple or heavy heuristics) are left for a follow-up.
 * </p>
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class DatabaseMaintenanceRepository {

  private static Log LOG = LogFactory.getLog(DatabaseMaintenanceRepository.class);

  public static DatabaseOverview findOverview() {
    String sql = "SELECT pg_database_size(current_database()) AS db_size, "
        + "pg_size_pretty(pg_database_size(current_database())) AS db_size_pretty, "
        + "(SELECT COUNT(*) FROM pg_stat_user_tables) AS table_count, "
        + "(SELECT COUNT(*) FROM pg_stat_user_indexes) AS index_count";
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      if (rs.next()) {
        return new DatabaseOverview(
            rs.getLong("db_size"),
            rs.getString("db_size_pretty"),
            rs.getInt("table_count"),
            rs.getInt("index_count"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  /** Ordered largest-first; a page with heavy churn but small size is still useful to see, so no filtering. */
  public static List<TableStats> findTableStats() {
    String sql = "SELECT relname, n_live_tup, n_dead_tup, "
        + "pg_total_relation_size(relid) AS total_size, pg_size_pretty(pg_total_relation_size(relid)) AS total_size_pretty, "
        + "last_vacuum, last_autovacuum, last_analyze, last_autoanalyze "
        + "FROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC";
    List<TableStats> results = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        results.add(new TableStats(
            rs.getString("relname"),
            rs.getLong("n_live_tup"),
            rs.getLong("n_dead_tup"),
            rs.getLong("total_size"),
            rs.getString("total_size_pretty"),
            rs.getTimestamp("last_vacuum"),
            rs.getTimestamp("last_autovacuum"),
            rs.getTimestamp("last_analyze"),
            rs.getTimestamp("last_autoanalyze")));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return results;
  }

  /** Ordered least-used first, so unused/rarely-used indexes surface at the top. */
  public static List<IndexStats> findIndexStats() {
    String sql = "SELECT indexrelname, relname AS table_name, idx_scan, "
        + "pg_relation_size(indexrelid) AS index_size, pg_size_pretty(pg_relation_size(indexrelid)) AS index_size_pretty "
        + "FROM pg_stat_user_indexes ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC";
    List<IndexStats> results = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        results.add(new IndexStats(
            rs.getString("indexrelname"),
            rs.getString("table_name"),
            rs.getLong("idx_scan"),
            rs.getLong("index_size"),
            rs.getString("index_size_pretty")));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return results;
  }

  /** Excludes this monitoring connection's own idle/active query and other idle connections -- noise, not activity. */
  public static List<ActiveQuery> findActiveQueries() {
    String sql = "SELECT pid, state, query_start, wait_event_type, application_name, LEFT(query, 500) AS query "
        + "FROM pg_stat_activity "
        + "WHERE datname = current_database() AND state IS DISTINCT FROM 'idle' AND pid != pg_backend_pid() "
        + "ORDER BY query_start ASC NULLS LAST";
    List<ActiveQuery> results = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        results.add(new ActiveQuery(
            rs.getInt("pid"),
            rs.getString("state"),
            rs.getTimestamp("query_start"),
            rs.getString("wait_event_type"),
            rs.getString("application_name"),
            rs.getString("query")));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return results;
  }

  /** The current set of user table names -- used to validate a requested VACUUM target before it's interpolated into SQL. */
  public static Set<String> findTableNames() {
    Set<String> names = new HashSet<>();
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT relname FROM pg_stat_user_tables")) {
      while (rs.next()) {
        names.add(rs.getString("relname"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return names;
  }

  /**
   * Runs VACUUM (ANALYZE) on a single table -- non-locking, safe to run against a live table
   * (unlike VACUUM FULL, which isn't offered here). {@code tableName} MUST already be verified
   * against {@link #findTableNames()} by the caller: VACUUM's target can't be a bind parameter,
   * so it is quoted as an identifier and executed as-is.
   */
  public static boolean vacuumAnalyzeTable(String tableName) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("VACUUM (ANALYZE) \"" + tableName.replace("\"", "\"\"") + "\"");
      return true;
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return false;
    }
  }

  public static class DatabaseOverview {
    private final long sizeBytes;
    private final String sizePretty;
    private final int tableCount;
    private final int indexCount;

    public DatabaseOverview(long sizeBytes, String sizePretty, int tableCount, int indexCount) {
      this.sizeBytes = sizeBytes;
      this.sizePretty = sizePretty;
      this.tableCount = tableCount;
      this.indexCount = indexCount;
    }

    public long getSizeBytes() {
      return sizeBytes;
    }

    public String getSizePretty() {
      return sizePretty;
    }

    public int getTableCount() {
      return tableCount;
    }

    public int getIndexCount() {
      return indexCount;
    }
  }

  public static class TableStats {
    private final String tableName;
    private final long liveRowEstimate;
    private final long deadRowEstimate;
    private final long totalSizeBytes;
    private final String totalSizePretty;
    private final Timestamp lastVacuum;
    private final Timestamp lastAutovacuum;
    private final Timestamp lastAnalyze;
    private final Timestamp lastAutoanalyze;

    public TableStats(String tableName, long liveRowEstimate, long deadRowEstimate, long totalSizeBytes,
        String totalSizePretty, Timestamp lastVacuum, Timestamp lastAutovacuum, Timestamp lastAnalyze,
        Timestamp lastAutoanalyze) {
      this.tableName = tableName;
      this.liveRowEstimate = liveRowEstimate;
      this.deadRowEstimate = deadRowEstimate;
      this.totalSizeBytes = totalSizeBytes;
      this.totalSizePretty = totalSizePretty;
      this.lastVacuum = lastVacuum;
      this.lastAutovacuum = lastAutovacuum;
      this.lastAnalyze = lastAnalyze;
      this.lastAutoanalyze = lastAutoanalyze;
    }

    public String getTableName() {
      return tableName;
    }

    public long getLiveRowEstimate() {
      return liveRowEstimate;
    }

    public long getDeadRowEstimate() {
      return deadRowEstimate;
    }

    public long getTotalSizeBytes() {
      return totalSizeBytes;
    }

    public String getTotalSizePretty() {
      return totalSizePretty;
    }

    /** The most recent of last_vacuum/last_autovacuum, or null if neither has ever run. */
    public Timestamp getLastVacuumAny() {
      if (lastVacuum == null) {
        return lastAutovacuum;
      }
      if (lastAutovacuum == null) {
        return lastVacuum;
      }
      return lastVacuum.after(lastAutovacuum) ? lastVacuum : lastAutovacuum;
    }

    /** The most recent of last_analyze/last_autoanalyze, or null if neither has ever run. */
    public Timestamp getLastAnalyzeAny() {
      if (lastAnalyze == null) {
        return lastAutoanalyze;
      }
      if (lastAutoanalyze == null) {
        return lastAnalyze;
      }
      return lastAnalyze.after(lastAutoanalyze) ? lastAnalyze : lastAutoanalyze;
    }
  }

  public static class IndexStats {
    private final String indexName;
    private final String tableName;
    private final long scanCount;
    private final long sizeBytes;
    private final String sizePretty;

    public IndexStats(String indexName, String tableName, long scanCount, long sizeBytes, String sizePretty) {
      this.indexName = indexName;
      this.tableName = tableName;
      this.scanCount = scanCount;
      this.sizeBytes = sizeBytes;
      this.sizePretty = sizePretty;
    }

    public String getIndexName() {
      return indexName;
    }

    public String getTableName() {
      return tableName;
    }

    public long getScanCount() {
      return scanCount;
    }

    public long getSizeBytes() {
      return sizeBytes;
    }

    public String getSizePretty() {
      return sizePretty;
    }

    public boolean isUnused() {
      return scanCount == 0;
    }
  }

  public static class ActiveQuery {
    private final int pid;
    private final String state;
    private final Timestamp queryStart;
    private final String waitEventType;
    private final String applicationName;
    private final String query;

    public ActiveQuery(int pid, String state, Timestamp queryStart, String waitEventType, String applicationName,
        String query) {
      this.pid = pid;
      this.state = state;
      this.queryStart = queryStart;
      this.waitEventType = waitEventType;
      this.applicationName = applicationName;
      this.query = query;
    }

    public int getPid() {
      return pid;
    }

    public String getState() {
      return state;
    }

    public Timestamp getQueryStart() {
      return queryStart;
    }

    public String getWaitEventType() {
      return waitEventType;
    }

    public String getApplicationName() {
      return applicationName;
    }

    public String getQuery() {
      return query;
    }
  }
}
