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

package com.simisinc.platform.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression test for the web_vitals migration conflict: two Flyway upgrade migrations
 * (UPGRADE_20260726.2000 and the now-deleted UPGRADE_20260827.1000) both created a table named
 * web_vitals with incompatible schemas. Flyway applies migrations in version order, so the
 * second migration's CREATE TABLE IF NOT EXISTS silently no-opped -- but its CREATE INDEX
 * statements referenced columns (metric_name, recorded_at) that only existed in ITS OWN,
 * never-applied schema. Verified separately against a real Postgres: under Flyway's real
 * single-transaction-per-migration execution, that is not a harmless no-op -- the first bad
 * CREATE INDEX aborts the transaction, and the whole migration (including its otherwise-unrelated
 * web_vitals_aggregates table) rolls back and is recorded as a failed migration, which blocks all
 * subsequent upgrade migrations until manually repaired.
 *
 * <p>
 * The fix keeps UPGRADE_20260726.2000 (the original, reviewed migration) untouched and adds a new
 * expand-only migration, UPGRADE_20260727.1001, which ALTERs web_vitals to add the extra context
 * columns and creates web_vitals_aggregates. This test drives that pair through the exact Flyway
 * configuration {@link com.simisinc.platform.application.admin.DatabaseCommand} uses for upgrades
 * (same table, prefix, locations, outOfOrder, validateOnMigrate) against a real PostgreSQL
 * instance, baselined just before these two migrations so only they execute.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 7/27/26
 */
class WebVitalsMigrationTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  // Just below UPGRADE_20260726.2000, so baseline() marks nothing as pre-applied and both
  // web_vitals migrations execute for real, matching an existing (upgrade-path) database.
  private static final String BASELINE_BEFORE_WEB_VITALS = "20260726.1999";

  private static GenericContainer<?> postgres;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping the web_vitals migration test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    postgres.start();

    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    // Minimal stand-ins for tables referenced by migrations that fall in this baseline's range.
    // web_pages: referenced by FK from the web_vitals migrations under test. sessions: altered by
    // UPGRADE_20260727.1000__sessions_ip_address_nullable.sql, an unrelated migration that now
    // sorts between the baseline and the web_vitals migrations' versions -- Flyway applies
    // everything in range, not just the two under test, so it runs here too. Everything else in
    // the real install schema is irrelevant to this conflict.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, link VARCHAR(255))");
      statement.execute("CREATE TABLE sessions (session_id BIGSERIAL PRIMARY KEY, ip_address VARCHAR(64) NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the stand-in tables", se);
    }

    // Same Flyway configuration as DatabaseCommand.upgrade(), baselined just before the
    // two web_vitals migrations so they are the only ones that actually run.
    Flyway flyway = Flyway.configure()
        .table("flyway_history")
        .validateOnMigrate(false)
        .sqlMigrationPrefix("UPGRADE_")
        .repeatableSqlMigrationPrefix("REPEAT_")
        .dataSource(jdbcUrl, DB_USER, DB_PASSWORD)
        .locations("com/simisinc/platform/infrastructure/database/upgrade", "classpath:database/upgrade")
        .placeholderReplacement(false)
        .outOfOrder(true)
        .cleanDisabled(true)
        .baselineVersion(BASELINE_BEFORE_WEB_VITALS)
        .load();
    flyway.baseline();
    migrateResult = flyway.migrate();
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void bothMigrationsApplySuccessfully() {
    assertTrue(migrateResult.success,
        "web_vitals upgrade migrations did not apply cleanly: " + migrateResult.warnings);
    // Not migrationsExecuted == 2: this baseline range also legitimately covers
    // UPGRADE_20260727.1000__sessions_ip_address_nullable.sql, an unrelated migration that
    // happens to sort between the two under test (see the sessions stand-in table above) --
    // asserting a fixed total would break again the next time anything else lands in range.
    // Check that the two web_vitals versions specifically are both present instead.
    Set<String> appliedVersions = new TreeSet<>();
    for (Object migration : migrateResult.migrations) {
      appliedVersions.add(((org.flywaydb.core.api.output.MigrateOutput) migration).version);
    }
    assertTrue(appliedVersions.containsAll(Set.of("20260726.2000", "20260727.1001")),
        "expected both web_vitals migrations to run, got versions: " + appliedVersions);
  }

  @Test
  void webVitalsHasTheFullMergedColumnSet() throws SQLException {
    assertEquals(
        Set.of("id", "url", "metric_type", "value", "rating", "session_id", "created_at",
            "web_page_id", "user_agent_hash", "viewport_width", "connection_type"),
        columnsOf("web_vitals"));
  }

  @Test
  void webVitalsAggregatesExistsWithExpectedColumns() throws SQLException {
    assertEquals(
        Set.of("id", "url", "metric_type", "p50_value", "p75_value", "p95_value",
            "sample_count", "aggregated_at"),
        columnsOf("web_vitals_aggregates"));
  }

  @Test
  void webVitalsCollectorInsertShapeRoundTrips() throws SQLException {
    // Mirrors WebVitalsCollector.storeMetric()'s exact insert shape.
    SqlUtils insertValues = new SqlUtils()
        .add("url", "/news/article")
        .add("metric_type", "CLS")
        .add("value", 0.08)
        .add("rating", "good")
        .add("session_id", "abc123")
        .add("web_page_id", 1L)
        .add("user_agent_hash", "deadbeef")
        .add("viewport_width", 1440)
        .add("connection_type", "4g");

    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_pages (web_page_id, link) VALUES (1, '/news/article') "
          + "ON CONFLICT DO NOTHING");
    }

    // Matches WebVitalsCollector.PRIMARY_KEY -- passing null here (as the original code did)
    // means getGeneratedKeys() is never requested and every insert silently fails.
    long insertId = DB.insertInto("web_vitals", insertValues, new String[]{"id"});
    assertTrue(insertId > 0, "insert should report a generated id");

    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT value, web_page_id, viewport_width FROM web_vitals WHERE id = ?")) {
      pst.setLong(1, insertId);
      try (ResultSet rs = pst.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(0, new BigDecimal("0.08").compareTo(rs.getBigDecimal("value")),
            "CLS should round-trip as a true fraction, not truncate to an integer scale");
        assertEquals(1L, rs.getLong("web_page_id"));
        assertEquals(1440, rs.getInt("viewport_width"));
      }
    }
  }

  @Test
  void aggregationUpsertReplacesTheSameDaysRow() throws SQLException {
    // Mirrors WebVitalsAggregationJob.insertAggregate()'s exact upsert shape: same
    // (url, metric_type, day) must update in place, not accumulate duplicate rows.
    String sql = "INSERT INTO web_vitals_aggregates (url, metric_type, p50_value, p75_value, p95_value, "
        + "sample_count, aggregated_at) VALUES (?, ?, ?, ?, ?, ?, date_trunc('day', NOW())) "
        + "ON CONFLICT (url, metric_type, aggregated_at) DO UPDATE SET "
        + "p50_value = EXCLUDED.p50_value, p75_value = EXCLUDED.p75_value, "
        + "p95_value = EXCLUDED.p95_value, sample_count = EXCLUDED.sample_count";

    try (Connection connection = DB.getConnection()) {
      upsertAggregate(connection, sql, "/news/article", "LCP", 1200, 2400, 3600, 10);
      upsertAggregate(connection, sql, "/news/article", "LCP", 1300, 2500, 3700, 25);
    }

    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT sample_count FROM web_vitals_aggregates WHERE url = ? AND metric_type = ?")) {
      pst.setString(1, "/news/article");
      pst.setString(2, "LCP");
      try (ResultSet rs = pst.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(25, rs.getInt("sample_count"), "second run should update the same day's row");
        assertTrue(!rs.next(), "there should be exactly one row for this url/metric/day");
      }
    }
  }

  private static void upsertAggregate(Connection connection, String sql, String url, String metricType,
                                       int p50, int p75, int p95, int sampleCount) throws SQLException {
    try (PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, url);
      pst.setString(2, metricType);
      pst.setInt(3, p50);
      pst.setInt(4, p75);
      pst.setInt(5, p95);
      pst.setInt(6, sampleCount);
      pst.executeUpdate();
    }
  }

  private static Set<String> columnsOf(String table) throws SQLException {
    Set<String> columns = new HashSet<>();
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT column_name FROM information_schema.columns WHERE table_name = ?")) {
      pst.setString(1, table);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          columns.add(rs.getString("column_name"));
        }
      }
    }
    return columns;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }

  // --- Install/upgrade parity: no Docker needed, runs unconditionally ---

  @Test
  void installSchemaMatchesUpgradeSchemaColumns() throws IOException {
    // NEW_10010 (fresh installs) must define web_vitals/web_vitals_aggregates with exactly the
    // same columns the upgrade path ends up with (UPGRADE_20260726.2000 + UPGRADE_20260727.1001
    // combined) -- this repo's install/upgrade parity convention.
    String installSql = Files.readString(
        Path.of("src/main/resources/database/install/NEW_10010__new_cms.sql"));
    String baseUpgradeSql = Files.readString(Path.of(
        "src/main/resources/database/upgrade/UPGRADE_20260726.2000__create_web_vitals_table.sql"));
    String expandUpgradeSql = Files.readString(Path.of(
        "src/main/resources/database/upgrade/2026/UPGRADE_20260727.1001__web_vitals_context_and_aggregates.sql"));

    Set<String> installWebVitals = createTableColumns(installSql, "web_vitals");
    Set<String> installAggregates = createTableColumns(installSql, "web_vitals_aggregates");

    Set<String> upgradeWebVitals = new TreeSet<>(createTableColumns(baseUpgradeSql, "web_vitals"));
    upgradeWebVitals.addAll(addedColumns(expandUpgradeSql, "web_vitals"));
    Set<String> upgradeAggregates = createTableColumns(expandUpgradeSql, "web_vitals_aggregates");

    assertEquals(installWebVitals, upgradeWebVitals,
        "web_vitals columns differ between the install and upgrade paths");
    assertEquals(installAggregates, upgradeAggregates,
        "web_vitals_aggregates columns differ between the install and upgrade paths");
  }

  /** Extracts column names from a {@code CREATE TABLE <name> ( ... )} block. */
  private static Set<String> createTableColumns(String sql, String tableName) {
    Pattern createTable = Pattern.compile(
        "CREATE TABLE (?:IF NOT EXISTS )?" + Pattern.quote(tableName) + "\\s*\\((.*)",
        Pattern.DOTALL);
    Matcher matcher = createTable.matcher(stripLineComments(sql));
    assertTrue(matcher.find(), "no CREATE TABLE " + tableName + " found");
    return parseColumnNames(closingParenBody(matcher.group(1)));
  }

  /** Extracts the column name added by each {@code ALTER TABLE <name> ADD COLUMN ...} statement. */
  private static List<String> addedColumns(String sql, String tableName) {
    List<String> added = new ArrayList<>();
    Pattern addColumn = Pattern.compile(
        "ALTER TABLE " + Pattern.quote(tableName) + "\\s+ADD COLUMN\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    Matcher matcher = addColumn.matcher(stripLineComments(sql));
    while (matcher.find()) {
      added.add(matcher.group(1));
    }
    return added;
  }

  private static String stripLineComments(String sql) {
    return sql.replaceAll("--[^\n]*", "");
  }

  /** Given text starting just after a CREATE TABLE's opening paren, returns up to its matching close. */
  private static String closingParenBody(String afterOpenParen) {
    int depth = 1;
    for (int i = 0; i < afterOpenParen.length(); i++) {
      char c = afterOpenParen.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return afterOpenParen.substring(0, i);
        }
      }
    }
    throw new IllegalStateException("unbalanced parentheses in CREATE TABLE body");
  }

  /** Splits a CREATE TABLE body on top-level commas only (not ones inside e.g. NUMERIC(10, 2)). */
  private static Set<String> parseColumnNames(String body) {
    Set<String> names = new HashSet<>();
    int depth = 0;
    int start = 0;
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        lines.add(body.substring(start, i));
        start = i + 1;
      }
    }
    lines.add(body.substring(start));

    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      String upper = line.toUpperCase();
      if (upper.startsWith("CONSTRAINT") || upper.startsWith("UNIQUE") || upper.startsWith("CHECK")
          || upper.startsWith("PRIMARY KEY") || upper.startsWith("FOREIGN KEY")) {
        continue;
      }
      names.add(line.split("\\s+")[0]);
    }
    return names;
  }
}
