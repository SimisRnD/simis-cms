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
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the latest-per-service and uptime-percentage queries against a real PostgreSQL instance,
 * since DISTINCT ON and interval-window aggregation cannot be exercised meaningfully with a mock.
 * Skipped automatically when Docker is not available, matching SearchAnalyticsRepositoryTest's
 * pattern. deleteOld() is a single unconditional DB.deleteFrom call with a fixed interval (no site
 * property lookup, unlike SearchAnalyticsRepository.deleteOld()), so it is exercised directly here.
 *
 * @author SimIS
 * @created 7/30/2026
 */
class SystemHealthCheckRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SystemHealthCheckRepository integration test");

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
      statement.execute("TRUNCATE TABLE system_health_checks RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset system_health_checks table", se);
    }
  }

  @Test
  void saveInsertsARetrievableRow() {
    SystemHealthCheck saved = addCheck("database", true, 12, null);

    assertTrue(saved.getId() > 0);
  }

  @Test
  void findLatestPerServiceReturnsOnlyTheMostRecentRowForEachService() {
    addCheck("database", true, 10, null);
    SystemHealthCheck latestDatabase = addCheck("database", false, 999, "connection refused");
    addCheck("filesystem", true, 5, null);

    List<SystemHealthCheck> latest = SystemHealthCheckRepository.findLatestPerService();

    assertEquals(2, latest.size());
    SystemHealthCheck databaseResult = latest.stream()
        .filter(c -> "database".equals(c.getServiceName())).findFirst().orElseThrow();
    assertEquals(latestDatabase.getId(), databaseResult.getId());
    assertEquals("connection refused", databaseResult.getErrorMessage());
  }

  @Test
  void findUptimePercentComputesThePassRateWithinTheWindow() {
    addCheck("database", true, 10, null);
    addCheck("database", true, 10, null);
    addCheck("database", false, 10, "timeout");

    Double uptime = SystemHealthCheckRepository.findUptimePercent("database", 24);

    assertEquals(66.67, uptime, 0.01);
  }

  @Test
  void findUptimePercentIsNullWhenNoChecksExistInTheWindow() {
    Double uptime = SystemHealthCheckRepository.findUptimePercent("database", 24);

    assertNull(uptime);
  }

  @Test
  void findUptimePercentExcludesChecksOutsideTheWindow() {
    SystemHealthCheck old = addCheck("database", false, 10, "old failure");
    backdate(old.getId(), 48);
    addCheck("database", true, 10, null);

    Double uptime = SystemHealthCheckRepository.findUptimePercent("database", 24);

    assertEquals(100.0, uptime, 0.01);
  }

  @Test
  void deleteOldKeepsChecksWithinThirtyDaysAndRemovesOlderOnes() {
    // Two different service_names on purpose: findLatestPerService()'s DISTINCT ON collapses to
    // one row per service regardless of how many raw rows exist for it, so asserting against that
    // method cannot detect whether deleteOld() actually deleted anything. A direct row count can.
    SystemHealthCheck justInsideRetention = addCheck("database", true, 10, null);
    backdate(justInsideRetention.getId(), 29 * 24);
    SystemHealthCheck justOutsideRetention = addCheck("filesystem", true, 5, null);
    backdate(justOutsideRetention.getId(), 31 * 24);

    SystemHealthCheckRepository.deleteOld();

    assertEquals(1, countRows());
    List<SystemHealthCheck> remaining = SystemHealthCheckRepository.findLatestPerService();
    assertEquals(1, remaining.size());
    assertEquals("database", remaining.get(0).getServiceName());
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
      statement.execute("DROP TABLE IF EXISTS system_health_checks CASCADE");
      statement.execute("CREATE TABLE system_health_checks ("
          + "system_health_check_id BIGSERIAL PRIMARY KEY, "
          + "service_name VARCHAR(50) NOT NULL, "
          + "status VARCHAR(10) NOT NULL, "
          + "response_time_ms INTEGER, "
          + "error_message VARCHAR(500), "
          + "checked_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the system_health_checks schema", se);
    }
  }

  private static SystemHealthCheck addCheck(String serviceName, boolean up, int responseTimeMs, String errorMessage) {
    SystemHealthCheck record = new SystemHealthCheck();
    record.setServiceName(serviceName);
    record.setStatus(up ? SystemHealthCheck.STATUS_UP : SystemHealthCheck.STATUS_DOWN);
    record.setResponseTimeMs(responseTimeMs);
    record.setErrorMessage(errorMessage);
    return SystemHealthCheckRepository.save(record);
  }

  private static long countRows() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM system_health_checks")) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not count system_health_checks rows", se);
    }
  }

  private static void backdate(long id, int hoursAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE system_health_checks SET checked_at = NOW() - INTERVAL '" + hoursAgo + " hours' "
                + "WHERE system_health_check_id = ?")) {
      pst.setLong(1, id);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate row " + id, se);
    }
  }
}
