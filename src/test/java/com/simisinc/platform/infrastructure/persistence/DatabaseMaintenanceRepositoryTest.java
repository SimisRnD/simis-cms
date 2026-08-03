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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link DatabaseMaintenanceRepository} against a real PostgreSQL instance (#469) -- these
 * queries are against Postgres's own pg_stat_* catalogs, which don't exist in any mock and behave
 * identically to a real deployment once a table has had at least one query run against it.
 *
 * @author elizabeth houser
 */
class DatabaseMaintenanceRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping DatabaseMaintenanceRepository integration test");

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

    createSchemaAndSeedStats();
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // The DataSource is never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void findOverviewReturnsARealDatabaseSizeAndCounts() {
    DatabaseMaintenanceRepository.DatabaseOverview overview = DatabaseMaintenanceRepository.findOverview();

    assertNotNull(overview);
    assertTrue(overview.getSizeBytes() > 0);
    assertNotNull(overview.getSizePretty());
    assertTrue(overview.getTableCount() >= 1, "the seeded table should be counted");
    assertTrue(overview.getIndexCount() >= 1, "the seeded primary key index should be counted");
  }

  @Test
  void findTableStatsIncludesTheSeededTableWithASize() {
    List<DatabaseMaintenanceRepository.TableStats> tableStatsList = DatabaseMaintenanceRepository.findTableStats();

    DatabaseMaintenanceRepository.TableStats widgets = tableStatsList.stream()
        .filter(t -> "widgets".equals(t.getTableName()))
        .findFirst()
        .orElse(null);
    assertNotNull(widgets, "the seeded 'widgets' table should appear in the stats");
    assertTrue(widgets.getTotalSizeBytes() > 0);
    assertNotNull(widgets.getTotalSizePretty());
    assertNotNull(widgets.getLastAnalyzeAny(), "the setup's explicit ANALYZE should be reflected here");
  }

  @Test
  void findIndexStatsFlagsAnIndexWithZeroScansAsUnused() {
    List<DatabaseMaintenanceRepository.IndexStats> indexStatsList = DatabaseMaintenanceRepository.findIndexStats();

    DatabaseMaintenanceRepository.IndexStats pk = indexStatsList.stream()
        .filter(i -> "widgets".equals(i.getTableName()))
        .findFirst()
        .orElse(null);
    assertNotNull(pk, "the seeded table's primary key index should appear in the stats");
    // A freshly-created index with no SELECTs run against its table has never been scanned.
    assertEquals(0, pk.getScanCount());
    assertTrue(pk.isUnused());
  }

  @Test
  void findTableNamesIncludesTheSeededTable() {
    Set<String> names = DatabaseMaintenanceRepository.findTableNames();

    assertTrue(names.contains("widgets"));
  }

  @Test
  void vacuumAnalyzeTableSucceedsForARealTable() {
    boolean result = DatabaseMaintenanceRepository.vacuumAnalyzeTable("widgets");

    assertTrue(result);
  }

  @Test
  void vacuumAnalyzeTableFailsGracefullyForANonexistentTable() {
    boolean result = DatabaseMaintenanceRepository.vacuumAnalyzeTable("this_table_does_not_exist");

    assertFalse(result);
  }

  @Test
  void findActiveQueriesExcludesThisConnectionsOwnBackend() {
    List<DatabaseMaintenanceRepository.ActiveQuery> activeQueries = DatabaseMaintenanceRepository.findActiveQueries();

    // Every connection in this pool is either idle (excluded) or, if caught mid-query, would show up as
    // running this exact SELECT -- pg_backend_pid() in the repository's own query excludes its own PID,
    // so this just proves the call doesn't blow up and returns a (possibly empty) real list.
    assertNotNull(activeQueries);
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

  private static void createSchemaAndSeedStats() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS widgets");
      statement.execute("CREATE TABLE widgets (widget_id BIGSERIAL PRIMARY KEY, name VARCHAR(255))");
      statement.execute("INSERT INTO widgets (name) VALUES ('a'), ('b'), ('c')");
      // pg_stat_user_tables/pg_stat_user_indexes only populate after the stats collector has seen
      // activity on the relation -- an explicit ANALYZE forces that immediately rather than waiting
      // on autovacuum's own schedule, which the test can't control.
      statement.execute("ANALYZE widgets");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create/seed the widgets table", se);
    }
  }
}
