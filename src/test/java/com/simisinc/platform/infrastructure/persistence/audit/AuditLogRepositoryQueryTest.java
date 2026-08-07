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

package com.simisinc.platform.infrastructure.persistence.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the source-IP/target-type filters (issue #478) and the CSV/JSON export methods against a real
 * PostgreSQL instance. Seeds rows through {@link AuditLogRepository#save} rather than hand-written INSERTs
 * so the real tamper-evidence hash-chain insert path runs exactly as it does in production.
 *
 * <p>
 * Schema replicated from {@code UPGRADE_20260719.1007__audit_log.sql} (base table),
 * {@code UPGRADE_20260720.1000__audit_tamper_evidence.sql} (hash columns), and
 * {@code UPGRADE_20260725.1002__audit_watermark.sql} (watermark table) -- not the full install script.
 * </p>
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
class AuditLogRepositoryQueryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping audit log query integration test");

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
  void clearTable() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping audit log query integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE audit_log, audit_log_watermark");
    }
  }

  @Test
  void filtersBySourceIp() {
    seed("authentication", "login.success", "203.0.113.4", "user", "42");
    seed("authentication", "login.success", "198.51.100.9", "user", "43");

    AuditLogSpecification spec = new AuditLogSpecification();
    spec.setSourceIp("203.0.113.4");

    List<AuditLog> results = AuditLogRepository.findAll(spec, null);

    assertEquals(1, results.size());
    assertEquals("203.0.113.4", results.get(0).getSourceIp());
  }

  @Test
  void filtersByTargetType() {
    seed("user_management", "user.disable", "203.0.113.4", "user", "42");
    seed("configuration", "site_property.update", "203.0.113.4", "site_property", "theme");

    AuditLogSpecification spec = new AuditLogSpecification();
    spec.setTargetType("site_property");

    List<AuditLog> results = AuditLogRepository.findAll(spec, null);

    assertEquals(1, results.size());
    assertEquals("site_property", results.get(0).getTargetType());
  }

  @Test
  void exportCsvWritesOnlyTheFilteredRows() throws Exception {
    seed("authentication", "login.success", "203.0.113.4", "user", "42");
    seed("authentication", "login.failure", "198.51.100.9", "user", "43");

    AuditLogSpecification spec = new AuditLogSpecification();
    spec.setOutcome("success");

    File file = File.createTempFile("audit-log-export-test", ".csv");
    try {
      AuditLogRepository.exportCsv(spec, file);
      String content = Files.readString(file.toPath());

      assertTrue(content.contains("Timestamp"), "expected a header row: " + content);
      assertTrue(content.contains("login.success"), "expected the matching row: " + content);
      assertFalse(content.contains("login.failure"), "the filtered-out row must not appear: " + content);
    } finally {
      file.delete();
    }
  }

  @Test
  void exportJsonWritesOnlyTheFilteredRowsAsJson() throws Exception {
    seed("authentication", "login.success", "203.0.113.4", "user", "42");
    seed("authentication", "login.failure", "198.51.100.9", "user", "43");

    AuditLogSpecification spec = new AuditLogSpecification();
    spec.setOutcome("success");

    File file = File.createTempFile("audit-log-export-test", ".json");
    try {
      AuditLogRepository.exportJson(spec, file);
      String content = Files.readString(file.toPath());

      assertTrue(content.startsWith("[") && content.endsWith("]"), "expected a JSON array: " + content);
      assertTrue(content.contains("\"eventType\":\"login.success\""), "expected the matching row: " + content);
      assertFalse(content.contains("login.failure"), "the filtered-out row must not appear: " + content);
    } finally {
      file.delete();
    }
  }

  @Test
  void findRecentActivityFiltersByMultipleCategoriesWithASingleInClauseQuery() {
    // Issue #1006: the activity feed's multi-category checkbox filter against a real IN (...) query,
    // not the per-category-query-and-merge pattern SiteStatsWidget.findRecentAdminActions used to use.
    seed("authentication", "authentication.login.success", "203.0.113.4", "user", "1");
    seed("content", "content.publish", "203.0.113.4", "web_page", "2");
    seed("data_access", "data.export", "203.0.113.4", "dataset", "3");

    List<AuditLog> results = AuditLogRepository.findRecentActivity(
        Set.of("authentication", "content"), null, null, null);

    assertEquals(2, results.size());
    List<String> categories = results.stream().map(AuditLog::getEventCategory).collect(Collectors.toList());
    assertTrue(categories.contains("authentication"));
    assertTrue(categories.contains("content"));
    assertFalse(categories.contains("data_access"));
  }

  @Test
  void findRecentActivityWithNoCategoriesReturnsEveryCategory() {
    seed("authentication", "authentication.login.success", "203.0.113.4", "user", "1");
    seed("content", "content.publish", "203.0.113.4", "web_page", "2");
    seed("data_access", "data.export", "203.0.113.4", "dataset", "3");

    List<AuditLog> resultsNullSet = AuditLogRepository.findRecentActivity(null, null, null, null);
    List<AuditLog> resultsEmptySet = AuditLogRepository.findRecentActivity(Set.of(), null, null, null);

    assertEquals(3, resultsNullSet.size());
    assertEquals(3, resultsEmptySet.size());
  }

  @Test
  void findRecentActivityHonorsTheTrailingTimeWindow() throws Exception {
    seed("content", "content.publish", "203.0.113.4", "web_page", "1");

    // A row that is deliberately backdated past the window by writing directly, since
    // AuditLogRepository.save() always stamps "occurred" close to now.
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO audit_log (occurred, event_category, event_type, outcome, actor_username) "
          + "VALUES (NOW() - INTERVAL '30 days', 'content', 'content.publish', 'success', 'old@example.com')");
    }

    List<AuditLog> withinLast7Days = AuditLogRepository.findRecentActivity(
        null, Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(7))), null, null);

    assertEquals(1, withinLast7Days.size());
    assertEquals("admin@example.com", withinLast7Days.get(0).getActorUsername());
  }

  @Test
  void findRecentActivityStillHonorsAnUpperBoundWhenOneIsGiven() {
    seed("content", "content.publish", "203.0.113.4", "web_page", "1");

    // "before" set to a moment before the seeded row's occurred timestamp excludes it; a future "before"
    // includes it -- proves the optional upper bound is wired through, not just the lower bound.
    Timestamp aSecondAgo = Timestamp.from(java.time.Instant.now().minus(Duration.ofSeconds(1)));
    Timestamp tomorrow = Timestamp.from(java.time.Instant.now().plus(Duration.ofDays(1)));

    List<AuditLog> excluded = AuditLogRepository.findRecentActivity(null, null, aSecondAgo, null);
    List<AuditLog> included = AuditLogRepository.findRecentActivity(null, null, tomorrow, null);

    assertTrue(excluded.isEmpty());
    assertEquals(1, included.size());
  }

  private void seed(String category, String eventType, String sourceIp, String targetType, String targetId) {
    AuditLog record = new AuditLog();
    record.setOccurred(new Timestamp(System.currentTimeMillis()));
    record.setEventCategory(category);
    record.setEventType(eventType);
    record.setOutcome(eventType.endsWith(".failure") ? "failure" : "success");
    record.setActorUsername("admin@example.com");
    record.setSourceIp(sourceIp);
    record.setTargetType(targetType);
    record.setTargetId(targetId);
    AuditLog saved = AuditLogRepository.save(record);
    assertNotNull(saved, "seed insert must succeed for the test to be meaningful");
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE audit_log ("
          + "audit_id BIGSERIAL PRIMARY KEY, "
          + "occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "event_category VARCHAR(50) NOT NULL, "
          + "event_type VARCHAR(100) NOT NULL, "
          + "outcome VARCHAR(20) NOT NULL, "
          + "actor_user_id BIGINT, "
          + "actor_username VARCHAR(255), "
          + "source_ip VARCHAR(200), "
          + "target_type VARCHAR(50), "
          + "target_id VARCHAR(255), "
          + "target_label VARCHAR(255), "
          + "details TEXT, "
          + "session_id VARCHAR(255), "
          + "schema_version INTEGER DEFAULT 1 NOT NULL, "
          + "previous_hash VARCHAR(64), "
          + "record_hash VARCHAR(64))");
      statement.execute("CREATE INDEX audit_log_occurred_idx ON audit_log(occurred)");
      statement.execute("CREATE INDEX audit_log_category_type_idx ON audit_log(event_category, event_type)");
      statement.execute("CREATE INDEX audit_log_actor_idx ON audit_log(actor_user_id)");
      statement.execute("CREATE TABLE audit_log_watermark ("
          + "id INTEGER PRIMARY KEY DEFAULT 1, "
          + "lowest_hashed_audit_id BIGINT NOT NULL DEFAULT 0)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the audit log test schema", se);
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
