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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.admin.DatabaseCommand;
import com.simisinc.platform.infrastructure.persistence.DatabaseVersionRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Installs the real schema with two logins and proves the split holds (issue #1129): a migration
 * login that owns the schema, and a connection pool login that can only read and write rows.
 *
 * <p>Like {@link DatabaseMigrationTest}, this drives {@link DatabaseCommand#initialize(Properties)},
 * the entry point the application uses at startup, against a real PostgreSQL with PostGIS. The
 * difference is who connects. Neither login is a superuser, which is the point: the stock test image
 * hands out a superuser, and a superuser passes every privilege check, so the ordinary migration
 * test cannot notice a statement the pool's login is not allowed to run.
 *
 * <p>What this covers that nothing else does:
 * <ul>
 * <li>A fresh install, where the Java install migrations write through the pool to tables the same
 * Flyway run created moments earlier. That only works if the grants arrive with the tables.</li>
 * <li>The second start, where an installed database must not be mistaken for a new one.</li>
 * <li>What the pool's login is refused: creating, altering, dropping and truncating.</li>
 * <li>JobRunr's own tables, which it would otherwise create through the pool.</li>
 * <li>The edges of what is handed out: which sequences can be set back, whose tables are granted,
 * and that a web-only node gets by on the pool's login alone.</li>
 * </ul>
 */
class MigrationLoginIntegrationTest {

  private static final String DEFAULT_IMAGE = "postgis/postgis:17-3.5";
  private static final int POSTGRES_PORT = 5432;
  private static final String BOOTSTRAP_DB = "bootstrap";
  private static final String SUPERUSER = "postgres_admin";
  private static final String SUPERUSER_PASSWORD = "admin-pw";

  private static final String DB_NAME = "simis";
  private static final String OWNER = "cms_owner";
  private static final String OWNER_PASSWORD = "owner-pw";
  private static final String APP = "cms_app";
  private static final String APP_PASSWORD = "app-pw";

  private static GenericContainer<?> postgres;
  private static Properties databaseProperties;
  private static boolean installed = false;

  @BeforeAll
  static void installWithTwoLogins() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping the migration login test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", SUPERUSER)
        .withEnv("POSTGRES_PASSWORD", SUPERUSER_PASSWORD)
        .withEnv("POSTGRES_DB", BOOTSTRAP_DB)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    postgres.start();

    // What an administrator does once, by hand: two ordinary logins, a database the migration login
    // owns, and PostGIS, which only an administrator may create. The install then finds PostGIS
    // already there.
    try (Connection connection = DriverManager.getConnection(jdbcUrl(BOOTSTRAP_DB), SUPERUSER, SUPERUSER_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE " + OWNER + " LOGIN PASSWORD '" + OWNER_PASSWORD + "'");
      statement.execute("CREATE ROLE " + APP + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
      statement.execute("CREATE DATABASE " + DB_NAME + " OWNER " + OWNER);
    }
    try (Connection connection = DriverManager.getConnection(jdbcUrl(DB_NAME), SUPERUSER, SUPERUSER_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE EXTENSION postgis");
    }

    // The pool connects as the data-only login
    Properties dataSourceProperties = new Properties();
    dataSourceProperties.setProperty("jdbcUrl", jdbcUrl(DB_NAME));
    dataSourceProperties.setProperty("username", APP);
    dataSourceProperties.setProperty("password", APP_PASSWORD);
    DataSource.init(dataSourceProperties);

    databaseProperties = new Properties();
    databaseProperties.setProperty("dataSource.serverName", postgres.getHost());
    databaseProperties.setProperty("dataSource.portNumber", String.valueOf(postgres.getMappedPort(POSTGRES_PORT)));
    databaseProperties.setProperty("dataSource.databaseName", DB_NAME);
    databaseProperties.setProperty("dataSource.user", APP);
    databaseProperties.setProperty("dataSource.password", APP_PASSWORD);
    configureSplit();

    installed = DatabaseCommand.initialize(databaseProperties);
  }

  private static void configureSplit() {
    MigrationLogin.configure(databaseProperties,
        Map.of(MigrationLogin.USER_VARIABLE, OWNER, MigrationLogin.PASSWORD_VARIABLE, OWNER_PASSWORD));
  }

  @AfterAll
  static void stopDatabase() {
    MigrationLogin.reset();
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  // -- Helpers ------------------------------------------------------------------------------------

  private static String jdbcUrl(String database) {
    return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/" + database;
  }

  private static Connection asOwner() throws SQLException {
    return DriverManager.getConnection(jdbcUrl(DB_NAME), OWNER, OWNER_PASSWORD);
  }

  /** Runs a statement as the pool's login, through the pool itself */
  private static void asApp(String sql) throws SQLException {
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void asOwner(String sql) throws SQLException {
    try (Connection connection = asOwner(); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Runs a statement as the database's administrator, for what only an administrator sets up */
  private static void asSuperuser(String sql) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(DB_NAME), SUPERUSER, SUPERUSER_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static long countAsApp(String table) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String ownerOf(String relation) throws SQLException {
    try (Connection connection = asOwner();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE relname = ? AND relnamespace = 'public'::regnamespace")) {
      pst.setString(1, relation);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  /** PostgreSQL's "insufficient_privilege" */
  private static void assertPermissionDenied(String sql) {
    SQLException e = assertThrows(SQLException.class, () -> asApp(sql), sql);
    assertEquals("42501", e.getSQLState(), sql + " failed, but not for want of permission: " + e.getMessage());
  }

  // -- The install --------------------------------------------------------------------------------

  @Test
  void aFreshInstallSucceedsWithNeitherLoginASuperuser() throws SQLException {
    assertTrue(installed, "DatabaseCommand.initialize() must succeed with a separate migration login");
    assertTrue(DatabaseVersionRepository.count() > 0, "the pool's login reads the version the install recorded");
    // V71120__create_admin writes through the pool, in the Flyway run that created the users table
    assertTrue(countAsApp("users") > 0, "the Java install migration created the administrator through the pool");
    assertTrue(countAsApp("site_properties") > 0);
  }

  @Test
  void theSchemaBelongsToTheMigrationLogin() throws SQLException {
    assertEquals(OWNER, ownerOf("users"));
    assertEquals(OWNER, ownerOf("site_properties"));
    assertEquals(OWNER, ownerOf("flyway_history"));
  }

  @Test
  void aSecondStartSeesAnInstalledDatabaseAndUpgradesIt() throws SQLException {
    long users = countAsApp("users");

    assertTrue(DatabaseCommand.initialize(databaseProperties), "the upgrade path, as the same two logins");

    assertEquals(users, countAsApp("users"), "nothing was installed a second time");
  }

  // -- What the pool's login may and may not do ---------------------------------------------------

  @Test
  void thePoolsLoginReadsAndWritesRows() throws SQLException {
    asApp("INSERT INTO site_properties (property_label, property_name, property_value) "
        + "VALUES ('Migration login test', 'test.migrationLogin', 'a')");
    asApp("UPDATE site_properties SET property_value = 'b' WHERE property_name = 'test.migrationLogin'");
    asApp("DELETE FROM site_properties WHERE property_name = 'test.migrationLogin'");
  }

  @Test
  void thePoolsLoginCannotChangeTheSchema() {
    assertPermissionDenied("CREATE TABLE made_by_the_pool (id INTEGER)");
    assertPermissionDenied("ALTER TABLE users ADD COLUMN made_by_the_pool TEXT");
    assertPermissionDenied("DROP TABLE site_properties");
    assertPermissionDenied("TRUNCATE site_properties");
    assertPermissionDenied("CREATE INDEX made_by_the_pool_idx ON users (email)");
    assertPermissionDenied("ALTER SEQUENCE order_daily_seq RESTART WITH 5");
  }

  @Test
  void theDailyOrderNumberStillResetsWithoutOwningTheSequence() {
    // OrderCommand does this once a day. ALTER SEQUENCE ... RESTART, as it was written, needs
    // ownership; setval needs only UPDATE on the sequence.
    DB.resetSequence("order_daily_seq", 1001);

    assertEquals(1001, DB.selectNextSequenceValue("order_daily_seq"), "the next value is the reset value itself");
    assertEquals(1002, DB.selectNextSequenceValue("order_daily_seq"));
  }

  @Test
  void onlyTheSequencesTheApplicationResetsCanBeSetBackFromThePool() throws SQLException {
    List<String> settable = new ArrayList<>();
    List<String> advanceOnly = new ArrayList<>();
    try (Connection connection = asOwner();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT relname, has_sequence_privilege(?, oid, 'USAGE'), has_sequence_privilege(?, oid, 'UPDATE') "
                + "FROM pg_class WHERE relkind = 'S' AND relnamespace = 'public'::regnamespace ORDER BY relname")) {
      pst.setString(1, APP);
      pst.setString(2, APP);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          assertTrue(rs.getBoolean(2), "the pool's login takes the next value from " + rs.getString(1));
          (rs.getBoolean(3) ? settable : advanceOnly).add(rs.getString(1));
        }
      }
    }
    assertEquals(MigrationLogin.RESETTABLE_SEQUENCES, settable, "UPDATE, which is what setval needs, on these and no others");
    assertFalse(advanceOnly.isEmpty(), "the schema has other sequences");
    assertPermissionDenied("SELECT setval('" + advanceOnly.get(0) + "', 1, false)");

    // The same holds for a sequence a later migration creates
    asOwner("CREATE SEQUENCE added_by_a_later_migration_seq");
    try {
      asApp("SELECT nextval('added_by_a_later_migration_seq')");
      assertPermissionDenied("SELECT setval('added_by_a_later_migration_seq', 1, false)");
    } finally {
      asOwner("DROP SEQUENCE added_by_a_later_migration_seq");
    }
  }

  @Test
  void aTableAnotherLoginKeepsInTheSchemaIsNotHandedOut() throws SQLException {
    // The migration login is made a member of the other login's role. That is one way to pass
    // pg_has_role() for a table it does not own; being a superuser is the other, and passes it for
    // every table there is. Ownership is the test, so neither hands the table to the pool.
    asSuperuser("CREATE ROLE other_app");
    try {
      asSuperuser("GRANT other_app TO " + OWNER);
      asSuperuser("CREATE TABLE public.kept_by_another_login (id INTEGER)");
      asSuperuser("ALTER TABLE public.kept_by_another_login OWNER TO other_app");

      assertTrue(MigrationLogin.grantRuntimeAccess(), "the application's own tables are granted as usual");

      assertPermissionDenied("SELECT COUNT(*) FROM kept_by_another_login");
      assertTrue(countAsApp("site_properties") > 0);
    } finally {
      asSuperuser("DROP TABLE IF EXISTS public.kept_by_another_login");
      asSuperuser("DROP ROLE other_app");
    }
  }

  @Test
  void flywaysHistoryCanBeReadButNotRewrittenFromThePool() throws SQLException {
    assertTrue(countAsApp("flyway_history") > 0, "a web-only node reads this to learn whether migrations are done");
    assertTrue(countAsApp("flyway_install") > 0);
    assertPermissionDenied("DELETE FROM flyway_history");
    assertPermissionDenied("UPDATE flyway_install SET success = false");
    assertPermissionDenied("INSERT INTO flyway_history (installed_rank, description, type, script, installed_by, "
        + "execution_time, success) VALUES (99999, 'x', 'SQL', 'x', 'x', 0, true)");
  }

  // -- The grants look after themselves -----------------------------------------------------------

  @Test
  void aTableAMigrationCreatesLaterIsUsableAtOnce() throws SQLException {
    // No grant is run between the two statements: default privileges did it at creation
    asOwner("CREATE TABLE added_by_a_later_migration (id BIGSERIAL PRIMARY KEY, note TEXT)");
    try {
      asApp("INSERT INTO added_by_a_later_migration (note) VALUES ('usable')");
      assertEquals(1, countAsApp("added_by_a_later_migration"));
    } finally {
      asOwner("DROP TABLE added_by_a_later_migration");
    }
  }

  @Test
  void aGrantRemovedByHandIsRestoredAtTheNextStart() throws SQLException {
    asOwner("REVOKE SELECT ON site_properties FROM " + APP);
    try {
      assertPermissionDenied("SELECT COUNT(*) FROM site_properties");
    } finally {
      assertTrue(MigrationLogin.grantRuntimeAccess());
    }
    assertTrue(countAsApp("site_properties") > 0);
  }

  @Test
  void aPoolLoginThatDoesNotExistStopsStartupInsteadOfFailingLater() {
    Properties missing = new Properties();
    missing.putAll(databaseProperties);
    missing.setProperty("dataSource.user", "nobody_created_this_login");
    MigrationLogin.configure(missing,
        Map.of(MigrationLogin.USER_VARIABLE, OWNER, MigrationLogin.PASSWORD_VARIABLE, OWNER_PASSWORD));
    try {
      assertFalse(MigrationLogin.grantRuntimeAccess());
    } finally {
      configureSplit();
    }
  }

  @Test
  void aWrongMigrationPasswordStopsStartupToo() {
    MigrationLogin.configure(databaseProperties,
        Map.of(MigrationLogin.USER_VARIABLE, OWNER, MigrationLogin.PASSWORD_VARIABLE, "not-the-password"));
    try {
      assertFalse(MigrationLogin.grantRuntimeAccess());
    } finally {
      configureSplit();
    }
  }

  // -- JobRunr ------------------------------------------------------------------------------------

  @Test
  void jobRunrsTablesAreMadeByTheMigrationLoginAndUsedThroughThePool() throws SQLException {
    StorageProvider storageProvider = SchedulerManager.createSqlStorageProvider();
    try {
      assertEquals(OWNER, ownerOf("jobrunr_jobs"), "JobRunr's table setup ran as the migration login");
      // Read through the pool directly: the provider's own queries need a job mapper, which only
      // the scheduler's full configuration supplies
      assertEquals(0, countAsApp("jobrunr_jobs"), "and the pool's login can use the tables");
      asApp("DELETE FROM jobrunr_jobs WHERE state = 'no-such-state'");
      assertPermissionDenied("DROP TABLE jobrunr_jobs");
      // JobRunr keeps a migration history of its own, and it is as read-only as Flyway's
      assertTrue(countAsApp("jobrunr_migrations") > 0);
      assertPermissionDenied("DELETE FROM jobrunr_migrations");
    } finally {
      storageProvider.close();
    }
  }

  // -- A web-only node ----------------------------------------------------------------------------

  @Test
  void aWebOnlyNodeGetsByOnThePoolsLoginAlone() throws SQLException {
    // The primary node has started first, as it must: Flyway's migrations, then JobRunr's tables
    SchedulerManager.createSqlStorageProvider().close();

    MigrationLogin.configure(databaseProperties, Map.of("CMS_NODE_TYPE", "web",
        MigrationLogin.USER_VARIABLE, OWNER, MigrationLogin.PASSWORD_VARIABLE, OWNER_PASSWORD));
    try {
      assertFalse(MigrationLogin.isSeparate(), "the owner's login is not used here, though it was offered");
      assertEquals(APP, MigrationLogin.user(databaseProperties));

      // The read DatabaseCommand makes while it waits for the primary node: Flyway's info(), with
      // the settings the upgrade itself uses
      MigrationInfoService info = Flyway.configure()
          .table("flyway_history")
          .validateOnMigrate(false)
          .sqlMigrationPrefix("UPGRADE_")
          .repeatableSqlMigrationPrefix("REPEAT_")
          .dataSource(MigrationLogin.jdbcUrl(databaseProperties), MigrationLogin.user(databaseProperties),
              MigrationLogin.password(databaseProperties))
          .locations("com/simisinc/platform/infrastructure/database/upgrade", "classpath:database/upgrade")
          .placeholderReplacement(false)
          .outOfOrder(true)
          .cleanDisabled(true)
          .load()
          .info();
      assertTrue(info.applied().length > 0, "the data-only login reads the history");
      assertEquals(0, info.pending().length, "and sees that the primary node has finished");

      // And the scheduler's storage, which on this node goes through the pool: with JobRunr's
      // tables up to date there is nothing to create, so a login that can create nothing is enough
      SchedulerManager.createSqlStorageProvider().close();
    } finally {
      configureSplit();
    }
  }

  // -- Docker -------------------------------------------------------------------------------------

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (LinkageError e) {
      // A broken test classpath is not "no Docker": see DatabaseMigrationTest
      fail("Testcontainers could not initialize, which means the test classpath is incomplete "
          + "rather than that Docker is missing: " + e);
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
