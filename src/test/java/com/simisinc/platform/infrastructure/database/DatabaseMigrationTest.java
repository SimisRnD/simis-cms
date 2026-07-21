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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.admin.DatabaseCommand;

/**
 * Applies the real schema migrations to a real PostgreSQL and asserts they succeed.
 *
 * <p>
 * This drives {@link DatabaseCommand#initialize(Properties)} -- the same entry point the
 * application uses at startup -- rather than reimplementing the Flyway configuration, so the test
 * exercises the migration path that actually ships: the install run (<code>NEW_</code> prefix,
 * <code>flyway_install</code> table, SQL plus Java migrations) followed by the baseline.
 * </p>
 *
 * <p>
 * <b>Why this exists.</b> Until now nothing verified the migrations. The one integration test in
 * the tree reported <code>Tests run: 0</code> in CI as well as locally, because Testcontainers was
 * vendored without its docker-java dependency: {@code DockerClientFactory} threw
 * {@link NoClassDefFoundError}, the availability helper caught {@link Throwable} and returned
 * false, and the whole class aborted while the build still reported success. On top of that, the
 * test hand-built a cut-down table instead of running Flyway, so even a working Docker setup would
 * not have covered a migration. A major Flyway upgrade was merged against that.
 * </p>
 *
 * <p>
 * <b>Skipping is deliberate and narrow.</b> A machine with no Docker daemon legitimately cannot run
 * this, and is skipped. But a broken test classpath is NOT a reason to skip -- that is the failure
 * that hid this gap for so long, so {@link LinkageError} fails the test loudly instead of being
 * mistaken for "no Docker".
 * </p>
 *
 * @author SimIS
 * @created 7/21/2026 12:30 PM
 */
class DatabaseMigrationTest {

  // PostGIS is required, not optional: the first install migration runs "CREATE EXTENSION postgis"
  // on line 4, so a stock postgres image fails immediately. This tracks the project's own database
  // image (docker/db/Dockerfile, PostgreSQL 17 + PostGIS) rather than the older stock image the
  // other integration test uses, since the point here is to exercise the real schema.
  private static final String DEFAULT_IMAGE = "postgis/postgis:17-3.5";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis";
  // initdb creates this one, and the postgis image enables the extension in it
  private static final String BOOTSTRAP_DB = "bootstrap";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static boolean migrated = false;

  @BeforeAll
  static void migrate() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping the database migration test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", BOOTSTRAP_DB)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forListeningPort());
    postgres.start();

    // The migration must run against a database that does NOT already have PostGIS: the postgis
    // image installs the extension into whatever database initdb creates, and the first install
    // migration runs a bare "CREATE EXTENSION postgis" which then fails with
    // "extension postgis already exists". Creating a second database gives the migrations the
    // clean target they expect. (Worth noting separately: that bare CREATE EXTENSION means a
    // managed PostgreSQL with PostGIS pre-enabled cannot be installed onto either.)
    createDatabase();

    Properties dataSourceProperties = new Properties();
    dataSourceProperties.setProperty("jdbcUrl", jdbcUrl());
    dataSourceProperties.setProperty("username", DB_USER);
    dataSourceProperties.setProperty("password", DB_PASSWORD);
    DataSource.init(dataSourceProperties);

    // The property names DatabaseCommand expects when it assembles its own jdbc url
    Properties databaseProperties = new Properties();
    databaseProperties.setProperty("dataSource.serverName", postgres.getHost());
    databaseProperties.setProperty("dataSource.portNumber",
        String.valueOf(postgres.getMappedPort(POSTGRES_PORT)));
    databaseProperties.setProperty("dataSource.databaseName", DB_NAME);
    databaseProperties.setProperty("dataSource.user", DB_USER);
    databaseProperties.setProperty("dataSource.password", DB_PASSWORD);

    migrated = DatabaseCommand.initialize(databaseProperties);
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
  void migrationsApplyToACleanDatabase() {
    assertTrue(migrated,
        "DatabaseCommand.initialize returned false - the migrations did not apply cleanly");
  }

  @Test
  void everyInstallMigrationIsRecordedAsSuccessful() throws SQLException {
    // Flyway records one row per applied migration; a non-successful row means the run
    // half-applied something, which is exactly what a migration-engine upgrade can break.
    List<String> failed = new ArrayList<>();
    int applied = 0;
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "SELECT version, description, success FROM flyway_install ORDER BY installed_rank")) {
      while (rs.next()) {
        applied++;
        if (!rs.getBoolean("success")) {
          failed.add(rs.getString("version") + " " + rs.getString("description"));
        }
      }
    }
    assertTrue(applied > 0, "no migrations were recorded in flyway_install");
    assertEquals(List.of(), failed, "these migrations did not apply successfully: " + failed);
  }

  @Test
  void coreTablesExist() throws SQLException {
    // A representative slice across the schema rather than an exhaustive list, so the test
    // does not need editing every time a table is added. Note the role table is "lookup_role",
    // not "roles" -- guessing that name is how this assertion first failed.
    for (String table : new String[] { "site_properties", "users", "lookup_role", "user_roles",
        "content", "web_pages", "sessions", "audit_log", "database_version" }) {
      assertTrue(tableExists(table), "expected table missing after migration: " + table);
    }
  }

  @Test
  void theWholeSchemaIsCreatedNotJustTheFirstMigration() throws SQLException {
    // Guards against a run that applies the early migrations and stops. The install set builds
    // ~126 tables; the floor is deliberately well below that so ordinary schema growth or
    // removal does not make this brittle, while a half-applied install still fails.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "SELECT count(*) AS total FROM pg_tables WHERE schemaname = 'public'")) {
      assertTrue(rs.next());
      int total = rs.getInt("total");
      assertTrue(total > 100,
          "only " + total + " tables exist after migration; the install did not complete");
    }
  }

  @Test
  void baselineIsRecorded() throws SQLException {
    assertTrue(tableExists("flyway_history"),
        "flyway_history is missing - the baseline step did not run");
  }

  private static boolean tableExists(String name) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "SELECT to_regclass('public." + name + "') IS NOT NULL AS present")) {
      assertNotNull(rs);
      return rs.next() && rs.getBoolean("present");
    }
  }

  private static String jdbcUrl() {
    return jdbcUrl(DB_NAME);
  }

  private static String jdbcUrl(String database) {
    return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + database;
  }

  /** Creates the target database, which initdb has not touched and so has no PostGIS. */
  private static void createDatabase() {
    try (Connection connection = java.sql.DriverManager.getConnection(
            jdbcUrl(BOOTSTRAP_DB), DB_USER, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + DB_NAME);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the target database", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (LinkageError e) {
      // A missing or mismatched Testcontainers dependency is a broken build, not an
      // environment without Docker. Swallowing this is what kept the only integration
      // test in the tree silently reporting "Tests run: 0" in CI for months.
      fail("Testcontainers could not initialize, which means the test classpath is incomplete "
          + "rather than that Docker is missing. Fix the vendored test dependencies instead of "
          + "skipping: " + e);
      return false;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
