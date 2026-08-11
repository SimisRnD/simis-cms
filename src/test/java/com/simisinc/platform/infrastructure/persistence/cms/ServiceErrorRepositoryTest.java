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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
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

import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies findRecent's ordering/limit and deleteOld's retention window against a real PostgreSQL
 * instance. Skipped automatically when Docker is not available, mirroring
 * SystemHealthCheckRepositoryTest's pattern.
 *
 * @author SimIS
 * @created 8/10/2026
 */
class ServiceErrorRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ServiceErrorRepository integration test");

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
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE service_errors RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset service_errors table", se);
    }
  }

  @Test
  void saveInsertsARetrievableRow() {
    ServiceError saved = addError("java.lang.NullPointerException", "boom");

    assertTrue(saved.getId() > 0);
  }

  @Test
  void findRecentReturnsNewestFirst() {
    addError("java.lang.RuntimeException", "first");
    ServiceError second = addError("java.lang.RuntimeException", "second");

    List<ServiceError> recent = ServiceErrorRepository.findRecent(10);

    assertEquals(2, recent.size());
    assertEquals(second.getId(), recent.get(0).getId());
  }

  @Test
  void findRecentRespectsTheLimit() {
    addError("java.lang.RuntimeException", "one");
    addError("java.lang.RuntimeException", "two");
    addError("java.lang.RuntimeException", "three");

    List<ServiceError> recent = ServiceErrorRepository.findRecent(2);

    assertEquals(2, recent.size());
  }

  @Test
  void deleteOldKeepsErrorsWithinThirtyDaysAndRemovesOlderOnes() {
    ServiceError justInsideRetention = addError("java.lang.RuntimeException", "recent");
    backdate(justInsideRetention.getId(), 29 * 24);
    ServiceError justOutsideRetention = addError("java.lang.RuntimeException", "stale");
    backdate(justOutsideRetention.getId(), 31 * 24);

    ServiceErrorRepository.deleteOld();

    List<ServiceError> remaining = ServiceErrorRepository.findRecent(10);
    assertEquals(1, remaining.size());
    assertEquals(justInsideRetention.getId(), remaining.get(0).getId());
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
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS service_errors CASCADE");
      statement.execute("CREATE TABLE service_errors ("
          + "service_error_id BIGSERIAL PRIMARY KEY, "
          + "request_uri VARCHAR(500), "
          + "exception_class VARCHAR(255) NOT NULL, "
          + "message VARCHAR(1000), "
          + "stack_trace TEXT, "
          + "occurred_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the service_errors schema", se);
    }
  }

  private static ServiceError addError(String exceptionClass, String message) {
    ServiceError record = new ServiceError();
    record.setRequestUri("/some/page");
    record.setExceptionClass(exceptionClass);
    record.setMessage(message);
    record.setStackTrace(exceptionClass + ": " + message + "\n\tat com.example.Foo.bar(Foo.java:1)");
    return ServiceErrorRepository.save(record);
  }

  private static void backdate(long id, int hoursAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE service_errors SET occurred_at = NOW() - INTERVAL '" + hoursAgo + " hours' "
                + "WHERE service_error_id = ?")) {
      pst.setLong(1, id);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate row " + id, se);
    }
  }
}
