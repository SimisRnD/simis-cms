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

import com.simisinc.platform.application.login.DeleteUserCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * The account that holds deleted accounts' content (DeleteUserCommand) can never be restored, given
 * a password, or sent a link to set one, whichever code path asks: UserRepository's restoreAccount,
 * createAccountToken and updatePassword leave it out of their UPDATE, so they return null, which
 * every caller treats as failure. Against a real PostgreSQL, since the guard is the SQL itself.
 *
 * @author SimIS Inc.
 */
class UserRepositoryHoldingAccountGuardTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String A_HASH = "$argon2id$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA";
  private static final String NEW_HASH = "$argon2id$v=19$m=65536,t=3,p=1$bmV3$bmV3aGFzaA";

  private static GenericContainer<?> postgres;
  private long holder;
  private long suspended;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping holding account guard test");
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
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);
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
  void createAccounts() throws SQLException {
    execute("DROP SCHEMA public CASCADE", "CREATE SCHEMA public",
        "CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY, unique_id VARCHAR(255) UNIQUE NOT NULL, "
            + "username VARCHAR(255) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, enabled BOOLEAN DEFAULT true, "
            + "suspension_reason TEXT, account_token VARCHAR(255), account_token_expires TIMESTAMP(3), "
            + "last_password_changed_at TIMESTAMP(3), modified TIMESTAMP(3))",
        "CREATE TABLE user_tokens (token_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL)");
    holder = insert(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID, DeleteUserCommand.NO_SIGN_IN_PASSWORD);
    suspended = insert("suspended-person", A_HASH);
  }

  @Test
  void theHoldingAccountIsNotRestored() {
    assertNull(UserRepository.restoreAccount(user(holder)));
    assertEquals("false", value(holder, "enabled"));
    assertNotNull(value(holder, "suspension_reason"));
  }

  @Test
  void theHoldingAccountIsNotSentALinkToSetAPassword() {
    assertNull(UserRepository.createAccountToken(user(holder)));
    assertNull(value(holder, "account_token"));
  }

  @Test
  void theHoldingAccountIsNotGivenAPassword() throws SQLException {
    execute("INSERT INTO user_tokens (user_id) VALUES (" + holder + ")");
    User withPassword = user(holder);
    withPassword.setPassword(NEW_HASH);

    assertNull(UserRepository.updatePassword(withPassword));
    assertEquals(DeleteUserCommand.NO_SIGN_IN_PASSWORD, value(holder, "password"));
    assertEquals("1", value("SELECT COUNT(*) FROM user_tokens WHERE user_id = " + holder));
  }

  @Test
  void anyOtherSuspendedAccountIsRestoredSentALinkAndGivenAPassword() {
    assertCanBeRestoredAndGivenAPassword(suspended);
  }

  /**
   * The guard follows the placeholder password, not the ID: a person given the reserved ID before it
   * was reserved (DeleteUserCommand.isHoldingAccount) is treated like anyone else.
   */
  @Test
  void aNamesakeWithARealPasswordIsTreatedLikeAnyoneElse() throws SQLException {
    execute("UPDATE users SET password = '" + A_HASH + "' WHERE user_id = " + holder);
    assertCanBeRestoredAndGivenAPassword(holder);
  }

  private static void assertCanBeRestoredAndGivenAPassword(long id) {
    assertNotNull(UserRepository.restoreAccount(user(id)));
    assertEquals("true", value(id, "enabled"));
    assertNotNull(UserRepository.createAccountToken(user(id)));
    assertNotNull(value(id, "account_token"));
    User withPassword = user(id);
    withPassword.setPassword(NEW_HASH);
    assertNotNull(UserRepository.updatePassword(withPassword));
    assertEquals(NEW_HASH, value(id, "password"));
  }

  private static long insert(String uniqueId, String password) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement("INSERT INTO users (unique_id, username, password, enabled, "
            + "suspension_reason) VALUES (?, ?, ?, false, 'suspended') RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, password);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private static String value(long userId, String column) {
    return value("SELECT " + column + " FROM users WHERE user_id = " + userId);
  }

  private static String value(String sql) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql);
        ResultSet rs = pst.executeQuery()) {
      rs.next();
      return rs.getString(1) == null ? null : String.valueOf(rs.getObject(1));
    } catch (SQLException se) {
      throw new IllegalStateException(sql, se);
    }
  }

  private static void execute(String... statements) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
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
}
