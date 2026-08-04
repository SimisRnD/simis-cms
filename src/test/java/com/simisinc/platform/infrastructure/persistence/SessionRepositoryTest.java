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
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Tests parsing of the geo-anomaly alert's configurable windows, plus (for
 * {@link #findTopCountriesByCount}) a real-Postgres integration test -- the geographic-anomaly
 * dashboard tile added for issue #569 slice 2.
 *
 * @author elizabeth houser
 */
class SessionRepositoryTest {

  @Test
  void resolveGeoAnomalyBaselineDaysFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays(null));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays(""));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays("not-a-number"));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays("30; DROP TABLE sessions"));
  }

  @Test
  void resolveGeoAnomalyBaselineDaysUsesConfiguredValueAndBounds() {
    assertEquals(90, SessionRepository.resolveGeoAnomalyBaselineDays("90"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyBaselineDays("0"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyBaselineDays("-5"));
    assertEquals(365, SessionRepository.resolveGeoAnomalyBaselineDays("999999"));
  }

  @Test
  void resolveGeoAnomalyRecentHoursFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours(null));
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours(""));
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours("not-a-number"));
  }

  @Test
  void resolveGeoAnomalyRecentHoursUsesConfiguredValueAndBounds() {
    assertEquals(6, SessionRepository.resolveGeoAnomalyRecentHours("6"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyRecentHours("0"));
    assertEquals(168, SessionRepository.resolveGeoAnomalyRecentHours("999999"));
  }

  // --- findTopCountriesByCount() integration coverage (issue #569 slice 2) ---

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SessionRepository integration test");

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
      statement.execute("TRUNCATE TABLE sessions RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset sessions table", se);
    }
  }

  @Test
  void findTopCountriesByCountOrdersByDescendingSessionCount() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, now);
    seedSession("Brazil", false, false, now);
    seedSession("Brazil", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Mexico", false, false, now);

    // Use a comfortably-future upper bound, not a freshly-computed now() -- otherwise this is flaky:
    // findTopCountriesByCount's upper bound is exclusive (created < endDate), so if a second
    // System.currentTimeMillis() call happens to land in the same millisecond as the seeded rows'
    // "now" (or earlier, under scheduling jitter), they'd be excluded by their own query's boundary.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(3, results.size());
    assertEquals("Brazil", results.get(0).getLabel());
    assertEquals("3", results.get(0).getValue());
    assertEquals("Canada", results.get(1).getLabel());
    assertEquals("Mexico", results.get(2).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesBotSessions() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", true, false, now);
    seedSession("Brazil", true, false, now);
    seedSession("Brazil", true, false, now);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "Brazil's bot sessions must not count at all: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesSessionsWithNoResolvedCountry() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession(null, false, false, now);
    seedSession(null, false, false, now);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "sessions with no resolved country can't be attributed to any country: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountIncludesAnonymousSessions() {
    // GeoIPCommand/SaveSessionCommand populate country for every session, anonymous or not --
    // only city/postal/lat/long are anonymous-restricted. Filtering on is_anonymous here would
    // silently undercount and could hide a real anomaly made up mostly of anonymous traffic.
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, true, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "an anonymous session's country must still count: " + results);
    assertEquals("Brazil", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesSessionsOutsideTheWindow() {
    Timestamp twoHoursAgo = Timestamp.from(Instant.now().minus(Duration.ofHours(2)));
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, twoHoursAgo);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "the session outside the 1-hour window must not count: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), now(), 5);

    assertTrue(results.isEmpty());
  }

  @Test
  void findTopCountriesByCountRespectsTheRecordLimit() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Mexico", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 2);

    assertEquals(2, results.size());
  }

  private static Timestamp hoursAgo(int hours) {
    return Timestamp.from(Instant.now().minus(Duration.ofHours(hours)));
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }

  /**
   * A query upper bound guaranteed to be strictly after anything seeded with {@code now()} in this
   * test class, without relying on two separate {@code System.currentTimeMillis()} calls landing in
   * different milliseconds. findTopCountriesByCount's upper bound is exclusive, so an upper bound
   * that isn't comfortably ahead of the seeded data is a real source of flakiness, not just a
   * theoretical one.
   */
  private static Timestamp future() {
    return Timestamp.from(Instant.now().plusSeconds(60));
  }

  private static void seedSession(String country, boolean isBot, boolean isAnonymous, Timestamp created) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String countryValue = country == null ? "NULL" : "'" + country + "'";
      statement.execute("INSERT INTO sessions (country, is_bot, is_anonymous, created) VALUES ("
          + countryValue + ", " + isBot + ", " + isAnonymous + ", '" + created + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed session", se);
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
    // A focused subset of the real sessions schema -- enough for findTopCountriesByCount's
    // window/bot/country filtering. FK constraints to unrelated tables are omitted.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS sessions CASCADE");
      statement.execute("CREATE TABLE sessions ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "session_id VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "country VARCHAR(100), "
          + "is_bot BOOLEAN DEFAULT false, "
          + "is_anonymous BOOLEAN NOT NULL DEFAULT false)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the sessions schema", se);
    }
  }
}
