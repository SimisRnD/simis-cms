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

package com.simisinc.platform.infrastructure.distributedlock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Covers {@link LockManager} against a real PostgreSQL, in particular {@link
 * LockManager#lockTableExists()} -- the mechanism {@link
 * com.simisinc.platform.application.admin.DatabaseCommand} relies on to tell "another node holds
 * this lock" apart from "the distributed_lock table doesn't exist yet" (a never-installed
 * database), since {@link LockManager#lock} returns {@code null} for both.
 *
 * @author SimIS
 * @created 8/1/2026
 */
class LockManagerTest {

  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping the lock manager test");

    postgres = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    postgres.start();

    Properties dataSourceProperties = new Properties();
    dataSourceProperties.setProperty("jdbcUrl", jdbcUrl());
    dataSourceProperties.setProperty("username", DB_USER);
    dataSourceProperties.setProperty("password", DB_PASSWORD);
    DataSource.init(dataSourceProperties);
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

  @Test
  void lockTableExistsIsFalseOnASchemaWithNoTablesAtAll() {
    // This is the exact state of a database that has never completed a first install: nothing
    // has run any migrations yet, so distributed_lock does not exist.
    assertFalse(LockManager.lockTableExists());
  }

  @Test
  void lockFailsWithoutThrowingWhenItsOwnTableDoesNotExist() {
    // LockManager.lock() must degrade gracefully (return null, not throw) when its backing table
    // is missing -- DatabaseCommand's fresh-install path depends on this not raising.
    assertNull(LockManager.lock("some-lock", Duration.ofMinutes(1)));
  }

  @Test
  void lockTableExistsIsTrueOnceTheTableHasBeenCreated() throws Exception {
    createDistributedLockTable();
    try {
      assertTrue(LockManager.lockTableExists());
    } finally {
      dropDistributedLockTable();
    }
  }

  @Test
  void lockAndUnlockRoundTripOnceTheTableExists() throws Exception {
    createDistributedLockTable();
    try {
      String uuid = LockManager.lock("round-trip-lock", Duration.ofMinutes(1));
      assertNotNull(uuid, "lock() should succeed once the table exists and no one else holds it");

      // A second, distinct attempt while the first is still held should fail
      assertNull(LockManager.lock("round-trip-lock", Duration.ofMinutes(1)),
          "a second lock attempt should fail while the first is still held");

      assertTrue(LockManager.unlock("round-trip-lock", uuid));

      // Now that it's released, a new attempt should succeed again
      assertNotNull(LockManager.lock("round-trip-lock", Duration.ofMinutes(1)));
    } finally {
      dropDistributedLockTable();
    }
  }

  private static void createDistributedLockTable() throws Exception {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE distributed_lock (" +
              "name VARCHAR(64) PRIMARY KEY NOT NULL, " +
              "locked_at TIMESTAMP(3) NOT NULL, " +
              "lock_until TIMESTAMP(3) NOT NULL, " +
              "uuid VARCHAR(255) NOT NULL)");
    }
  }

  private static void dropDistributedLockTable() throws Exception {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("DROP TABLE IF EXISTS distributed_lock");
    }
  }

  private static String jdbcUrl() {
    return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/" + DB_NAME;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }
}
