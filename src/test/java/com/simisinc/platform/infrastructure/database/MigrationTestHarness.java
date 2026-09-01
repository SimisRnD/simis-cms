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

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs one upgrade migration against a real PostgreSQL, so a test can assert what it actually did.
 *
 * <p>Issue #1755: an upgrade migration only executes in CI if someone hand-writes a test for it, and
 * 3 of 169 have one. The full suite passes whether a new migration is correct, syntactically broken,
 * or silently a no-op, because nothing runs it. That is not theoretical -- the repair migration in
 * #1754 passed a green 4,164-test suite without being executed once, and had to be verified against
 * a container by hand.
 *
 * <p>The reason so few exist is cost. Each of the three tests that do this carries the same ~18 lines
 * of container and Flyway setup, its own copies of the Docker and image helpers, and a hand-picked
 * "baseline just below" constant. This owns all of that, so a migration test is a fixture, a call,
 * and assertions.
 *
 * <p><b>The baseline is derived, not chosen.</b> Each existing test declares a constant like
 * {@code "20260801.0999"} meaning "just below the one under test", and both WebVitalsMigrationTest
 * and ItemOrderMigrationTest carry comments warning that without a matching {@code target()} ceiling,
 * {@code outOfOrder(true)} applies every migration dated after that baseline rather than the one
 * under test. Two hand-maintained version strings, and getting either wrong silently widens what
 * runs. {@link #applyOnly(String)} takes the target and computes the baseline as the highest
 * migration version strictly below it, so the pair cannot disagree.
 *
 * @author SimIS Inc.
 */
public final class MigrationTestHarness implements AutoCloseable {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  /**
   * Mirrors DatabaseCommand's upgrade configuration. It is a copy because that method is private,
   * but it is now one copy instead of three -- if the shipping configuration changes, this is the
   * single place a test can drift from it.
   */
  private static final String[] UPGRADE_LOCATIONS = {
      "com/simisinc/platform/infrastructure/database/upgrade", "classpath:database/upgrade" };

  private final GenericContainer<?> postgres;
  private final String jdbcUrl;

  private MigrationTestHarness(GenericContainer<?> postgres, String jdbcUrl) {
    this.postgres = postgres;
    this.jdbcUrl = jdbcUrl;
  }

  /**
   * Starts PostgreSQL and points the application's DataSource at it, so DB.getConnection() works for
   * the code under test. Skips the calling test when Docker is unavailable rather than failing it.
   */
  public static MigrationTestHarness start(String skipReason) {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping " + skipReason);
    GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
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
    return new MigrationTestHarness(postgres, jdbcUrl);
  }

  /** Runs SQL to build whatever the migration under test expects to find. */
  public void execute(String... sql) {
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      for (String each : sql) {
        statement.execute(each);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not prepare the schema: " + se.getMessage(), se);
    }
  }

  public Connection connection() throws SQLException {
    return DB.getConnection();
  }

  /**
   * Applies exactly one migration: everything below it is marked as already applied, and the target
   * is the ceiling, so nothing after it runs either.
   *
   * @param version the migration's version, e.g. "20260801.1000"
   */
  public MigrateResult applyOnly(String version) {
    return apply(baselineBelow(version), version);
  }

  /**
   * Applies a contiguous run of migrations, for a change that genuinely spans more than one file.
   * Prefer {@link #applyOnly(String)} -- a test that needs a range is usually testing two things.
   */
  public MigrateResult applyThrough(String firstVersion, String lastVersion) {
    return apply(baselineBelow(firstVersion), lastVersion);
  }

  private MigrateResult apply(String baselineVersion, String targetVersion) {
    Flyway flyway = Flyway.configure()
        .table("flyway_history")
        .validateOnMigrate(false)
        .sqlMigrationPrefix("UPGRADE_")
        .repeatableSqlMigrationPrefix("REPEAT_")
        .dataSource(jdbcUrl, DB_USER, DB_PASSWORD)
        .locations(UPGRADE_LOCATIONS)
        .placeholderReplacement(false)
        .outOfOrder(true)
        .cleanDisabled(true)
        .baselineVersion(baselineVersion)
        .target(targetVersion)
        .load();
    flyway.baseline();
    return flyway.migrate();
  }

  /**
   * The highest migration version strictly below the given one, read from the migration files
   * themselves. Baselining there marks every earlier migration as applied without running it, which
   * is what makes the target the only migration that executes.
   */
  static String baselineBelow(String version) {
    List<String> versions = allUpgradeVersions();
    if (!versions.contains(version)) {
      throw new IllegalArgumentException("No upgrade migration found at version " + version
          + " -- check the version string against the filename");
    }
    String best = null;
    for (String candidate : versions) {
      if (compareVersions(candidate, version) < 0
          && (best == null || compareVersions(candidate, best) > 0)) {
        best = candidate;
      }
    }
    // Nothing earlier exists, so baselining at 0 leaves the target as the first thing to run
    return best == null ? "0" : best;
  }

  /** Every UPGRADE_ version on the classpath, from the same directory Flyway reads. */
  static List<String> allUpgradeVersions() {
    List<String> versions = new ArrayList<>();
    URL url = MigrationTestHarness.class.getClassLoader().getResource("database/upgrade");
    if (url == null) {
      return versions;
    }
    collectVersions(new File(url.getFile()), versions);
    Collections.sort(versions);
    return versions;
  }

  private static void collectVersions(File directory, List<String> versions) {
    File[] entries = directory.listFiles();
    if (entries == null) {
      return;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collectVersions(entry, versions);
      } else if (entry.getName().startsWith("UPGRADE_") && entry.getName().endsWith(".sql")) {
        int separator = entry.getName().indexOf("__");
        if (separator > 0) {
          versions.add(entry.getName().substring("UPGRADE_".length(), separator));
        }
      }
    }
  }

  /** Compares dotted numeric versions the way Flyway orders them, not as strings. */
  static int compareVersions(String left, String right) {
    String[] leftParts = left.split("\\.");
    String[] rightParts = right.split("\\.");
    for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
      long l = i < leftParts.length ? parseOrZero(leftParts[i]) : 0;
      long r = i < rightParts.length ? parseOrZero(rightParts[i]) : 0;
      if (l != r) {
        return Long.compare(l, r);
      }
    }
    return 0;
  }

  private static long parseOrZero(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public void close() {
    if (postgres != null) {
      postgres.stop();
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
