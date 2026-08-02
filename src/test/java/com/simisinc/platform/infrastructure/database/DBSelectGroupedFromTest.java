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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.dashboard.StatisticsData;

/**
 * Verifies {@link DB#selectGroupedFrom} (issue #637) against a real PostgreSQL instance: grouping and
 * counting, WHERE-clause filtering combined with GROUP BY, ordering, LIMIT enforcement, and that a
 * WHERE value is genuinely bound as a query parameter rather than concatenated into the SQL text --
 * mirroring the pattern established by ImageRepositorySearchTest/SearchAnalyticsRepositoryTest.
 * Skipped automatically when Docker is not available.
 */
class DBSelectGroupedFromTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String TABLE_NAME = "db_grouped_from_test";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping DB.selectGroupedFrom test");

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

    createSchema();
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

  @BeforeEach
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE " + TABLE_NAME + " RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset " + TABLE_NAME + " table", se);
    }
  }

  @Test
  void groupsAndCountsRowsByColumn() {
    insertEvent("alpha", "active");
    insertEvent("alpha", "active");
    insertEvent("alpha", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("gamma", "active");

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", null, null, 0);

    assertEquals(3, results.size());
    assertEquals("3", valueFor(results, "alpha"));
    assertEquals("2", valueFor(results, "beta"));
    assertEquals("1", valueFor(results, "gamma"));
  }

  @Test
  void whereClauseFiltersRowsBeforeGrouping() {
    insertEvent("alpha", "active");
    insertEvent("alpha", "active");
    insertEvent("alpha", "archived");
    insertEvent("beta", "active");
    insertEvent("beta", "archived");
    insertEvent("beta", "archived");
    insertEvent("beta", "archived");
    insertEvent("gamma", "archived");
    insertEvent("gamma", "archived");

    SqlUtils where = new SqlUtils().add("status = ?", "active");

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", where, null, 0);

    // gamma has no "active" rows at all -- filtering must drop it out of the grouping entirely,
    // not just zero out its count (an unfiltered GROUP BY would still return all 3 categories)
    assertEquals(2, results.size());
    assertEquals("2", valueFor(results, "alpha"));
    assertEquals("1", valueFor(results, "beta"));
  }

  @Test
  void orderByRanksHighestCountFirst() {
    insertEvent("alpha", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("gamma", "active");
    insertEvent("gamma", "active");

    SqlUtils orderBy = new SqlUtils().add("total DESC");

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", null, orderBy, 0);

    assertEquals(3, results.size());
    assertEquals("beta", results.get(0).getLabel());
    assertEquals("4", results.get(0).getValue());
    assertEquals("gamma", results.get(1).getLabel());
    assertEquals("2", results.get(1).getValue());
    assertEquals("alpha", results.get(2).getLabel());
    assertEquals("1", results.get(2).getValue());
  }

  @Test
  void recordLimitCapsTheNumberOfGroupsReturned() {
    insertEvent("alpha", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("beta", "active");
    insertEvent("gamma", "active");
    insertEvent("gamma", "active");
    insertEvent("delta", "active");
    insertEvent("delta", "active");
    insertEvent("delta", "active");

    SqlUtils orderBy = new SqlUtils().add("total DESC");

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", null, orderBy, 2);

    assertEquals(2, results.size(), "recordLimit=2 must cap the number of groups returned");
    assertEquals("beta", results.get(0).getLabel());
    assertEquals("delta", results.get(1).getLabel());
  }

  @Test
  void zeroOrNegativeRecordLimitMeansNoLimit() {
    for (int i = 0; i < 5; i++) {
      insertEvent("category-" + i, "active");
    }

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", null, null, 0);

    assertEquals(5, results.size());
  }

  @Test
  void whereClauseValueIsBoundAsAParameterNotConcatenatedIntoTheQuery() {
    // If the WHERE value were concatenated into the SQL text instead of bound as a placeholder, this
    // payload would either break the query (SQLException) or -- far worse -- actually execute the
    // injected statement. Neither happens: it is treated as a literal, no-match string, and the
    // GROUP BY still runs cleanly on top of the (empty) filtered result.
    insertEvent("alpha", "active");
    insertEvent("beta", "active");

    SqlUtils where = new SqlUtils().add("category = ?", "x'; DROP TABLE " + TABLE_NAME + "; --");

    List<StatisticsData> results = DB.selectGroupedFrom(TABLE_NAME, "category", "total", where, null, 0);

    assertTrue(results.isEmpty(), "the payload must be treated as a literal filter value, matching nothing");
    // The table must still exist and still hold every seeded row -- proving DROP TABLE never ran
    assertEquals(2, DB.selectCountFrom(TABLE_NAME));
  }

  private static String valueFor(List<StatisticsData> results, String label) {
    return results.stream()
        .filter(r -> label.equals(r.getLabel()))
        .findFirst()
        .map(StatisticsData::getValue)
        .orElseThrow(() -> new AssertionError("No result for label " + label));
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

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS " + TABLE_NAME + " CASCADE");
      statement.execute("CREATE TABLE " + TABLE_NAME + " ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "category VARCHAR(255) NOT NULL, "
          + "status VARCHAR(50) NOT NULL DEFAULT 'active')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the " + TABLE_NAME + " schema", se);
    }
  }

  private static void insertEvent(String category, String status) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO " + TABLE_NAME + " (category, status) VALUES (?, ?)")) {
      pst.setString(1, category);
      pst.setString(2, status);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test row", se);
    }
  }
}
