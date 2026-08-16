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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link RoleRepository#findAllWithMfaEnrolledMember} against a real PostgreSQL instance
 * -- the role list shown on /admin/mfa-properties so an admin can see, before enabling enforcement
 * for a role, whether anyone in it already has MFA enrolled. Modeled on
 * {@code RoleRepositoryFindAllByUserIdsTest}'s Testcontainers convention.
 *
 * @author SimIS Inc.
 */
class RoleRepositoryFindAllWithMfaEnrolledMemberTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping RoleRepository integration test");

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
    java.util.Properties properties = new java.util.Properties();
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
      statement.execute("TRUNCATE TABLE user_roles, lookup_role, users RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset users/lookup_role/user_roles tables", se);
    }
  }

  @Test
  void aRoleWithAnMfaEnrolledMemberIsReturned() throws SQLException {
    long enrolledUser = insertUser("enrolled", true);
    long adminRole = insertRole(100, "admin", "System Administrator");
    assignRole(enrolledUser, adminRole);

    List<Role> roles = RoleRepository.findAllWithMfaEnrolledMember();

    assertEquals(1, roles.size());
    assertEquals("admin", roles.get(0).getCode());
  }

  @Test
  void aRoleWithOnlyNonEnrolledMembersIsExcluded() throws SQLException {
    long notEnrolledUser = insertUser("not-enrolled", false);
    long editorRole = insertRole(70, "content-editor", "Content Editor");
    assignRole(notEnrolledUser, editorRole);

    List<Role> roles = RoleRepository.findAllWithMfaEnrolledMember();

    assertNull(roles, "a role with no MFA-enrolled member must not appear");
  }

  @Test
  void aRoleWithSomeButNotAllMembersEnrolledIsStillReturned() throws SQLException {
    long enrolledUser = insertUser("enrolled", true);
    long notEnrolledUser = insertUser("not-enrolled", false);
    long managerRole = insertRole(80, "content-manager", "Content Manager");
    assignRole(enrolledUser, managerRole);
    assignRole(notEnrolledUser, managerRole);

    List<Role> roles = RoleRepository.findAllWithMfaEnrolledMember();

    assertEquals(1, roles.size(), "the page answers \"has anyone enrolled\", not \"has everyone enrolled\"");
    assertEquals("content-manager", roles.get(0).getCode());
  }

  @Test
  void aRoleIsNotDuplicatedWhenItHasMultipleEnrolledMembers() throws SQLException {
    long firstEnrolled = insertUser("first-enrolled", true);
    long secondEnrolled = insertUser("second-enrolled", true);
    long adminRole = insertRole(100, "admin", "System Administrator");
    assignRole(firstEnrolled, adminRole);
    assignRole(secondEnrolled, adminRole);

    List<Role> roles = RoleRepository.findAllWithMfaEnrolledMember();

    assertEquals(1, roles.size(), "the EXISTS subquery must not fan the role out once per matching member");
  }

  @Test
  void returnsNullWhenNoRoleHasAnyEnrolledMember() throws SQLException {
    long notEnrolledUser = insertUser("not-enrolled", false);
    long editorRole = insertRole(70, "content-editor", "Content Editor");
    assignRole(notEnrolledUser, editorRole);
    insertRole(80, "content-manager", "Content Manager"); // no members at all

    assertNull(RoleRepository.findAllWithMfaEnrolledMember());
  }

  private static long insertUser(String uniqueId, boolean mfaEnabled) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password, mfa_enabled) VALUES (?, ?, ?, ?) RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      pst.setBoolean(4, mfaEnabled);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static long insertRole(int level, String code, String title) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO lookup_role (level, code, title) VALUES (?, ?, ?) RETURNING role_id")) {
      pst.setInt(1, level);
      pst.setString(2, code);
      pst.setString(3, title);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static void assignRole(long userId, long roleId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)")) {
      pst.setLong(1, userId);
      pst.setLong(2, roleId);
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
    // Mirrors NEW_10000__new_database.sql's `users`/`lookup_role`/`user_roles` tables, trimmed to
    // just the columns findAllWithMfaEnrolledMember's join and buildRecord() touch.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS user_roles CASCADE");
      statement.execute("DROP TABLE IF EXISTS lookup_role CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL, "
          + "mfa_enabled BOOLEAN NOT NULL DEFAULT false)");
      statement.execute("CREATE TABLE lookup_role ("
          + "role_id SERIAL PRIMARY KEY, "
          + "level INTEGER NOT NULL, "
          + "code VARCHAR(20), "
          + "title VARCHAR(100), "
          + "oauth_path VARCHAR(255))");
      statement.execute("CREATE TABLE user_roles ("
          + "user_role_id BIGSERIAL PRIMARY KEY, "
          + "user_id BIGINT REFERENCES users(user_id) NOT NULL, "
          + "role_id BIGINT REFERENCES lookup_role(role_id) NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the users/lookup_role/user_roles schema", se);
    }
  }
}
