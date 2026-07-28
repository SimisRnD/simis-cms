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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Integration test for {@link SessionRepository#scrubOldPii(int)} against a real PostgreSQL
 * (Testcontainers). A pure unit test cannot catch this class of bug: the original implementation
 * looked correct in review but threw a NOT NULL constraint violation on every real run (sessions
 * table, ip_address column), which a Postgres-unaware mock of DB would never surface. Skipped when
 * no Docker daemon is reachable.
 *
 * @author SimIS Inc.
 */
class SessionsPiiScrubIntegrationTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SessionsPiiScrub integration test");

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
    execute("TRUNCATE TABLE sessions RESTART IDENTITY");
  }

  @Test
  void scrubsPiiFromSessionsOlderThanTheRetentionWindow() {
    long oldId = insertSession(200, "203.0.113.5", "San Francisco", 37.7749, -122.4194);
    long recentId = insertSession(5, "203.0.113.9", "Portland", 45.5152, -122.6784);

    int scrubbed = SessionRepository.scrubOldPii(90);

    assertEquals(1, scrubbed, "only the session past the retention window is scrubbed");

    SessionRow old = fetch(oldId);
    assertNull(old.ipAddress, "ip_address must be nulled -- this is the exact constraint that used to reject the UPDATE");
    assertNull(old.city);
    assertNull(old.postalCode);
    assertNull(old.latitude);
    assertNull(old.longitude);
    assertEquals("United States", old.country, "country is retained for aggregate analytics");
    assertEquals("US", old.countryIso);

    SessionRow recent = fetch(recentId);
    assertNotNull(recent.ipAddress, "a session inside the retention window must not be touched");
    assertNotNull(recent.city);
  }

  @Test
  void isIdempotentOnAlreadyScrubbedRows() {
    long oldId = insertSession(200, "203.0.113.5", "San Francisco", 37.7749, -122.4194);

    int firstRun = SessionRepository.scrubOldPii(90);
    assertEquals(1, firstRun);

    int secondRun = SessionRepository.scrubOldPii(90);
    assertEquals(0, secondRun, "a row with ip_address already NULL must not be counted again");

    assertNull(fetch(oldId).ipAddress);
  }

  @Test
  void aZeroOrNegativeRetentionWindowScrubsNothing() {
    insertSession(200, "203.0.113.5", "San Francisco", 37.7749, -122.4194);

    assertEquals(0, SessionRepository.scrubOldPii(0));
    assertEquals(0, SessionRepository.scrubOldPii(-1));
  }

  private static long insertSession(int ageInDays, String ipAddress, String city, double latitude, double longitude) {
    String sql = "INSERT INTO sessions "
        + "(session_id, created, ip_address, city, postal_code, latitude, longitude, country, country_iso) "
        + "VALUES (?, NOW() - (? || ' days')::interval, ?, ?, '94103', ?, ?, 'United States', 'US') "
        + "RETURNING id";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, "session-" + System.nanoTime());
      pst.setInt(2, ageInDays);
      pst.setString(3, ipAddress);
      pst.setString(4, city);
      pst.setDouble(5, latitude);
      pst.setDouble(6, longitude);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Insert failed: " + sql, se);
    }
  }

  private record SessionRow(String ipAddress, String city, String postalCode, Double latitude, Double longitude,
      String country, String countryIso) {
  }

  private static SessionRow fetch(long id) {
    String sql = "SELECT ip_address, city, postal_code, latitude, longitude, country, country_iso "
        + "FROM sessions WHERE id = ?";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setLong(1, id);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        Double latitude = rs.getObject("latitude") != null ? rs.getDouble("latitude") : null;
        Double longitude = rs.getObject("longitude") != null ? rs.getDouble("longitude") : null;
        return new SessionRow(rs.getString("ip_address"), rs.getString("city"), rs.getString("postal_code"),
            latitude, longitude, rs.getString("country"), rs.getString("country_iso"));
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Fetch failed: " + sql, se);
    }
  }

  private static void execute(String sql) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException se) {
      throw new IllegalStateException("SQL failed: " + sql, se);
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
    // The sessions table as defined by the install migration (post-fix: ip_address is nullable).
    execute("DROP TABLE IF EXISTS sessions CASCADE");
    execute("CREATE TABLE sessions ("
        + "id BIGSERIAL PRIMARY KEY, "
        + "session_id VARCHAR(255), "
        + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
        + "ip_address VARCHAR(200), "
        + "user_agent VARCHAR(255), "
        + "referer VARCHAR(255), "
        + "continent VARCHAR(20), "
        + "country_iso VARCHAR(2), "
        + "country VARCHAR(100), "
        + "city VARCHAR(100), "
        + "state_iso VARCHAR(3), "
        + "state VARCHAR(100), "
        + "postal_code VARCHAR(50), "
        + "timezone VARCHAR(50), "
        + "latitude float, "
        + "longitude float, "
        + "metro_code INTEGER, "
        + "source VARCHAR(50), "
        + "app_id BIGINT, "
        + "visitor_id BIGINT, "
        + "is_bot BOOLEAN DEFAULT FALSE, "
        + "is_anonymous BOOLEAN DEFAULT FALSE)");
  }
}
