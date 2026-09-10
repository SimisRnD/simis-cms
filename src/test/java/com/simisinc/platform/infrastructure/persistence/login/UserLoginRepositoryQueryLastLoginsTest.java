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

package com.simisinc.platform.infrastructure.persistence.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link UserLoginRepository#queryLastLogins} against a real PostgreSQL instance -- the
 * batch form added for /admin/users (UsersListWidget) so a page of users issues one last-login query
 * instead of one per row. Exercises the Postgres-specific {@code DISTINCT ON (user_id) ... ORDER BY
 * user_id, created DESC} this method relies on to keep only the most recent row per user in a single
 * pass. Modeled on {@code ImageVariantRepositoryTest}'s Testcontainers convention.
 *
 * @author SimIS Inc.
 */
class UserLoginRepositoryQueryLastLoginsTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping UserLoginRepository integration test");

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
      statement.execute("TRUNCATE TABLE user_logins, users RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset users/user_logins tables", se);
    }
  }

  @Test
  void previousActivityIgnoresTheViewersOwnCurrentSession() throws SQLException {
    // The point of the method. user_logins also collects a row on the first request of each new
    // calendar day for an already-signed-in session, so the newest row is almost always the
    // viewer's own visit -- reporting it would tell them they were last here seconds ago.
    Instant now = Instant.now();
    long user = insertUser("returning-user");
    insertLogin(user, Timestamp.from(now.minusSeconds(86400)), "1.1.1.1", "session-yesterday");
    insertLogin(user, Timestamp.from(now), "9.9.9.9", "session-now");

    UserLogin previous = UserLoginRepository.queryPreviousActivity(user, "session-now");

    assertNotNull(previous);
    assertEquals("1.1.1.1", previous.getIpAddress(),
        "the row from the current session must not be reported back as the previous visit");
  }

  @Test
  void previousActivityIsNullOnAFirstEverVisit() throws SQLException {
    Instant now = Instant.now();
    long user = insertUser("brand-new-user");
    insertLogin(user, Timestamp.from(now), "9.9.9.9", "session-now");

    assertNull(UserLoginRepository.queryPreviousActivity(user, "session-now"),
        "a first visit has no previous one, and must not fall back to showing this one");
  }

  @Test
  void previousActivityKeepsRowsWithNoSessionId() throws SQLException {
    // Older rows may predate session tracking; they cannot be this session, so they still count
    Instant now = Instant.now();
    long user = insertUser("legacy-rows-user");
    insertLogin(user, Timestamp.from(now.minusSeconds(7200)), "1.1.1.1", null);
    insertLogin(user, Timestamp.from(now), "9.9.9.9", "session-now");

    UserLogin previous = UserLoginRepository.queryPreviousActivity(user, "session-now");

    assertNotNull(previous);
    assertEquals("1.1.1.1", previous.getIpAddress());
  }

  @Test
  void queryLastLoginsReturnsOnlyTheMostRecentLoginPerUser() throws SQLException {
    long user1 = insertUser("user-one");
    long user2 = insertUser("user-two");
    long user3 = insertUser("user-three"); // never logged in -- must not appear in the returned map at all
    Instant now = Instant.now();
    insertLogin(user1, Timestamp.from(now.minusSeconds(3600)), "1.1.1.1");
    insertLogin(user1, Timestamp.from(now), "9.9.9.9"); // the most recent for user1
    insertLogin(user2, Timestamp.from(now.minusSeconds(60)), "2.2.2.2");

    Map<Long, UserLogin> lastLoginByUserId = UserLoginRepository.queryLastLogins(List.of(user1, user2, user3));

    assertEquals("9.9.9.9", lastLoginByUserId.get(user1).getIpAddress(),
        "must keep the most recent row per user, not an arbitrary one");
    assertEquals("2.2.2.2", lastLoginByUserId.get(user2).getIpAddress());
    assertTrue(!lastLoginByUserId.containsKey(user3), "a user with zero logins must not get a map entry");
  }

  @Test
  void queryLastLoginsOnlyReturnsLoginsForTheRequestedIds() throws SQLException {
    long requested = insertUser("requested");
    long notRequested = insertUser("not-requested");
    insertLogin(requested, new Timestamp(System.currentTimeMillis()), "1.1.1.1");
    insertLogin(notRequested, new Timestamp(System.currentTimeMillis()), "2.2.2.2");

    Map<Long, UserLogin> lastLoginByUserId = UserLoginRepository.queryLastLogins(List.of(requested));

    assertEquals(1, lastLoginByUserId.size(), "a user id outside the requested set must not leak into the result");
    assertTrue(lastLoginByUserId.containsKey(requested));
  }

  @Test
  void queryLastLoginsReturnsAnEmptyMapForNullOrEmptyInputWithoutQuerying() {
    assertTrue(UserLoginRepository.queryLastLogins(null).isEmpty());
    assertTrue(UserLoginRepository.queryLastLogins(List.of()).isEmpty());
  }

  private static long insertUser(String uniqueId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password) VALUES (?, ?, ?) RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static void insertLogin(long userId, Timestamp created, String ipAddress, String sessionId)
      throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO user_logins (user_id, ip_address, user_agent, session_id, created) VALUES (?, ?, ?, ?, ?)")) {
      pst.setLong(1, userId);
      pst.setString(2, ipAddress);
      pst.setString(3, "test-agent");
      pst.setString(4, sessionId);
      pst.setTimestamp(5, created);
      pst.executeUpdate();
    }
  }

  private static void insertLogin(long userId, Timestamp created, String ipAddress) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO user_logins (user_id, ip_address, user_agent, created) VALUES (?, ?, ?, ?)")) {
      pst.setLong(1, userId);
      pst.setString(2, ipAddress);
      pst.setString(3, "test-agent");
      pst.setTimestamp(4, created);
      pst.executeUpdate();
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
    // Mirrors NEW_10000__new_database.sql's `users`/`user_logins` tables, trimmed to just the
    // columns queryLastLogins' query and buildRecord() touch.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS user_logins CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE user_logins ("
          + "login_id BIGSERIAL PRIMARY KEY, "
          + "user_id BIGINT REFERENCES users(user_id) NOT NULL, "
          + "ip_address VARCHAR(200) NOT NULL, "
          + "user_agent VARCHAR(255) NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "session_id VARCHAR(255), "
          + "source VARCHAR(50))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the users/user_logins schema", se);
    }
  }
}
