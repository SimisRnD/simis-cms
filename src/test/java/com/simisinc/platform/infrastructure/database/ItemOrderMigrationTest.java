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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

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
 * Drives UPGRADE_20260801.1000__items_order_column.sql (issue #815) through the exact Flyway
 * configuration {@link com.simisinc.platform.application.admin.DatabaseCommand} uses for upgrades
 * (same table, prefix, locations, outOfOrder, validateOnMigrate) against a real PostgreSQL
 * instance, baselined just before this migration and targeted at it -- see
 * {@link WebVitalsMigrationTest} for the precedent this follows.
 *
 * <p>
 * This covers the <b>upgrade path</b> (an existing database that already has rows in {@code
 * items}) specifically: {@link DatabaseMigrationTest} separately covers the install path (a fresh
 * database, so there is nothing to backfill) by asserting {@code items.item_order} exists after a
 * full install run. Together the two tests are the "both Flyway paths produce the same schema"
 * verification for this issue -- the schema shape (column exists) is identical either way, but
 * only the upgrade path exercises the backfill logic, since a fresh install has no pre-existing
 * rows to preserve the order of.
 * </p>
 *
 * @author SimIS
 */
class ItemOrderMigrationTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  // Just below the migration under test, so baseline() marks nothing as pre-applied and the
  // migration executes for real, matching an existing (upgrade-path) database that already has
  // item rows to backfill.
  private static final String BASELINE_BEFORE_ITEM_ORDER = "20260801.0999";

  // The migration under test, passed to Flyway as an upper bound (target()). Without this,
  // outOfOrder(true) plus no ceiling would apply any migration dated after the baseline above --
  // see WebVitalsMigrationTest's LAST_WEB_VITALS_MIGRATION for the precedent and why this matters.
  private static final String ITEM_ORDER_MIGRATION = "20260801.1000";

  private static GenericContainer<?> postgres;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping the items.item_order migration test");

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

    // Minimal stand-in for the real items table: only what this migration's ALTER/backfill
    // touches (item_id, collection_id, name). No FK targets (collections, users) are needed since
    // an ALTER TABLE ADD COLUMN doesn't require them to exist.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE items ("
          + "item_id BIGSERIAL PRIMARY KEY, "
          + "collection_id BIGINT NOT NULL, "
          + "name VARCHAR(255) NOT NULL)");
      // Collection 1: inserted out of alphabetical order (and out of item_id order), so the
      // backfill's ORDER BY LOWER(name) is what's actually under test, not insertion order.
      statement.execute("INSERT INTO items (collection_id, name) VALUES "
          + "(1, 'Cherry'), (1, 'apple'), (1, 'Banana')");
      // Collection 2: a second, disjoint collection -- its item_order sequence must restart at 1,
      // proving the backfill partitions by collection_id rather than numbering globally.
      statement.execute("INSERT INTO items (collection_id, name) VALUES "
          + "(2, 'Zebra'), (2, 'Aardvark')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the items stand-in table", se);
    }

    // Same Flyway configuration as DatabaseCommand.upgrade(), baselined just before and targeted
    // just at the migration under test so it's the only one that actually runs.
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
        .baselineVersion(BASELINE_BEFORE_ITEM_ORDER)
        .target(ITEM_ORDER_MIGRATION)
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
  void migrationAppliesSuccessfully() {
    assertTrue(migrateResult.success,
        "items.item_order upgrade migration did not apply cleanly: " + migrateResult.warnings);
    Set<String> appliedVersions = new TreeSet<>();
    for (Object migration : migrateResult.migrations) {
      appliedVersions.add(((org.flywaydb.core.api.output.MigrateOutput) migration).version);
    }
    assertTrue(appliedVersions.contains(ITEM_ORDER_MIGRATION),
        "expected " + ITEM_ORDER_MIGRATION + " to run, got versions: " + appliedVersions);
  }

  @Test
  void itemOrderColumnExistsAfterUpgrade() throws SQLException {
    assertTrue(columnExists("items", "item_order"),
        "items.item_order is missing after the upgrade migration ran");
  }

  @Test
  void backfillOrdersEachCollectionAlphabeticallyByNameStartingAtOne() throws SQLException {
    // Collection 1 was inserted as Cherry, apple, Banana (in that item_id order) -- the backfill
    // must sort by LOWER(name), not by item_id/insertion order, so the resulting item_order must
    // be apple=1, Banana=2, Cherry=3.
    assertEquals(1, itemOrderOf("apple"));
    assertEquals(2, itemOrderOf("Banana"));
    assertEquals(3, itemOrderOf("Cherry"));
  }

  @Test
  void backfillRestartsNumberingPerCollection() throws SQLException {
    // Collection 2 (Zebra, Aardvark) must number 1..2 on its own, not continue from collection
    // 1's 1..3 -- proving ROW_NUMBER() is partitioned by collection_id, not applied globally.
    assertEquals(1, itemOrderOf("Aardvark"));
    assertEquals(2, itemOrderOf("Zebra"));
  }

  @Test
  void everyRowGetsADistinctPositiveOrderWithinItsCollection() throws SQLException {
    // No NULLs and no ties left over from the DEFAULT 100 the ALTER TABLE itself applies --
    // the backfill UPDATE must have touched every pre-existing row.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "SELECT collection_id, COUNT(*) AS total, COUNT(DISTINCT item_order) AS distinct_orders, "
                + "COUNT(item_order) AS non_null "
                + "FROM items GROUP BY collection_id")) {
      boolean sawARow = false;
      while (rs.next()) {
        sawARow = true;
        int total = rs.getInt("total");
        assertEquals(total, rs.getInt("distinct_orders"),
            "collection " + rs.getLong("collection_id") + " has duplicate item_order values");
        assertEquals(total, rs.getInt("non_null"),
            "collection " + rs.getLong("collection_id") + " has a NULL item_order left over");
      }
      assertTrue(sawARow, "no rows found in items - test setup did not insert as expected");
    }
  }

  private static int itemOrderOf(String name) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT item_order FROM items WHERE name = ?")) {
      pst.setString(1, name);
      try (ResultSet rs = pst.executeQuery()) {
        assertTrue(rs.next(), "no item named " + name + " found");
        return rs.getInt("item_order");
      }
    }
  }

  private static boolean columnExists(String table, String column) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?) AS present")) {
      pst.setString(1, table);
      pst.setString(2, column);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next() && rs.getBoolean("present");
      }
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
