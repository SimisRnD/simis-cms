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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Runs {@link UserRepository#removeAndReassign} against a real PostgreSQL, because what it relies on
 * is the database's own behavior: the foreign keys it reads from the catalog, the ones that block a
 * plain DELETE, and the transaction that has to undo everything when one reassignment fails.
 *
 * <p>The schema is a cut-down copy of the real one: the account tables the delete clears, with the
 * same names and the same reference between oauth_tokens and user_tokens and between member_roles
 * and members, plus a content table whose NOT NULL created_by is what used to make a delete fail.
 *
 * @author SimIS Inc.
 */
class UserRepositoryRemoveAndReassignTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  private long target;
  private long holder;
  private long colleague;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping UserRepository delete integration test");

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
  void createSchemaAndPeople() throws SQLException {
    execute("DROP SCHEMA public CASCADE", "CREATE SCHEMA public",
        "CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY, unique_id VARCHAR(255) UNIQUE NOT NULL, "
            + "username VARCHAR(255) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, "
            + "created_by BIGINT REFERENCES users(user_id))",
        "CREATE TABLE groups (group_id BIGSERIAL PRIMARY KEY, user_count INTEGER DEFAULT 0)",
        "CREATE TABLE user_groups (user_group_id BIGSERIAL PRIMARY KEY, "
            + "user_id BIGINT REFERENCES users(user_id) NOT NULL, group_id BIGINT REFERENCES groups(group_id) NOT NULL)",
        "CREATE TABLE user_roles (user_role_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL, "
            + "role_id INTEGER NOT NULL)",
        "CREATE TABLE user_tokens (token_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL)",
        "CREATE TABLE oauth_tokens (token_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL, "
            + "user_token_id BIGINT REFERENCES user_tokens(token_id) NOT NULL)",
        "CREATE TABLE user_logins (login_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL)",
        "CREATE TABLE user_mfa_recovery_codes (recovery_code_id BIGSERIAL PRIMARY KEY, "
            + "user_id BIGINT REFERENCES users(user_id) NOT NULL)",
        "CREATE TABLE capability_grants (capability_grant_id BIGSERIAL PRIMARY KEY, "
            + "user_id BIGINT NOT NULL REFERENCES users (user_id), granted_by BIGINT REFERENCES users (user_id))",
        "CREATE TABLE members (member_id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(user_id) NOT NULL, "
            + "created_by BIGINT REFERENCES users(user_id) NOT NULL)",
        "CREATE TABLE member_roles (member_role_id BIGSERIAL PRIMARY KEY, member_id BIGINT REFERENCES members(member_id) NOT NULL, "
            + "user_id BIGINT REFERENCES users(user_id) NOT NULL, created_by BIGINT REFERENCES users(user_id) NOT NULL)",
        "CREATE TABLE content (content_id BIGSERIAL PRIMARY KEY, created_by BIGINT REFERENCES users(user_id) NOT NULL, "
            + "modified_by BIGINT REFERENCES users(user_id) NOT NULL, archived_by BIGINT REFERENCES users(user_id))");
    holder = insertReturningId("INSERT INTO users (unique_id, username, password) VALUES ('deleted-accounts', 'deleted-accounts', '!') RETURNING user_id");
    colleague = insertReturningId("INSERT INTO users (unique_id, username, password) VALUES ('colleague', 'colleague', 'x') RETURNING user_id");
    target = insertReturningId("INSERT INTO users (unique_id, username, password, created_by) VALUES ('leaver', 'leaver', 'x', "
        + colleague + ") RETURNING user_id");
  }

  @Test
  void deletesTheAccountAndReassignsEverythingItDid() throws SQLException {
    // What the account is: its own rows
    long group = insertReturningId("INSERT INTO groups (user_count) VALUES (1) RETURNING group_id");
    execute("INSERT INTO user_groups (user_id, group_id) VALUES (" + target + ", " + group + ")",
        "INSERT INTO user_roles (user_id, role_id) VALUES (" + target + ", 1)",
        "INSERT INTO user_logins (user_id) VALUES (" + target + ")",
        "INSERT INTO user_mfa_recovery_codes (user_id) VALUES (" + target + ")",
        "INSERT INTO capability_grants (user_id, granted_by) VALUES (" + target + ", " + colleague + ")");
    long token = insertReturningId("INSERT INTO user_tokens (user_id) VALUES (" + target + ") RETURNING token_id");
    execute("INSERT INTO oauth_tokens (user_id, user_token_id) VALUES (" + target + ", " + token + ")");
    long membership = insertReturningId("INSERT INTO members (user_id, created_by) VALUES (" + target + ", " + colleague + ") RETURNING member_id");
    execute("INSERT INTO member_roles (member_id, user_id, created_by) VALUES (" + membership + ", " + target + ", " + colleague + ")");

    // What the account did: 8 references in all
    execute("INSERT INTO content (created_by, modified_by, archived_by) VALUES (" + target + ", " + target + ", " + target + ")",
        "INSERT INTO content (created_by, modified_by) VALUES (" + colleague + ", " + target + ")",
        "INSERT INTO capability_grants (user_id, granted_by) VALUES (" + colleague + ", " + target + ")",
        "UPDATE users SET created_by = " + target + " WHERE user_id = " + colleague);
    long colleagueMembership = insertReturningId("INSERT INTO members (user_id, created_by) VALUES (" + colleague + ", " + target + ") RETURNING member_id");
    execute("INSERT INTO member_roles (member_id, user_id, created_by) VALUES (" + colleagueMembership + ", " + colleague + ", " + target + ")");

    int reassigned = UserRepository.removeAndReassign(user(target), holder);

    assertEquals(8, reassigned);
    assertEquals(0, count("SELECT COUNT(*) FROM users WHERE user_id = " + target));
    assertEquals(0, referencesTo(target), "nothing may still reference the deleted account");
    // Its own rows went with it, not to the holder
    assertEquals(0, count("SELECT COUNT(*) FROM capability_grants WHERE user_id = " + holder));
    assertEquals(0, count("SELECT COUNT(*) FROM members WHERE user_id = " + holder));
    assertEquals(0, count("SELECT user_count FROM groups WHERE group_id = " + group), "group member count kept right");
    // What it did is kept, attributed to the holder
    assertEquals(2, count("SELECT COUNT(*) FROM content"));
    assertEquals(1, count("SELECT COUNT(*) FROM content WHERE created_by = " + holder + " AND modified_by = " + holder
        + " AND archived_by = " + holder));
    assertEquals(1, count("SELECT COUNT(*) FROM content WHERE created_by = " + colleague + " AND modified_by = " + holder));
    assertEquals(1, count("SELECT COUNT(*) FROM capability_grants WHERE user_id = " + colleague + " AND granted_by = " + holder));
    assertEquals(1, count("SELECT COUNT(*) FROM users WHERE user_id = " + colleague + " AND created_by = " + holder));
    assertEquals(1, count("SELECT COUNT(*) FROM member_roles WHERE user_id = " + colleague + " AND created_by = " + holder));
  }

  @Test
  void anAccountThatDidNothingIsSimplyDeleted() {
    assertEquals(0, UserRepository.removeAndReassign(user(target), holder));
    assertEquals(0, count("SELECT COUNT(*) FROM users WHERE user_id = " + target));
    assertEquals(2, count("SELECT COUNT(*) FROM users"));
  }

  @Test
  void aReassignmentThatFailsLeavesEverythingAsItWas() throws SQLException {
    // One row per person: moving the target's row onto the holder, who already has one, is refused
    execute("CREATE TABLE one_per_person (owner BIGINT UNIQUE REFERENCES users(user_id))",
        "INSERT INTO one_per_person (owner) VALUES (" + holder + "), (" + target + ")",
        "INSERT INTO capability_grants (user_id) VALUES (" + target + ")",
        "INSERT INTO content (created_by, modified_by) VALUES (" + target + ", " + target + ")");

    assertEquals(-1, UserRepository.removeAndReassign(user(target), holder));

    assertEquals(1, count("SELECT COUNT(*) FROM users WHERE user_id = " + target));
    assertEquals(1, count("SELECT COUNT(*) FROM capability_grants WHERE user_id = " + target), "its own rows are back");
    assertEquals(1, count("SELECT COUNT(*) FROM content WHERE created_by = " + target), "nothing was reassigned");
  }

  @Test
  void refusesToReassignAnAccountToItself() {
    assertEquals(-1, UserRepository.removeAndReassign(user(target), target));
    assertEquals(-1, UserRepository.removeAndReassign(user(target), 0));
    assertEquals(1, count("SELECT COUNT(*) FROM users WHERE user_id = " + target));
  }

  @Test
  void readsEveryReferenceFromTheDatabase() throws SQLException {
    try (Connection connection = DB.getConnection()) {
      Set<String> references = UserRepository.findUserReferences(connection);
      assertTrue(references.contains("content.created_by"));
      assertTrue(references.contains("content.archived_by"));
      assertTrue(references.contains("users.created_by"));
      assertTrue(references.contains("oauth_tokens.user_id"));
      assertFalse(references.contains("oauth_tokens.user_token_id"), "references to other tables are not users");
      assertEquals(16, references.size());
    }
  }

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  /** Rows in every table that still point at {@code userId}, through any foreign key to users. */
  private static long referencesTo(long userId) throws SQLException {
    long total = 0;
    try (Connection connection = DB.getConnection()) {
      for (String reference : UserRepository.findUserReferences(connection)) {
        String[] tableAndColumn = reference.split("\\.");
        total += count("SELECT COUNT(*) FROM " + tableAndColumn[0] + " WHERE " + tableAndColumn[1] + " = " + userId);
      }
    }
    return total;
  }

  private static void execute(String... statements) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    }
  }

  private static long insertReturningId(String sql) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql);
        ResultSet rs = pst.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static long count(String sql) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql);
        ResultSet rs = pst.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException se) {
      throw new IllegalStateException(sql, se);
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
