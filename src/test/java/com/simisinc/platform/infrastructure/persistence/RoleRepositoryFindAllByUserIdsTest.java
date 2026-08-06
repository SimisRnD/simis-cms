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

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link RoleRepository#findAllByUserIds} against a real PostgreSQL instance -- the batch
 * form added for /admin/users (UsersListWidget) so a page of users issues one role query instead of
 * one per row. Modeled on {@code ImageVariantRepositoryTest}'s Testcontainers convention.
 *
 * @author SimIS Inc.
 */
class RoleRepositoryFindAllByUserIdsTest {

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
      statement.execute("TRUNCATE TABLE user_roles, lookup_role, users RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset users/lookup_role/user_roles tables", se);
    }
  }

  @Test
  void findAllByUserIdsGroupsRolesByUserIdAcrossMultipleUsers() throws SQLException {
    long user1 = insertUser("user-one");
    long user2 = insertUser("user-two");
    long user3 = insertUser("user-three"); // no roles -- must not appear in the returned map at all
    long editorRole = insertRole(70, "content-editor", "Content Editor");
    long managerRole = insertRole(80, "content-manager", "Content Manager");
    assignRole(user1, editorRole);
    assignRole(user1, managerRole);
    assignRole(user2, editorRole);

    Map<Long, List<Role>> rolesByUserId = RoleRepository.findAllByUserIds(List.of(user1, user2, user3));

    assertEquals(2, rolesByUserId.get(user1).size());
    assertTrue(rolesByUserId.get(user1).stream().map(Role::getCode)
        .toList().containsAll(List.of("content-editor", "content-manager")));
    assertEquals(1, rolesByUserId.get(user2).size());
    assertEquals("content-editor", rolesByUserId.get(user2).get(0).getCode());
    assertNull(rolesByUserId.get(user3), "a user with zero roles must not get an empty-list entry");
  }

  @Test
  void findAllByUserIdsOnlyReturnsRolesForTheRequestedIds() throws SQLException {
    long requested = insertUser("requested");
    long notRequested = insertUser("not-requested");
    long adminRole = insertRole(100, "admin", "System Administrator");
    assignRole(requested, adminRole);
    assignRole(notRequested, adminRole);

    Map<Long, List<Role>> rolesByUserId = RoleRepository.findAllByUserIds(List.of(requested));

    assertEquals(1, rolesByUserId.size(), "a user id outside the requested set must not leak into the result");
    assertTrue(rolesByUserId.containsKey(requested));
  }

  @Test
  void findAllByUserIdsReturnsAnEmptyMapForNullOrEmptyInputWithoutQuerying() {
    assertTrue(RoleRepository.findAllByUserIds(null).isEmpty());
    assertTrue(RoleRepository.findAllByUserIds(List.of()).isEmpty());
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
    // just the columns findAllByUserIds' join and buildRecord() touch.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS user_roles CASCADE");
      statement.execute("DROP TABLE IF EXISTS lookup_role CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
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
