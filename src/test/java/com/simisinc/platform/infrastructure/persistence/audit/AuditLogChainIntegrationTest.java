/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.simisinc.platform.application.audit.AuditLogIntegrityCommand;
import com.simisinc.platform.application.audit.AuditLogIntegrityCommand.AuditIntegrityResult;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Integration test for the tamper-evidence hash chain against a real PostgreSQL (Testcontainers). It proves
 * the property the pure unit test cannot: that a record inserted through the serialized append and then read
 * back re-hashes to the same value (so the millisecond-precision timestamp and column-width normalization
 * keep the chain valid across the database round trip), and that verification catches an in-place edit while
 * tolerating a retention purge of the oldest records. Skipped when no Docker daemon is reachable.
 *
 * @author SimIS Inc.
 */
class AuditLogChainIntegrationTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping AuditLogChain integration test");

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
    execute("TRUNCATE TABLE audit_log RESTART IDENTITY");
  }

  @Test
  void appendsAHashChainThatVerifiesIntactAcrossTheRoundTrip() {
    // occurred is set with sub-millisecond precision on purpose: the append must round it to milliseconds
    // before hashing so the stored value re-hashes identically when read back.
    save(nanosecondEvent(1));
    save(nanosecondEvent(2));
    save(nanosecondEvent(3));

    List<AuditLog> rows = AuditLogRepository.findChainPage(0, 100);
    assertEquals(3, rows.size());
    assertEquals(AuditLogIntegrityCommand.GENESIS_HASH, rows.get(0).getPreviousHash(),
        "the first record anchors on the genesis hash");
    for (int i = 0; i < rows.size(); i++) {
      assertNotNull(rows.get(i).getRecordHash());
      assertEquals(64, rows.get(i).getRecordHash().length());
      if (i > 0) {
        assertEquals(rows.get(i - 1).getRecordHash(), rows.get(i).getPreviousHash(),
            "each record links to the prior record's hash");
      }
    }

    AuditIntegrityResult result = AuditLogIntegrityCommand.verify();
    assertTrue(result.isIntact(), "a freshly written chain must verify intact");
    assertEquals(3, result.getCheckedCount());
  }

  @Test
  void detectsAnInPlaceEdit() {
    save(nanosecondEvent(1));
    save(nanosecondEvent(2));
    save(nanosecondEvent(3));

    // Silently edit the middle row directly in the database, the way an attacker with table access would.
    execute("UPDATE audit_log SET outcome = 'failure' WHERE audit_id = 2");

    AuditIntegrityResult result = AuditLogIntegrityCommand.verify();
    assertFalse(result.isIntact());
    assertEquals(2L, result.getFirstInvalidAuditId());
  }

  @Test
  void toleratesARetentionPurgeOfTheOldestRecords() {
    save(nanosecondEvent(1));
    save(nanosecondEvent(2));
    save(nanosecondEvent(3));

    // Deleting the oldest record (as retention does) leaves the chain verifiable from the new oldest record.
    execute("DELETE FROM audit_log WHERE audit_id = 1");

    AuditIntegrityResult result = AuditLogIntegrityCommand.verify();
    assertTrue(result.isIntact(), "the surviving records must still verify after an oldest-first purge");
    assertEquals(2, result.getCheckedCount());
  }

  @Test
  void deleteOlderThanRemovesTheAgedPrefixAndLeavesTheChainIntact() {
    // Aged record first (lowest audit_id), then a recent one -- the normal, monotonic case.
    AuditLog aged = nanosecondEvent(1);
    aged.setOccurred(Timestamp.from(Instant.now().minus(200, ChronoUnit.DAYS)));
    save(aged);
    save(nanosecondEvent(2));

    int deleted = AuditLogRepository.deleteOlderThan(90);
    assertEquals(1, deleted, "the aged prefix record is purged");
    assertEquals(1, DB.selectCountFrom("audit_log"));
    assertTrue(AuditLogIntegrityCommand.verify().isIntact(), "the survivor must still verify after the purge");
  }

  @Test
  void deleteOlderThanNeverOpensAMidChainGap() {
    // Inversion: a recent record has the LOWER audit_id and an aged record sits behind it. Deleting the aged
    // record out of order would break the chain, so retention must leave it alone (over-retention is safe).
    save(nanosecondEvent(1));
    AuditLog agedBehind = nanosecondEvent(2);
    agedBehind.setOccurred(Timestamp.from(Instant.now().minus(200, ChronoUnit.DAYS)));
    save(agedBehind);

    int deleted = AuditLogRepository.deleteOlderThan(90);
    assertEquals(0, deleted, "an aged record behind a younger one is retained, not deleted mid-chain");
    assertEquals(2, DB.selectCountFrom("audit_log"));
    assertTrue(AuditLogIntegrityCommand.verify().isIntact(), "no false tamper alarm from retention");
  }

  private static AuditLog nanosecondEvent(long seed) {
    AuditLog event = new AuditLog();
    event.setOccurred(Timestamp.from(Instant.now().plusNanos(123456 + seed)));
    event.setEventCategory("authentication");
    event.setEventType("login.success");
    event.setOutcome("success");
    event.setActorUserId(seed);
    event.setActorUsername("user" + seed);
    event.setSourceIp("10.0.0." + seed);
    event.setSessionId("session-" + seed);
    return event;
  }

  private static void save(AuditLog event) {
    assertNotNull(AuditLogRepository.save(event), "the append should persist the record");
  }

  private static void execute(String sql) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException se) {
      throw new IllegalStateException("SQL failed: " + sql, se);
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
    // The audit_log table as defined by the install migration, including the Phase 4 hash columns.
    execute("DROP TABLE IF EXISTS audit_log CASCADE");
    execute("CREATE TABLE audit_log ("
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
  }
}
