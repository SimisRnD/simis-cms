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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the new bot-traffic-reporting methods (issue #561) against a real PostgreSQL instance.
 * Minimal schema replicated from {@code NEW_10000__new_database.sql} -- the sessions table only.
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
class SessionBotTrafficRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping bot traffic integration test");

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
  void clearTable() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping bot traffic integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE sessions");
    }
  }

  @Test
  void countBotSessionsOnlyCountsBotsWithinTheRange() throws SQLException {
    Instant now = Instant.now();
    seedSession(true, now);
    seedSession(true, now);
    seedSession(false, now); // real -- must not be counted
    seedSession(true, now.minus(10, ChronoUnit.DAYS)); // bot, but outside the range

    Timestamp start = Timestamp.from(now.minus(1, ChronoUnit.DAYS));
    Timestamp end = Timestamp.from(now.plus(1, ChronoUnit.DAYS));

    assertEquals(2, SessionRepository.countBotSessions(start, end));
  }

  @Test
  void findDailySessionsByBotStatusZeroFillsDaysWithNoMatchingSessions() throws SQLException {
    Instant today = Instant.now();
    seedSession(true, today);
    seedSession(true, today);
    seedSession(false, today);

    List<StatisticsData> botSeries = SessionRepository.findDailySessionsByBotStatus(7, 'd', true);
    List<StatisticsData> realSeries = SessionRepository.findDailySessionsByBotStatus(7, 'd', false);

    assertEquals(8, botSeries.size(), "7 days plus today, inclusive, zero-filled");
    assertEquals(8, realSeries.size(), "7 days plus today, inclusive, zero-filled");

    // Deliberately not asserted as the *last* bucket. The rows land in the bucket for the date they
    // were written, while the series' last day comes from NOW() at query time -- if local midnight
    // falls in between, they sit one bucket to the left and an index-based assertion fails for a
    // reason that has nothing to do with zero-filling (issue #1738). What this test is about is
    // that exactly one bucket carries the count and every other day is present and zero rather
    // than dropped, which holds whichever calendar day the query runs on.
    assertCountInASingleZeroFilledSeries(botSeries, "2");
    assertCountInASingleZeroFilledSeries(realSeries, "1");
  }

  /**
   * Asserts the series carries its whole count in exactly one bucket, with every other bucket
   * present and explicitly "0" -- the zero-fill guarantee, stated without depending on which
   * calendar day the query happens to run on. A dropped day would fail the size assertion at the
   * call site; a day zero-filled as null or absent would show up here.
   */
  private static void assertCountInASingleZeroFilledSeries(List<StatisticsData> series, String expectedCount) {
    List<String> populatedDays = series.stream()
        .map(StatisticsData::getValue)
        .filter(value -> !"0".equals(value))
        .toList();

    // StatisticsData has no toString(), so render the series by hand -- otherwise a failure prints
    // a list of object hashes and says nothing about which day went wrong.
    String rendered = series.stream()
        .map(data -> data.getLabel() + "=" + data.getValue())
        .collect(Collectors.joining(", ", "[", "]"));

    assertEquals(List.of(expectedCount), populatedDays,
        "expected every day zero-filled except exactly one holding " + expectedCount + ": " + rendered);
  }

  @Test
  void findDailySessionsByBotStatusOrdersOldestToNewest() throws SQLException {
    List<StatisticsData> series = SessionRepository.findDailySessionsByBotStatus(7, 'd', false);
    assertTrue(series.size() >= 2);
    assertTrue(series.get(0).getLabel().compareTo(series.get(series.size() - 1).getLabel()) < 0,
        "expected the series ordered oldest to newest: " + series);
  }

  @Test
  void findDailySessionsByBotStatusHonoursTheIntervalUnit() throws SQLException {
    // The report drop-down can ask for "3m" as readily as "90d"; both have to reach the INTERVAL
    // literal as a real unit rather than silently being read as days
    List<StatisticsData> threeMonths = SessionRepository.findDailySessionsByBotStatus(3, 'm', false);
    List<StatisticsData> threeDays = SessionRepository.findDailySessionsByBotStatus(3, 'd', false);

    assertEquals(4, threeDays.size(), "3 days plus today, inclusive");
    assertTrue(threeMonths.size() > 80,
        "expected roughly a quarter of daily points for a 3-month window, got " + threeMonths.size());
  }

  private void seedSession(boolean isBot, Instant created) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO sessions (session_id, created, is_bot, is_anonymous) VALUES ("
          + "'" + UUID.randomUUID() + "', '" + Timestamp.from(created) + "', " + isBot + ", false)");
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE sessions ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "session_id VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "is_bot BOOLEAN DEFAULT false, "
          + "is_anonymous BOOLEAN NOT NULL DEFAULT false)");
      statement.execute("CREATE INDEX sessions_created_idx ON sessions(created)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the sessions test schema", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
