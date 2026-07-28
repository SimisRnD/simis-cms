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
 * Verifies the new user-account-analytics count methods (issue #560) against a real PostgreSQL
 * instance. Minimal schema replicated from {@code NEW_10000__new_database.sql} -- users,
 * lookup_role, user_roles only, not the full install script.
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
class UserAccountAnalyticsRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping user account analytics integration test");

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
  void clearTables() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping user account analytics integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE user_roles, users RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void countsOnlyEnabledAccounts() throws SQLException {
    seedUser("alice", true, null);
    seedUser("bob", true, null);
    seedUser("carol", false, null);

    assertEquals(2, UserRepository.countEnabledAccounts());
  }

  @Test
  void countsOnlyValidatedAccounts() throws SQLException {
    seedUser("alice", true, "2026-01-01");
    seedUser("bob", true, "2026-01-01");
    seedUser("carol", true, null);

    assertEquals(2, UserRepository.countValidatedAccounts());
  }

  @Test
  void countsOnlyRegistrationsFromTheCurrentMonth() throws SQLException {
    seedUserWithCreated("alice", "NOW()");
    seedUserWithCreated("bob", "NOW() - INTERVAL '3 months'");
    seedUserWithCreated("carol", "NOW() - INTERVAL '1 year'");

    assertEquals(1, UserRepository.countNewRegistrationsThisMonth());
  }

  @Test
  void countsAccountsWithAnyRoleWithoutDoubleCountingMultipleRoles() throws SQLException {
    long adminUserId = seedUser("alice", true, null);
    long contentManagerUserId = seedUser("bob", true, null);
    seedUser("carol", true, null); // no role -- public account

    long adminRoleId = lookupRoleId("admin");
    long contentManagerRoleId = lookupRoleId("content-manager");
    assignRole(adminUserId, adminRoleId);
    // alice also holds a second role -- must not be double-counted
    assignRole(adminUserId, contentManagerRoleId);
    assignRole(contentManagerUserId, contentManagerRoleId);

    assertEquals(2, UserRepository.countAccountsWithAnyRole());
  }

  @Test
  void publicAccountsIsTotalMinusAccountsWithARole() throws SQLException {
    long adminUserId = seedUser("alice", true, null);
    seedUser("bob", true, null);
    seedUser("carol", true, null);

    assignRole(adminUserId, lookupRoleId("admin"));

    assertEquals(3, UserRepository.countTotalUsers());
    assertEquals(1, UserRepository.countAccountsWithAnyRole());
    assertEquals(2, UserRepository.countPublicAccounts());
  }

  private long seedUser(String username, boolean enabled, String validatedDate) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String validatedSql = validatedDate == null ? "NULL" : "'" + validatedDate + "'";
      statement.execute("INSERT INTO users (unique_id, username, password, enabled, validated) VALUES ("
          + "'" + username + "', '" + username + "', 'hash', " + enabled + ", " + validatedSql + ")");
      try (var rs = statement.executeQuery("SELECT user_id FROM users WHERE username = '" + username + "'")) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private long seedUserWithCreated(String username, String createdExpression) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO users (unique_id, username, password, enabled, created) VALUES ("
          + "'" + username + "', '" + username + "', 'hash', true, " + createdExpression + ")");
      try (var rs = statement.executeQuery("SELECT user_id FROM users WHERE username = '" + username + "'")) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private long lookupRoleId(String code) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        var rs = statement.executeQuery("SELECT role_id FROM lookup_role WHERE code = '" + code + "'")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private void assignRole(long userId, long roleId) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO user_roles (user_id, role_id) VALUES (" + userId + ", " + roleId + ")");
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE lookup_role ("
          + "role_id SERIAL PRIMARY KEY, "
          + "level INTEGER NOT NULL, "
          + "code VARCHAR(20), "
          + "title VARCHAR(100))");
      statement.execute("INSERT INTO lookup_role (level, code, title) VALUES (80, 'content-manager', 'Content Manager')");
      statement.execute("INSERT INTO lookup_role (level, code, title) VALUES (100, 'admin', 'System Administrator')");

      // Full column set buildRecord() reads (see UserRepository.buildRecord), minus the PostGIS
      // geom column and unused content fields (description/image/video/field_values) -- a missing
      // column there would fail silently (buildRecord swallows SQLException), which would hide a
      // real problem rather than test against the real read path.
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "first_name VARCHAR(100), "
          + "last_name VARCHAR(100), "
          + "organization VARCHAR(100), "
          + "nickname VARCHAR(100), "
          + "email VARCHAR(255) UNIQUE, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL, "
          + "enabled BOOLEAN DEFAULT true, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT REFERENCES users(user_id), "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT REFERENCES users(user_id), "
          + "account_token VARCHAR(255), "
          + "account_token_expires TIMESTAMP(3), "
          + "validated TIMESTAMP(3), "
          + "title VARCHAR(100), "
          + "department VARCHAR(100), "
          + "timezone VARCHAR(100), "
          + "city VARCHAR(100), "
          + "state VARCHAR(100), "
          + "country VARCHAR(100), "
          + "postal_code VARCHAR(100), "
          + "latitude FLOAT DEFAULT 0, "
          + "longitude FLOAT DEFAULT 0, "
          + "mfa_secret VARCHAR(64), "
          + "mfa_enabled BOOLEAN DEFAULT false, "
          + "failed_attempt_count INTEGER DEFAULT 0, "
          + "locked_until TIMESTAMP(3))");

      statement.execute("CREATE TABLE user_roles ("
          + "user_role_id BIGSERIAL PRIMARY KEY, "
          + "user_id BIGINT REFERENCES users(user_id) NOT NULL, "
          + "role_id BIGINT REFERENCES lookup_role(role_id) NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the user account analytics test schema", se);
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
