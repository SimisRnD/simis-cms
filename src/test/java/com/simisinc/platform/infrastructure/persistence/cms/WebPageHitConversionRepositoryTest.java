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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
 * Verifies WebPageHitRepository.countPageViews (issue #563 -- conversion-rate tracking) against a real
 * PostgreSQL instance: it must scope to the exact page path, the date range, and exclude bot sessions,
 * mirroring findTopPaths' existing bot-exclusion query shape. Kept separate from the lightweight,
 * no-Docker-needed WebPageHitRepositoryTest (retention-window parsing only).
 *
 * @author SimIS Inc.
 * @created 7/28/26
 */
class WebPageHitConversionRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebPageHitRepository conversion-tracking integration test");

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
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE web_page_hits RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE sessions RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void countPageViewsOnlyCountsTheMatchingPathInRange() {
    addHit("/contact-us", "session-a", 0);
    addHit("/contact-us", "session-b", 0);
    addHit("/pricing", "session-c", 0);
    addHit("/contact-us", "session-d", 40); // outside the range

    long views = WebPageHitRepository.countPageViews("/contact-us", daysAgo(30), inTheFuture());

    assertEquals(2, views);
  }

  @Test
  void countPageViewsExcludesBotSessions() {
    addHit("/contact-us", "session-real", 0);
    addSession("session-bot", true);
    addHit("/contact-us", "session-bot", 0);

    long views = WebPageHitRepository.countPageViews("/contact-us", daysAgo(30), inTheFuture());

    assertEquals(1, views, "the bot-flagged session's hit should be excluded");
  }

  private static void addHit(String pagePath, String sessionId, int daysAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO web_page_hits (page_path, session_id, hit_date) VALUES (?, ?, NOW() - (? || ' days')::interval)")) {
      pst.setString(1, pagePath);
      pst.setString(2, sessionId);
      pst.setInt(3, daysAgo);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a web_page_hits row", se);
    }
  }

  private static void addSession(String sessionId, boolean isBot) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO sessions (session_id, is_bot) VALUES (?, ?)")) {
      pst.setString(1, sessionId);
      pst.setBoolean(2, isBot);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a sessions row", se);
    }
  }

  private static Timestamp daysAgo(int days) {
    return new Timestamp(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);
  }

  /**
   * countPageViews' upper bound is exclusive ({@code hit_date < endDate}), and rows are inserted
   * using the database server's own NOW(), not the JVM's clock -- comparing against a Java-computed
   * "now" taken strictly after those inserts is a real race under clock skew or CI latency between
   * the container and the test host. A few minutes of headroom removes that race without weakening
   * what the test actually verifies (the 40-day-old row is still 30+ days outside the lower bound).
   */
  private static Timestamp inTheFuture() {
    return new Timestamp(System.currentTimeMillis() + Duration.ofMinutes(5).toMillis());
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
    // Focused subsets of web_page_hits and sessions -- just enough for the bot-exclusion join.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_page_hits CASCADE");
      statement.execute("CREATE TABLE web_page_hits ("
          + "hit_id BIGSERIAL PRIMARY KEY, "
          + "method VARCHAR(6), "
          + "page_path VARCHAR(255), "
          + "web_page_id BIGINT, "
          + "ip_address VARCHAR(200), "
          + "hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "session_id VARCHAR(255), "
          + "is_logged_in BOOLEAN DEFAULT FALSE)");

      statement.execute("DROP TABLE IF EXISTS sessions CASCADE");
      statement.execute("CREATE TABLE sessions ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "session_id VARCHAR(255), "
          + "is_bot BOOLEAN DEFAULT FALSE)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the schema", se);
    }
  }
}
