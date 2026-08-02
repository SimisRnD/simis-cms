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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
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
 * Covers both halves of issue #570's schema change: web_pages.solution_type is added directly in
 * NEW_10010__new_cms.sql for fresh installs AND via UPGRADE_20260801.1007__web_pages_solution_type.sql
 * for existing databases. This repo has hit the "only mirrored into one path" bug shape multiple
 * times (e.g. issue #431's media_assets table), so both halves get their own coverage here rather
 * than trusting a fresh-install-only check (see also
 * {@link DatabaseMigrationTest#webPagesHasTheSolutionTypeColumnOnAFreshInstall()}, which covers the
 * install side against a real, full migration run).
 *
 * @author SimIS
 * @created 8/1/2026
 */
class SolutionTypeMigrationTest {

  // --- Install/upgrade parity: no Docker needed, runs unconditionally ---

  @Test
  void installAddsTheColumnDirectlyToTheWebPagesCreateTable() throws IOException {
    String installSql = Files.readString(
        Path.of("src/main/resources/database/install/NEW_10010__new_cms.sql"));
    assertTrue(createTableColumns(installSql, "web_pages").contains("solution_type"),
        "NEW_10010__new_cms.sql's web_pages table must declare solution_type directly, "
            + "since a fresh install never runs the upgrade/ migrations");
  }

  @Test
  void upgradeMigrationAddsTheSameColumn() throws IOException {
    String upgradeSql = Files.readString(Path.of(
        "src/main/resources/database/upgrade/2026/UPGRADE_20260801.1007__web_pages_solution_type.sql"));
    assertTrue(addedColumns(upgradeSql, "web_pages").contains("solution_type"),
        "the upgrade migration must ALTER TABLE web_pages ADD COLUMN solution_type "
            + "so existing (already-installed) databases get the same column");
  }

  /** Extracts column names from a {@code CREATE TABLE <name> ( ... )} block. */
  private static java.util.Set<String> createTableColumns(String sql, String tableName) {
    Pattern createTable = Pattern.compile(
        "CREATE TABLE (?:IF NOT EXISTS )?" + Pattern.quote(tableName) + "\\s*\\((.*)",
        Pattern.DOTALL);
    Matcher matcher = createTable.matcher(stripLineComments(sql));
    assertTrue(matcher.find(), "no CREATE TABLE " + tableName + " found");
    return parseColumnNames(closingParenBody(matcher.group(1)));
  }

  /** Extracts the column name added by each {@code ALTER TABLE <name> ADD COLUMN ...} statement. */
  private static java.util.Set<String> addedColumns(String sql, String tableName) {
    java.util.Set<String> added = new java.util.HashSet<>();
    Pattern addColumn = Pattern.compile(
        "ALTER TABLE " + Pattern.quote(tableName) + "\\s+ADD COLUMN\\s+(?:IF NOT EXISTS\\s+)?(\\w+)",
        Pattern.CASE_INSENSITIVE);
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
  private static java.util.Set<String> parseColumnNames(String body) {
    java.util.Set<String> names = new java.util.HashSet<>();
    int depth = 0;
    int start = 0;
    java.util.List<String> lines = new java.util.ArrayList<>();
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

  // --- Real upgrade-path run against Postgres: proves the migration applies to an
  //     already-installed (upgrade-path) database, not just a fresh install ---

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  // Just below our migration's version, so baseline() marks every earlier upgrade migration
  // (including 20260801.1000/.1001, which alter tables this test's minimal stand-in schema
  // doesn't have) as already-applied, and only THIS_MIGRATION actually executes against the
  // stand-in web_pages table, matching an existing (upgrade-path) database that predates it.
  private static final String BASELINE_BEFORE_MIGRATION = "20260801.1006";

  // The migration under test, passed to Flyway as an upper bound (target()). Without this,
  // outOfOrder(true) plus no ceiling would let migrate() apply any later-dated migration too (see
  // WebVitalsMigrationTest's LAST_WEB_VITALS_MIGRATION for the precedent and why this matters).
  // Renamed from 20260801.1000 after a version collision with an already-merged migration
  // (items_order_column.sql) that independently claimed the same version number.
  private static final String THIS_MIGRATION = "20260801.1007";

  private static GenericContainer<?> postgres;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping the solution_type upgrade migration test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    try {
      postgres.start();
    } catch (Throwable t) {
      Assumptions.abort("Unable to start PostgreSQL test container: " + t.getMessage());
    }

    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    // Minimal stand-in for the table this migration ALTERs, matching the pre-migration (upgrade
    // path) shape -- i.e. without solution_type, exactly like a real existing database.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, link VARCHAR(255))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the stand-in web_pages table", se);
    }

    // Same Flyway configuration as DatabaseCommand.upgrade(), baselined just before and targeted
    // just at this migration so it's the only one that actually runs.
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
        .baselineVersion(BASELINE_BEFORE_MIGRATION)
        .target(THIS_MIGRATION)
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
  void migrationAppliesSuccessfullyToAnExistingDatabase() {
    assertTrue(migrateResult.success,
        "the solution_type upgrade migration did not apply cleanly: " + migrateResult.warnings);
  }

  @Test
  void webPagesHasTheSolutionTypeColumnAfterTheUpgradeMigration() throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        var rs = statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_name = 'web_pages' AND column_name = 'solution_type') AS present")) {
      assertTrue(rs.next());
      assertTrue(rs.getBoolean("present"),
          "web_pages.solution_type should exist after running the upgrade migration "
              + "against an existing (pre-#570) database");
    }
  }

  @Test
  void theColumnAcceptsNullSoExistingUntaggedPagesAreUnaffected() throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_pages (link) VALUES ('/pre-existing-page')");
      var rs = statement.executeQuery(
          "SELECT solution_type FROM web_pages WHERE link = '/pre-existing-page'");
      assertTrue(rs.next());
      assertTrue(rs.getString("solution_type") == null,
          "a page that existed before this migration must end up with a null (untagged) solution_type");
    }
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
}
