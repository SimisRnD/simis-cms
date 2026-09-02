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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
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

  // ---------------------------------------------------------------------------------------------
  // The upgrade track (issue #1755).
  //
  // Everything above covers the install run. The upgrade track was not executed by anything: a
  // fresh install baselines flyway_history above every UPGRADE_ file that exists, so they are
  // recorded as applied without running, and a new one only reached CI if its author hand-wrote a
  // test. Three of 169 had one. A migration that was syntactically broken, referenced a dropped
  // object, or silently did nothing passed the whole suite.
  //
  // These apply each UPGRADE_*.sql to the installed database inside a transaction that is then
  // rolled back, so the schema the assertions above depend on is not disturbed and the tests are
  // order-independent. Replaying against a modern install is not the same as replaying against the
  // schema of the day -- an upgrade the install track has since caught up with cannot succeed
  // twice -- so the ones that legitimately cannot replay are listed, with a reason, in
  // upgrade-replay-exceptions.txt, and the list is checked in both directions.
  // ---------------------------------------------------------------------------------------------

  // Both are read from the source tree rather than the classpath. compile-test stages only
  // src/main/resources/database onto the test classpath, and the exceptions list must not be in
  // there: it is test data, and anything under src/main/resources/database is copied into the WAR.
  private static final Path EXCEPTIONS_FILE =
      Paths.get("src/test/resources/database/upgrade-replay-exceptions.txt");
  private static final Path UPGRADE_DIRECTORY = Paths.get("src/main/resources/database/upgrade");

  @Test
  void everyUpgradeMigrationEitherReplaysOrIsAKnownException() throws Exception {
    Map<String, String> exceptions = readExceptions();
    List<String> unexpectedFailures = new ArrayList<>();
    int replayed = 0;
    for (Path file : upgradeMigrations()) {
      String name = file.getFileName().toString();
      String failure = replayFailure(file);
      if (failure == null) {
        replayed++;
        continue;
      }
      if (!exceptions.containsKey(name)) {
        unexpectedFailures.add(name + " -- " + failure);
      }
    }
    assertTrue(replayed > 0, "no upgrade migrations were executed at all");
    assertEquals(List.of(), unexpectedFailures,
        "these upgrade migrations no longer apply to a freshly installed database. If the cause is "
            + "that the install track now does the same thing, add the file to "
            + EXCEPTIONS_FILE + " with that reason. Otherwise it is a defect in the migration: "
            + unexpectedFailures);
  }

  @Test
  void everyListedExceptionStillFails() throws Exception {
    // The other direction, so the list cannot rot. A migration that starts replaying cleanly --
    // because the install file it collided with was removed, say -- must lose its line, or the
    // list slowly becomes a place where real failures could hide.
    Map<String, String> exceptions = readExceptions();
    List<String> nowPassing = new ArrayList<>();
    List<String> notFound = new ArrayList<>(exceptions.keySet());
    for (Path file : upgradeMigrations()) {
      String name = file.getFileName().toString();
      if (!exceptions.containsKey(name)) {
        continue;
      }
      notFound.remove(name);
      if (replayFailure(file) == null) {
        nowPassing.add(name);
      }
    }
    // Reported together rather than as two assertions, so a stale entry does not hide a missing
    // file behind it -- the first assertion to fail would be the only one anyone saw.
    List<String> stale = new ArrayList<>();
    nowPassing.forEach(name -> stale.add(name + " (now applies cleanly)"));
    notFound.forEach(name -> stale.add(name + " (no such migration)"));
    assertEquals(List.of(), stale,
        "remove these lines from " + EXCEPTIONS_FILE + " -- an entry that is no longer true is a "
            + "place a real failure could hide: " + stale);
  }

  /**
   * Applies one migration and rolls it back.
   *
   * @return null when it applied, or the first line of the database error when it did not
   */
  private static String replayFailure(Path file) throws Exception {
    String sql = Files.readString(file, StandardCharsets.UTF_8);
    try (Connection connection = DB.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
        return null;
      } catch (SQLException e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
        return message.length() > 160 ? message.substring(0, 160) : message;
      } finally {
        // Nothing this method does may survive: the assertions above run against this same
        // database, and the migrations are replayed in no particular order.
        connection.rollback();
      }
    }
  }

  private static List<Path> upgradeMigrations() throws IOException {
    assertTrue(Files.isDirectory(UPGRADE_DIRECTORY),
        "upgrade migrations not found at " + UPGRADE_DIRECTORY.toAbsolutePath()
            + " -- this test reads them from the source tree, so it must run from the project root");
    try (Stream<Path> walk = Files.walk(UPGRADE_DIRECTORY)) {
      return walk.filter(path -> path.getFileName().toString().endsWith(".sql"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .collect(Collectors.toList());
    }
  }

  /** @return filename to reason, for every non-comment line of the exceptions list */
  private static Map<String, String> readExceptions() throws IOException {
    assertTrue(Files.isRegularFile(EXCEPTIONS_FILE),
        "missing " + EXCEPTIONS_FILE.toAbsolutePath() + " -- this test runs from the project root");
    Map<String, String> exceptions = new LinkedHashMap<>();
    for (String line : Files.readAllLines(EXCEPTIONS_FILE, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int hash = trimmed.indexOf('#');
      String name = (hash == -1 ? trimmed : trimmed.substring(0, hash)).trim();
      String reason = hash == -1 ? "" : trimmed.substring(hash + 1).trim();
      assertTrue(!reason.isEmpty(),
          "every entry needs a reason after '#', so the list stays reviewable: " + name);
      exceptions.put(name, reason);
    }
    return exceptions;
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

  @Test
  void columnsThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() throws SQLException {
    // A table can exist (coreTablesExist passes) while still being missing a column that an
    // upgrade/ migration added without a matching install/ counterpart -- since a fresh install
    // never runs the upgrade/ tree, only baselines past it. Two such gaps reached this point
    // undetected: users.account_token_expires (added by
    // UPGRADE_20260725.1001__account_token_expires.sql) broke every login on a fresh install --
    // UserRepository.buildRecord() unconditionally reads it -- and sessions.is_anonymous (added
    // by UPGRADE_20260726.1002__sessions_is_anonymous.sql) broke SessionRepository's daily
    // unique-locations report. Both are represented here, not as an exhaustive general check,
    // but so this specific class of regression fails loudly instead of silently again.
    assertTrue(columnExists("users", "account_token_expires"),
        "users.account_token_expires is missing - a fresh install cannot log anyone in "
            + "(UserRepository.buildRecord reads this column unconditionally)");
    assertTrue(columnExists("sessions", "is_anonymous"),
        "sessions.is_anonymous is missing - SessionRepository.findDailyUniqueLocations() "
            + "will fail on a fresh install");
    // items.item_order (issue #815) is mirrored directly into NEW_10024__new_items.sql rather than
    // added by a separate upgrade-only migration, but this is exactly the class of gap that
    // mirroring is meant to prevent -- ItemRepository.buildRecord() reads this column
    // unconditionally too, so asserting it here guards against the same regression shape.
    assertTrue(columnExists("items", "item_order"),
        "items.item_order is missing - ItemRepository.buildRecord()/findAll() read/sort by this "
            + "column unconditionally, and reorderCollectionItem has nowhere to persist a reorder "
            + "without it (issue #815)");
  }

  @Test
  void webhookTablesExistOnAFreshInstall() throws SQLException {
    // Issue #418: webhook_subscription/webhook_delivery are added in BOTH
    // NEW_10130__new_webhooks.sql (install/) and UPGRADE_20260801.1001__create_webhook_tables.sql
    // (upgrade/) -- this is the same install/upgrade mirroring gap class as
    // tablesThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() below (issue #431 precedent),
    // checked directly here for the tables this PR actually adds.
    assertTrue(tableExists("webhook_subscription"), "webhook_subscription is missing on a fresh install");
    assertTrue(tableExists("webhook_delivery"), "webhook_delivery is missing on a fresh install");
    assertTrue(columnExists("webhook_delivery", "delivery_uuid"),
        "webhook_delivery.delivery_uuid is missing -- issue #456's idempotency id has nowhere to live");
    assertTrue(columnExists("webhook_delivery", "next_retry_at"),
        "webhook_delivery.next_retry_at is missing -- AttemptWebhookDeliveryCommand's backoff schedule cannot be persisted");
  }

  @Test
  void imageVariantsTableExistsOnAFreshInstall() throws SQLException {
    // Issue #411: image_variants is added in BOTH NEW_10160__new_image_variants.sql (install/)
    // and UPGRADE_20260803.1003__image_variants.sql (upgrade/) -- same install/upgrade mirroring
    // gap class as webhookTablesExistOnAFreshInstall() above.
    assertTrue(tableExists("image_variants"), "image_variants is missing on a fresh install");
    assertTrue(columnExists("image_variants", "image_id"),
        "image_variants.image_id is missing -- ImageVariantRepository queries will fail on a fresh install");
    assertTrue(columnExists("image_variants", "variant_type"),
        "image_variants.variant_type is missing -- ImageVariantRepository.findByImageIdAndVariantType() "
            + "will fail on a fresh install");
  }

  @Test
  void featureFlagPropertiesSeedOnAFreshInstall() throws SQLException {
    // Issue #410: NEW_10150__new_feature_flag_properties.sql seeds the features.* site properties
    // that FeatureFlagCommand reads (through the cached/invalidated LoadSitePropertyCommand path --
    // see FeatureFlagCommand's own class javadoc). features.layout-editor must default to 'true' so
    // an install picks up the same always-on composition-canvas behavior that shipped before this
    // flag existed.
    assertEquals("true", sitePropertyValue("features.layout-editor"),
        "features.layout-editor is missing or not defaulted to true on a fresh install");
    assertEquals("boolean", sitePropertyType("features.layout-editor"));
    assertEquals("false", sitePropertyValue("features.item-tags-facet-search"),
        "features.item-tags-facet-search is missing or not defaulted to false on a fresh install");
  }

  @Test
  void cspReportOnlyPropertySeedsOnAFreshInstall() throws SQLException {
    // Issue #1430: security.csp.reportOnly was inserted only by
    // UPGRADE_20260827.1100__csp_report_only_property.sql, so it existed on upgraded deployments
    // and not on fresh installs -- SchemaInstallUpgradeParityTest compares CREATE TABLE statements
    // and would never have caught a missing site_properties row (the csp_violation table itself
    // WAS mirrored, which is what made the gap look closed). The missing row is not just a missing
    // default: SitePropertiesEditorWidget renders and saves only the rows
    // SitePropertyRepository.findAllByPrefix("security") returns, so with no row there is no field
    // on /admin/security-properties and saving that page cannot create one -- leaving CSP
    // report-only mode and the /csp-report collector unreachable. Asserting the empty string rather
    // than just non-null pins both halves: the row exists, and it still seeds blank so
    // CspPolicyCommand.reportOnlyPolicy() returns null and no header is sent until an administrator
    // sets a policy.
    assertEquals("", sitePropertyValue("security.csp.reportOnly"),
        "security.csp.reportOnly is missing on a fresh install -- /admin/security-properties will "
            + "render no field for it, so report-only CSP cannot be enabled at all");
    assertEquals("text", sitePropertyType("security.csp.reportOnly"));
  }

  @Test
  void accountLockoutPropertiesSeedOnAFreshInstall() throws SQLException {
    // Issue #295 / PR #318 shipped the durable account lockout reading its two thresholds through
    // LoadSitePropertyCommand and calling them "site property" in javadoc, but no migration ever
    // inserted a row -- not in install/ and not in upgrade/, so this is the same shape as the
    // security.csp.reportOnly gap above with both halves missing instead of one. Lockout still
    // worked, because AuthenticateLoginCommand falls back to 5 attempts / 15 minutes on a blank
    // value; what did not work was changing either number, since SitePropertiesEditorWidget
    // renders and saves only the rows SitePropertyRepository.findAllByPrefix(prefix) returns, so a
    // property with no row has no field on any settings page and saving cannot create one.
    // Asserting the exact values, not just non-null, pins the part that matters for existing
    // sites: seeding these rows must not change what the login flow already enforces.
    assertEquals("5", sitePropertyValue("security.lockout.threshold"),
        "security.lockout.threshold is missing or not defaulted to 5 on a fresh install -- "
            + "/admin/security-properties will render no field for it, so the lockout threshold "
            + "cannot be changed without a code change");
    assertEquals("text", sitePropertyType("security.lockout.threshold"));
    assertEquals("15", sitePropertyValue("security.lockout.durationMinutes"),
        "security.lockout.durationMinutes is missing or not defaulted to 15 on a fresh install -- "
            + "/admin/security-properties will render no field for it, so the lockout duration "
            + "cannot be changed without a code change");
    assertEquals("text", sitePropertyType("security.lockout.durationMinutes"));
  }

  @Test
  void theSeededNewsletterMailingListHasItsUniqueIdOnAFreshInstall() throws SQLException {
    // Issue #1724 and its follow-up. NEW_50040 seeds a /subscribe page whose emailSubscribe widget
    // carries <mailingListUniqueId>newsletter</mailingListUniqueId>, and the widget refuses to
    // render when that doesn't resolve -- so if NEW_10070 (the column) or NEW_10071 (the row and
    // its id) drifted from the upgrade path, a fresh install would ship a /subscribe page with no
    // signup form on it and nothing but a log line to say why. The literal value is asserted, not
    // just presence: it is named in seeded page XML, so it is part of the contract, not a detail.
    assertTrue(columnExists("mailing_lists", "unique_id"),
        "mailing_lists.unique_id is missing - MailingListRepository.buildRecord() reads this column "
            + "unconditionally, and the emailSubscribe widget's mailingListUniqueId preference has "
            + "nothing to resolve against without it");
    assertEquals("newsletter", mailingListUniqueId("Newsletter"),
        "the seeded Newsletter mailing list has no 'newsletter' unique id - NEW_50040's /subscribe "
            + "page points its emailSubscribe widget at that id, and the widget renders nothing "
            + "when it does not resolve");
  }

  @Test
  void tablesThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() throws SQLException {
    // Same class of gap as columnsThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath above,
    // but for whole tables instead of columns: media_assets/media_asset_usage
    // (UPGRADE_20260726.1004__create_media_assets_table.sql) were never mirrored into install/, so
    // a fresh install had no media_assets table at all -- MediaAssetRepository's queries threw a
    // SQLException that DB.java logs and swallows, silently returning an empty result that looks
    // identical to "no files yet" (issue #771).
    assertTrue(tableExists("media_assets"),
        "media_assets is missing - MediaAssetRepository queries will silently fail on a fresh "
            + "install (DB.java swallows the SQLException and returns an empty result)");
    assertTrue(tableExists("media_asset_usage"),
        "media_asset_usage is missing on a fresh install");
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

  private static String mailingListUniqueId(String name) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT unique_id FROM mailing_lists WHERE name = ?")) {
      pst.setString(1, name);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next() ? rs.getString("unique_id") : null;
      }
    }
  }

  private static String sitePropertyValue(String propertyName) throws SQLException {
    return sitePropertyColumn(propertyName, "property_value");
  }

  private static String sitePropertyType(String propertyName) throws SQLException {
    return sitePropertyColumn(propertyName, "property_type");
  }

  private static String sitePropertyColumn(String propertyName, String column) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT " + column + " FROM site_properties WHERE property_name = ?")) {
      pst.setString(1, propertyName);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next() ? rs.getString(column) : null;
      }
    }
  }

  private static boolean columnExists(String table, String column) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = '" + table + "' "
                + "AND column_name = '" + column + "') AS present")) {
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
