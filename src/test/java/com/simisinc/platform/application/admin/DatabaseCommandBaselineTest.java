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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.ApplicationInfo;

/**
 * Covers {@link DatabaseCommand#resolveInstallBaselineVersion}.
 *
 * <p>
 * A fresh install baselines the upgrade schema history so that migrations already represented in
 * the install/ scripts don't replay. That baseline must never sit below a migration that actually
 * exists on the classpath -- otherwise a fresh install silently, permanently never applies (or
 * even attempts) that migration, with no error. This previously used the hardcoded
 * {@link ApplicationInfo#VERSION} directly as the baseline, which drifts behind the newest
 * migration file whenever nobody remembers to bump it by hand.
 * </p>
 *
 * @author SimIS
 * @created 7/28/2026
 */
class DatabaseCommandBaselineTest {

  private static final int POSTGRES_PORT = 5432;
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static Properties databaseProperties;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping the database baseline test");

    postgres = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_USER)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    postgres.start();

    databaseProperties = new Properties();
    databaseProperties.setProperty("dataSource.user", DB_USER);
    databaseProperties.setProperty("dataSource.password", DB_PASSWORD);
  }

  @AfterAll
  static void stopDatabase() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void fallsBackToApplicationVersionWhenNoMigrationExceedsIt(@TempDir Path emptyLocation) {
    MigrationVersion resolved = DatabaseCommand.resolveInstallBaselineVersion(
        jdbcUrl(), databaseProperties, "filesystem:" + emptyLocation);
    assertEquals(MigrationVersion.fromVersion(ApplicationInfo.VERSION), resolved,
        "with no upgrade migrations present, the baseline should be exactly ApplicationInfo.VERSION");
  }

  @Test
  void ignoresAMigrationOlderThanApplicationVersion(@TempDir Path location) throws IOException {
    writeMigration(location, "UPGRADE_00000001.0001__ancient.sql");

    MigrationVersion resolved = DatabaseCommand.resolveInstallBaselineVersion(
        jdbcUrl(), databaseProperties, "filesystem:" + location);

    assertEquals(MigrationVersion.fromVersion(ApplicationInfo.VERSION), resolved,
        "a migration version below ApplicationInfo.VERSION must not lower the baseline");
  }

  @Test
  void risesToCoverAMigrationNewerThanApplicationVersion(@TempDir Path location) throws IOException {
    // This is the exact bug: a real migration dated after ApplicationInfo.VERSION was last
    // bumped by hand. Before this fix, DatabaseCommand baselined at ApplicationInfo.VERSION
    // directly, so this migration would never be applied AND never even be attempted on a
    // fresh install -- with no error raised anywhere.
    String futureVersion = "99999999.0001";
    writeMigration(location, "UPGRADE_" + futureVersion + "__future.sql");

    MigrationVersion resolved = DatabaseCommand.resolveInstallBaselineVersion(
        jdbcUrl(), databaseProperties, "filesystem:" + location);

    assertEquals(MigrationVersion.fromVersion(futureVersion), resolved,
        "a migration version above ApplicationInfo.VERSION must raise the baseline to cover it");
  }

  private static void writeMigration(Path directory, String filename) throws IOException {
    Files.writeString(directory.resolve(filename), "SELECT 1;\n");
  }

  private static String jdbcUrl() {
    return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/" + DB_USER;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }
}
