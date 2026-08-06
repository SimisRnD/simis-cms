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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * Verifies {@link RoleCapabilityRepository#countDistinctUsersHoldingCapability} against a real
 * PostgreSQL instance -- this query is the entirety of what the {@code admin:manage} self-lockout
 * guard (SaveRoleCapabilitiesCommand, SaveCapabilityGrantCommand) relies on to decide whether a
 * revoke is safe, so a bug here (a swapped join column, an inverted revoked_at/expires_at
 * condition, a misbound "?" in the conditional exclude branches) would leave the guard either
 * falsely blocking legitimate revokes or, worse, silently failing to prevent a real lockout.
 *
 * @author SimIS Inc.
 */
class RoleCapabilityRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long capabilityId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping RoleCapabilityRepository integration test");

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
  void resetTables() throws SQLException {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE TABLE capability_grants, role_capabilities, user_roles, capabilities, lookup_role, users RESTART IDENTITY CASCADE");
    }
    capabilityId = insertCapability("admin:manage");
  }

  @Test
  void countsAUserWhoHoldsTheCapabilityThroughARole() throws SQLException {
    long role = insertRole("admin");
    grantRoleCapability(role, capabilityId);
    long user = insertUser(true, true);
    assignUserRole(user, role);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void aRoleWithZeroMembersDoesNotCountAsAHolder() throws SQLException {
    // The exact bug this method replaced: the old "how many roles list this capability" count
    // treated an empty role as a holder. Zero real users must mean zero here.
    long role = insertRole("admin");
    grantRoleCapability(role, capabilityId);

    assertEquals(0, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void countsAUserWhoHoldsTheCapabilityOnlyThroughADirectGrantWithNoRoleAtAll() throws SQLException {
    long user = insertUser(true, true);
    insertGrant(user, capabilityId, null, null);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void aUserHoldingViaBothARoleAndADirectGrantIsCountedOnce() throws SQLException {
    long role = insertRole("admin");
    grantRoleCapability(role, capabilityId);
    long user = insertUser(true, true);
    assignUserRole(user, role);
    insertGrant(user, capabilityId, null, null);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void aRevokedDirectGrantDoesNotCount() throws SQLException {
    long user = insertUser(true, true);
    insertGrant(user, capabilityId, null, Timestamp.from(java.time.Instant.now()));

    assertEquals(0, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void anExpiredDirectGrantDoesNotCount() throws SQLException {
    long user = insertUser(true, true);
    insertGrant(user, capabilityId, Timestamp.from(java.time.Instant.now().minusSeconds(60)), null);

    assertEquals(0, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void aNotYetExpiredDirectGrantCounts() throws SQLException {
    long user = insertUser(true, true);
    insertGrant(user, capabilityId, Timestamp.from(java.time.Instant.now().plusSeconds(3600)), null);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void aDisabledUserDoesNotCountEvenThoughTheirRoleGrantsTheCapability() throws SQLException {
    // The bug this fix closes: a disabled account can never log in (AuthenticateLoginCommand), so
    // counting it as a safety margin lets the guard approve a revoke that leaves nobody who can
    // actually reach the admin UI to fix it.
    long role = insertRole("admin");
    grantRoleCapability(role, capabilityId);
    long user = insertUser(false, true);
    assignUserRole(user, role);

    assertEquals(0, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void anUnvalidatedUserDoesNotCountEvenThoughTheyHoldADirectGrant() throws SQLException {
    long user = insertUser(true, false);
    insertGrant(user, capabilityId, null, null);

    assertEquals(0, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void excludeRoleIdOmitsOnlyThatRolesMembersNotOthers() throws SQLException {
    long roleA = insertRole("admin");
    long roleB = insertRole("data-manager");
    grantRoleCapability(roleA, capabilityId);
    grantRoleCapability(roleB, capabilityId);
    long userA = insertUser(true, true);
    long userB = insertUser(true, true);
    assignUserRole(userA, roleA);
    assignUserRole(userB, roleB);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId, roleA, -1));
    assertEquals(2, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void excludeGrantIdOmitsOnlyThatGrantNotOthers() throws SQLException {
    long userA = insertUser(true, true);
    long userB = insertUser(true, true);
    long grantA = insertGrant(userA, capabilityId, null, null);
    insertGrant(userB, capabilityId, null, null);

    assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId, -1, grantA));
    assertEquals(2, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  @Test
  void excludingBothARoleAndAGrantAtOnceLeavesOnlyTheRemainingCoverage() throws SQLException {
    long role = insertRole("admin");
    grantRoleCapability(role, capabilityId);
    long userViaRole = insertUser(true, true);
    assignUserRole(userViaRole, role);
    long userViaGrant = insertUser(true, true);
    long grant = insertGrant(userViaGrant, capabilityId, null, null);

    assertEquals(0,
        RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId, role, grant));
    assertEquals(2, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId));
  }

  private static long insertUser(boolean enabled, boolean validated) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password, enabled, validated) VALUES (?, ?, ?, ?, ?) RETURNING user_id")) {
      String uniqueId = "user-" + System.nanoTime();
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      pst.setBoolean(4, enabled);
      if (validated) {
        pst.setTimestamp(5, Timestamp.from(java.time.Instant.now()));
      } else {
        pst.setNull(5, java.sql.Types.TIMESTAMP);
      }
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static long insertRole(String code) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO lookup_role (level, code, title) VALUES (100, ?, ?) RETURNING role_id")) {
      pst.setString(1, code);
      pst.setString(2, code);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static long insertCapability(String code) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO capabilities (code) VALUES (?) RETURNING capability_id")) {
      pst.setString(1, code);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static void grantRoleCapability(long roleId, long capabilityId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO role_capabilities (role_id, capability_id) VALUES (?, ?)")) {
      pst.setLong(1, roleId);
      pst.setLong(2, capabilityId);
      pst.executeUpdate();
    }
  }

  private static void assignUserRole(long userId, long roleId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)")) {
      pst.setLong(1, userId);
      pst.setLong(2, roleId);
      pst.executeUpdate();
    }
  }

  private static long insertGrant(long userId, long capabilityId, Timestamp expiresAt, Timestamp revokedAt)
      throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO capability_grants (user_id, capability_id, expires_at, revoked_at) VALUES (?, ?, ?, ?) RETURNING capability_grant_id")) {
      pst.setLong(1, userId);
      pst.setLong(2, capabilityId);
      pst.setTimestamp(3, expiresAt);
      pst.setTimestamp(4, revokedAt);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
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

  private static void createSchema() {
    // Mirrors NEW_10000__new_database.sql's users/lookup_role/user_roles/capabilities/
    // role_capabilities/capability_grants tables, trimmed to just the columns
    // countDistinctUsersHoldingCapability's query touches (no geom -- not queried here, and it
    // would require PostGIS on the test image).
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS capability_grants CASCADE");
      statement.execute("DROP TABLE IF EXISTS role_capabilities CASCADE");
      statement.execute("DROP TABLE IF EXISTS user_roles CASCADE");
      statement.execute("DROP TABLE IF EXISTS capabilities CASCADE");
      statement.execute("DROP TABLE IF EXISTS lookup_role CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL, "
          + "enabled BOOLEAN DEFAULT true, "
          + "validated TIMESTAMP(3))");
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
      statement.execute("CREATE TABLE capabilities ("
          + "capability_id BIGSERIAL PRIMARY KEY, "
          + "code VARCHAR(100) UNIQUE NOT NULL, "
          + "category VARCHAR(50), "
          + "description VARCHAR(500), "
          + "created TIMESTAMP DEFAULT NOW())");
      statement.execute("CREATE TABLE role_capabilities ("
          + "role_id INTEGER NOT NULL REFERENCES lookup_role (role_id), "
          + "capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id), "
          + "PRIMARY KEY (role_id, capability_id))");
      statement.execute("CREATE TABLE capability_grants ("
          + "capability_grant_id BIGSERIAL PRIMARY KEY, "
          + "user_id BIGINT NOT NULL REFERENCES users (user_id), "
          + "capability_id BIGINT NOT NULL REFERENCES capabilities (capability_id), "
          + "granted_by BIGINT REFERENCES users (user_id), "
          + "granted TIMESTAMP DEFAULT NOW(), "
          + "reason VARCHAR(500), "
          + "expires_at TIMESTAMP, "
          + "revoked_at TIMESTAMP, "
          + "expiration_notified_at TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the RoleCapabilityRepository test schema", se);
    }
  }
}
