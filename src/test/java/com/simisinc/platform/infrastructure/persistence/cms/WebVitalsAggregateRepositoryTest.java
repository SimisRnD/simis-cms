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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
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

import com.simisinc.platform.domain.model.cms.WebVitalsAggregate;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebVitalsAggregateRepository#findAggregates} and
 * {@link WebVitalsAggregateRepository#findDistinctUrls} -- the trend chart's data source (issue
 * #762) -- against a real PostgreSQL instance.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack, mirroring
 * {@code ContentRepositoryTest}. It is skipped automatically when Docker is not available.
 * </p>
 *
 * @author claude
 * @created 7/31/26
 */
class WebVitalsAggregateRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebVitalsAggregateRepository integration test");

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
      // Never initialized when Docker is unavailable
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
      statement.execute("TRUNCATE TABLE web_vitals_aggregates RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_vitals_aggregates table", se);
    }
  }

  @Test
  void findAggregatesReturnsOnlyTheRequestedUrlAndMetricOldestFirst() throws SQLException {
    insertAggregate("/pricing", "LCP", 1200, 2400, 3600, "3 days");
    insertAggregate("/pricing", "LCP", 1300, 2500, 3700, "1 days");
    insertAggregate("/pricing", "LCP", 1100, 2300, 3500, "2 days");
    // Different metric, same URL -- must not leak into the LCP series
    insertAggregate("/pricing", "CLS", 5, 8, 12, "1 days");
    // Different URL, same metric -- must not leak either
    insertAggregate("/checkout", "LCP", 2000, 3000, 4000, "1 days");

    List<WebVitalsAggregate> series = WebVitalsAggregateRepository.findAggregates("/pricing", "LCP", 30);

    assertEquals(3, series.size());
    // Oldest (3 days ago) first, newest (1 day ago) last
    assertEquals(2400, series.get(0).getP75Value());
    assertEquals(2300, series.get(1).getP75Value());
    assertEquals(2500, series.get(2).getP75Value());
    for (WebVitalsAggregate row : series) {
      assertEquals("/pricing", row.getUrl());
      assertEquals("LCP", row.getMetricType());
    }
  }

  @Test
  void findAggregatesExcludesRowsOutsideTheRequestedWindow() throws SQLException {
    insertAggregate("/pricing", "LCP", 1200, 2400, 3600, "5 days");
    insertAggregate("/pricing", "LCP", 1300, 2500, 3700, "45 days");

    List<WebVitalsAggregate> last7Days = WebVitalsAggregateRepository.findAggregates("/pricing", "LCP", 7);
    List<WebVitalsAggregate> last90Days = WebVitalsAggregateRepository.findAggregates("/pricing", "LCP", 90);

    assertEquals(1, last7Days.size(), "the 45-day-old row must not appear in a 7-day window");
    assertEquals(2400, last7Days.get(0).getP75Value());
    assertEquals(2, last90Days.size(), "both rows fall inside a 90-day window");
  }

  @Test
  void findAggregatesReturnsAnEmptyListForABlankUrlOrMetric() {
    assertTrue(WebVitalsAggregateRepository.findAggregates("", "LCP", 30).isEmpty());
    assertTrue(WebVitalsAggregateRepository.findAggregates("/pricing", "", 30).isEmpty());
    assertTrue(WebVitalsAggregateRepository.findAggregates(null, "LCP", 30).isEmpty());
  }

  @Test
  void findDistinctUrlsReturnsSortedUniqueUrlsWithinTheWindow() throws SQLException {
    insertAggregate("/pricing", "LCP", 1200, 2400, 3600, "1 days");
    insertAggregate("/pricing", "CLS", 5, 8, 12, "2 days"); // same URL, different metric -- one entry
    insertAggregate("/checkout", "LCP", 2000, 3000, 4000, "1 days");
    insertAggregate("/archived", "LCP", 900, 1200, 1800, "120 days"); // outside a 90-day window

    List<String> urls = WebVitalsAggregateRepository.findDistinctUrls(90);

    assertEquals(List.of("/checkout", "/pricing"), urls);
  }

  @Test
  void boundDaysClampsToTheSupportedRange() {
    assertEquals(1, WebVitalsAggregateRepository.boundDays(0));
    assertEquals(1, WebVitalsAggregateRepository.boundDays(-5));
    assertEquals(7, WebVitalsAggregateRepository.boundDays(7));
    assertEquals(90, WebVitalsAggregateRepository.boundDays(90));
    assertEquals(90, WebVitalsAggregateRepository.boundDays(365));
  }

  private static void insertAggregate(String url, String metricType, double p50, double p75, double p95,
      String ageInterval) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_vitals_aggregates "
          + "(url, metric_type, p50_value, p75_value, p95_value, sample_count, aggregated_at) VALUES ("
          + "'" + url + "', '" + metricType + "', " + p50 + ", " + p75 + ", " + p95 + ", 10, "
          + "NOW() - INTERVAL '" + ageInterval + "')");
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

  private static void createSchema() {
    // A focused subset of the real web_vitals_aggregates table (matches
    // UPGRADE_20260727.1001__web_vitals_context_and_aggregates.sql) -- enough for the
    // findAggregates/findDistinctUrls read paths under test.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_vitals_aggregates CASCADE");
      statement.execute("CREATE TABLE web_vitals_aggregates ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "url VARCHAR(2048) NOT NULL, "
          + "metric_type VARCHAR(50) NOT NULL, "
          + "p50_value NUMERIC(10, 2), "
          + "p75_value NUMERIC(10, 2), "
          + "p95_value NUMERIC(10, 2), "
          + "sample_count INTEGER NOT NULL DEFAULT 0, "
          + "aggregated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "UNIQUE (url, metric_type, aggregated_at))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_vitals_aggregates schema", se);
    }
  }
}
