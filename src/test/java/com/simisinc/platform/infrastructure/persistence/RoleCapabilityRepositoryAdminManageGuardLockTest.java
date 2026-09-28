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
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * Proves the admin:manage self-lockout guard's check-then-revoke is atomic across two genuinely
 * concurrent transactions - the exact race a code-review pass found in the unsynchronized version of
 * this guard: two admins each revoking a different role's (or a role's and a direct grant's) last
 * contribution to admin:manage could both run their "would this leave zero holders" count before
 * either revoke committed, both see the other's holder as safety margin, and both commit, leaving
 * zero effective holders system-wide with the guard never having observed a 0 count.
 *
 * <p>This does not go through SaveRoleCapabilitiesCommand/SaveCapabilityGrantCommand (those are
 * covered by SaveRoleCapabilitiesCommandTest/SaveCapabilityGrantCommandTest with a mocked
 * connection) - it drives {@link RoleCapabilityRepository#acquireAdminManageGuardLock},
 * {@link RoleCapabilityRepository#countDistinctUsersHoldingCapability(Connection, long, long, long)}
 * and {@link RoleCapabilityRepository#revoke(Connection, long, long)} directly on two real JDBC
 * connections against a real PostgreSQL instance, because the property being verified - that
 * {@code pg_advisory_xact_lock} actually blocks a second transaction until the first commits - is a
 * database behavior no mock can stand in for.
 *
 * @author SimIS Inc.
 */
class RoleCapabilityRepositoryAdminManageGuardLockTest {

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
        "Docker is not available - skipping admin:manage guard lock integration test");

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
  void secondConcurrentRevokeBlocksUntilTheFirstCommitsAndThenCorrectlyRefuses() throws Exception {
    // Two roles, each with one real (enabled, validated) member - two effective holders total, and
    // neither role's contribution alone is redundant: revoking either one first is individually
    // "safe" only because the other one is still there.
    long roleA = insertRole("role-a");
    long roleB = insertRole("role-b");
    grantRoleCapability(roleA, capabilityId);
    grantRoleCapability(roleB, capabilityId);
    long userA = insertUser(true, true);
    long userB = insertUser(true, true);
    assignUserRole(userA, roleA);
    assignUserRole(userB, roleB);

    CountDownLatch firstHasRevokedButNotCommitted = new CountDownLatch(1);
    CountDownLatch releaseFirstToCommit = new CountDownLatch(1);
    CountDownLatch secondHasAcquiredTheLock = new CountDownLatch(1);
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    AtomicReference<Long> secondObservedCount = new AtomicReference<>();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = executor.submit(() -> {
        try (Connection connection = DB.getConnection()) {
          connection.setAutoCommit(false);
          RoleCapabilityRepository.acquireAdminManageGuardLock(connection);

          long remaining = RoleCapabilityRepository.countDistinctUsersHoldingCapability(
              connection, capabilityId, roleA, -1);
          assertEquals(1, remaining, "userB (via roleB) must still be counted as a safety margin");
          boolean revoked = RoleCapabilityRepository.revoke(connection, roleA, capabilityId);
          assertTrue(revoked, "roleA's admin:manage row must exist to revoke");

          // Hold the transaction open (and the advisory lock with it) so the second transaction's
          // acquireAdminManageGuardLock call has something to actually block on.
          firstHasRevokedButNotCommitted.countDown();
          if (!releaseFirstToCommit.await(10, TimeUnit.SECONDS)) {
            fail("test setup never released the first transaction");
          }
          connection.commit();
        } catch (Throwable t) {
          firstFailure.set(t);
        }
      });

      if (!firstHasRevokedButNotCommitted.await(10, TimeUnit.SECONDS)) {
        fail("the first transaction never reached its held-open point");
      }

      Future<?> second = executor.submit(() -> {
        try (Connection connection = DB.getConnection()) {
          connection.setAutoCommit(false);
          // Must block here until the first transaction commits (releasing the advisory lock) -
          // this is the exact call the old, unsynchronized guard never made.
          RoleCapabilityRepository.acquireAdminManageGuardLock(connection);
          secondHasAcquiredTheLock.countDown();

          long remaining = RoleCapabilityRepository.countDistinctUsersHoldingCapability(
              connection, capabilityId, roleB, -1);
          secondObservedCount.set(remaining);
          if (remaining > 0) {
            RoleCapabilityRepository.revoke(connection, roleB, capabilityId);
          }
          connection.commit();
        } catch (Throwable t) {
          secondFailure.set(t);
        }
      });

      // The second transaction must still be blocked on the lock a good while after the first
      // revoked but before it committed - proving acquireAdminManageGuardLock actually serializes
      // rather than letting both transactions run their counts concurrently.
      assertFalse(secondHasAcquiredTheLock.await(500, TimeUnit.MILLISECONDS),
          "the second transaction acquired the guard lock before the first committed");

      releaseFirstToCommit.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);

      if (firstFailure.get() != null) {
        throw new AssertionError("first transaction failed", firstFailure.get());
      }
      if (secondFailure.get() != null) {
        throw new AssertionError("second transaction failed", secondFailure.get());
      }

      // The second transaction only got to run its count after the first committed, so it must have
      // seen roleA's contribution already gone - 0 remaining once roleB is also excluded - and must
      // have refused to revoke roleB. Had the two transactions raced instead of serialized, the
      // second would have observed 1 (roleA's still-uncommitted holder) and revoked roleB too,
      // leaving zero effective holders.
      assertEquals(0L, secondObservedCount.get(),
          "the second transaction must observe roleA's revoke as already committed");
      assertEquals(1, RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId),
          "exactly one effective holder (userB, via roleB) must remain - not zero, not two");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void connectionScopedCountMatchesThePooledConnectionOverload() throws SQLException {
    long role = insertRole("role-a");
    grantRoleCapability(role, capabilityId);
    long user = insertUser(true, true);
    assignUserRole(user, role);

    long pooled = RoleCapabilityRepository.countDistinctUsersHoldingCapability(capabilityId, -1, -1);
    long connectionScoped;
    try (Connection connection = DB.getConnection()) {
      connectionScoped = RoleCapabilityRepository.countDistinctUsersHoldingCapability(
          connection, capabilityId, -1, -1);
    }

    assertEquals(1, pooled);
    assertEquals(pooled, connectionScoped);
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
    // Mirrors RoleCapabilityRepositoryTest's schema - see that class for the field-by-field
    // rationale (trimmed to just what the guard's queries touch, no geom/PostGIS).
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
      throw new IllegalStateException("Could not create the admin:manage guard lock test schema", se);
    }
  }
}
