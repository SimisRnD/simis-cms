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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
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
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Covers the five new count queries added for the actionable-admin-dashboard work (issue #476):
 * {@link UserRepository#countLockedAccounts()}, {@link ContentRepository#countByDraftStatus(String)},
 * {@link WebPageRepository#countScheduledNotYetLive()}, {@link SessionRepository#countDistinctBotSessions},
 * and {@link FormDataRepository#countAwaitingReview()}. Each is a small, real WHERE clause against a
 * real Postgres -- exactly the kind of thing that silently returns the wrong number forever if only
 * unit-tested against a mock.
 *
 * <p>
 * Minimal focused schema, not the real install script -- only the columns each method's WHERE clause
 * touches, matching this repo's established Testcontainers test convention.
 * </p>
 *
 * @author SimIS
 * @created 7/28/2026
 */
class DashboardMetricsRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping DashboardMetricsRepository integration test");

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
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE users, content, web_pages, sessions, form_data RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset dashboard-metrics tables", se);
    }
  }

  @Test
  void countLockedAccountsOnlyCountsAccountsLockedInTheFuture() throws SQLException {
    insertUser(Timestamp.from(java.time.Instant.now().plusSeconds(600))); // still locked
    insertUser(Timestamp.from(java.time.Instant.now().minusSeconds(600))); // lock already expired
    insertUser(null); // never locked

    assertEquals(1, UserRepository.countLockedAccounts());
  }

  @Test
  void countByDraftStatusMatchesOnlyTheRequestedStatus() throws SQLException {
    insertContent("submitted-1", "submitted");
    insertContent("submitted-2", "submitted");
    insertContent("draft-1", "draft");
    insertContent("published-1", null);

    assertEquals(2, ContentRepository.countByDraftStatus("submitted"));
    assertEquals(1, ContentRepository.countByDraftStatus("draft"));
  }

  @Test
  void countScheduledNotYetLiveExcludesPastAndUnscheduledPages() throws SQLException {
    insertWebPage("/future-1", Timestamp.from(java.time.Instant.now().plusSeconds(3600))); // scheduled, not live yet
    insertWebPage("/future-2", Timestamp.from(java.time.Instant.now().plusSeconds(7200))); // scheduled, not live yet
    insertWebPage("/already-live", Timestamp.from(java.time.Instant.now().minusSeconds(3600))); // publish_at in the past
    insertWebPage("/no-schedule", null); // never scheduled

    assertEquals(2, WebPageRepository.countScheduledNotYetLive());
  }

  @Test
  void countDistinctBotSessionsOnlyCountsBotsInRange() throws SQLException {
    Timestamp startOfToday = Timestamp.valueOf(LocalDate.now().atStartOfDay());
    Timestamp insertedAt = new Timestamp(System.currentTimeMillis());
    // The end bound is exclusive (created < endDate), matching countDistinctSessions' convention --
    // query strictly after the inserted rows' timestamp, the way the real caller naturally does by
    // querying "now" some time after the rows were written.
    Timestamp queryEnd = Timestamp.from(insertedAt.toInstant().plusSeconds(60));
    Timestamp yesterday = Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(1)));

    insertSession("bot-1", insertedAt, true);
    insertSession("bot-2", insertedAt, true);
    insertSession("human-1", insertedAt, false); // real visitor, must not be counted
    insertSession("bot-old", yesterday, true); // outside the date range

    assertEquals(2, SessionRepository.countDistinctBotSessions(startOfToday, queryEnd));
  }

  @Test
  void countAwaitingReviewExcludesProcessedAndDismissedSubmissions() throws SQLException {
    insertFormData(null, null); // awaiting review
    insertFormData(null, null); // awaiting review
    insertFormData(new Timestamp(System.currentTimeMillis()), null); // already processed
    insertFormData(null, new Timestamp(System.currentTimeMillis())); // dismissed

    assertEquals(2, FormDataRepository.countAwaitingReview());
  }

  private static void insertUser(Timestamp lockedUntil) throws SQLException {
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection.prepareStatement("INSERT INTO users (locked_until) VALUES (?)")) {
      pst.setTimestamp(1, lockedUntil);
      pst.executeUpdate();
    }
  }

  private static void insertContent(String uniqueId, String draftStatus) throws SQLException {
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection
            .prepareStatement("INSERT INTO content (content_unique_id, draft_status) VALUES (?, ?)")) {
      pst.setString(1, uniqueId);
      pst.setString(2, draftStatus);
      pst.executeUpdate();
    }
  }

  private static void insertWebPage(String link, Timestamp publishAt) throws SQLException {
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection
            .prepareStatement("INSERT INTO web_pages (link, publish_at) VALUES (?, ?)")) {
      pst.setString(1, link);
      pst.setTimestamp(2, publishAt);
      pst.executeUpdate();
    }
  }

  private static void insertSession(String sessionId, Timestamp created, boolean isBot) throws SQLException {
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection
            .prepareStatement("INSERT INTO sessions (session_id, created, is_bot) VALUES (?, ?, ?)")) {
      pst.setString(1, sessionId);
      pst.setTimestamp(2, created);
      pst.setBoolean(3, isBot);
      pst.executeUpdate();
    }
  }

  private static void insertFormData(Timestamp processed, Timestamp dismissed) throws SQLException {
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection
            .prepareStatement("INSERT INTO form_data (processed, dismissed) VALUES (?, ?)")) {
      pst.setTimestamp(1, processed);
      pst.setTimestamp(2, dismissed);
      pst.executeUpdate();
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY, locked_until TIMESTAMP(3))");
      statement.execute("CREATE TABLE content (content_id BIGSERIAL PRIMARY KEY, "
          + "content_unique_id VARCHAR(255) UNIQUE, draft_status VARCHAR(20))");
      statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL, publish_at TIMESTAMP)");
      statement.execute("CREATE TABLE sessions (id BIGSERIAL PRIMARY KEY, session_id VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, is_bot BOOLEAN DEFAULT false)");
      statement.execute("CREATE TABLE form_data (form_data_id BIGSERIAL PRIMARY KEY, "
          + "processed TIMESTAMP(3), dismissed TIMESTAMP(3))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the dashboard-metrics test schema", se);
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
