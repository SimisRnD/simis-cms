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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies FunnelEventRepository (issue #565, phase 1) against a real PostgreSQL instance.
 *
 * @author SimIS Inc.
 * @created 8/2/26
 */
class FunnelEventRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping FunnelEventRepository integration test");

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
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE funnel_events RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset funnel_events table", se);
    }
  }

  @Test
  void recordSavesAFunnelEventRow() {
    FunnelEventRepository.record("contact-form", "view", "session-abc");

    assertEquals(1, DB.selectCountFrom("funnel_events"));
  }

  @Test
  void recordNeverThrowsEvenWithNullFields() {
    // record() is called from three separate call sites (a page render, a form submission, and an
    // admin action) -- a recording failure here must never become a second, unrelated failure there.
    assertDoesNotThrow(() -> FunnelEventRepository.record(null, null, null));
  }

  @Test
  void countStagesInRangeGroupsCountsByStage() {
    addEvent("contact-form", "view", 0);
    addEvent("contact-form", "view", 0);
    addEvent("contact-form", "submitted", 0);

    // The end bound is compared against occurred, which the DB sets from its own clock at insert
    // time -- daysAgo(-1) (a day into the future) rather than daysAgo(0) gives slack against any
    // drift between the host clock and the Testcontainers-Postgres container's clock, since this
    // test's purpose is the stage grouping, not exact-boundary behavior (see
    // countStagesInRangeIncludesTheStartInstantAndExcludesTheEndInstant for that).
    Map<String, Long> counts = FunnelEventRepository.countStagesInRange("contact-form", daysAgo(30), daysAgo(-1));

    assertEquals(2L, counts.get("view"));
    assertEquals(1L, counts.get("submitted"));
    assertNull(counts.get("processed"), "a stage with zero events in range should simply be absent");
  }

  @Test
  void countStagesInRangeScopesByFunnelKey() {
    // Two logical funnels sharing the same table (the reason funnel_key exists at all) must not mix
    addEvent("contact-form", "view", 0);
    addEvent("newsletter-signup", "view", 0);
    addEvent("newsletter-signup", "view", 0);

    // daysAgo(-1): see countStagesInRangeGroupsCountsByStage for why the end bound gets a day of
    // slack rather than sitting at daysAgo(0).
    Map<String, Long> contactFormCounts = FunnelEventRepository.countStagesInRange("contact-form", daysAgo(30), daysAgo(-1));
    Map<String, Long> newsletterCounts = FunnelEventRepository.countStagesInRange("newsletter-signup", daysAgo(30), daysAgo(-1));

    assertEquals(1L, contactFormCounts.get("view"));
    assertEquals(2L, newsletterCounts.get("view"));
  }

  @Test
  void countStagesInRangeExcludesEventsOutsideTheDateRange() {
    addEvent("contact-form", "view", 0);
    addEvent("contact-form", "view", 40);

    Map<String, Long> counts = FunnelEventRepository.countStagesInRange("contact-form", daysAgo(30), daysAgo(-1));

    assertEquals(1L, counts.get("view"));
  }

  @Test
  void countStagesInRangeReturnsAnEmptyMapWhenNoEventsExist() {
    Map<String, Long> counts = FunnelEventRepository.countStagesInRange("contact-form", daysAgo(30), daysAgo(0));

    assertTrue(counts.isEmpty());
  }

  @Test
  void countStagesInRangeIncludesTheStartInstantAndExcludesTheEndInstant() {
    // The query is occurred >= start AND occurred < end -- a half-open window. An event placed at
    // exactly `start` must count; an event placed at exactly `end` must not.
    java.sql.Timestamp start = daysAgo(30);
    java.sql.Timestamp end = daysAgo(0);
    addEventAt("contact-form", "view", start);
    addEventAt("contact-form", "view", end);

    Map<String, Long> counts = FunnelEventRepository.countStagesInRange("contact-form", start, end);

    assertEquals(1L, counts.get("view"));
  }

  @Test
  void deleteOlderThanRemovesOnlyRowsPastTheWindow() {
    addEvent("contact-form", "view", 0);
    addEvent("contact-form", "view", 100);

    int deleted = FunnelEventRepository.deleteOlderThan(90);

    assertEquals(1, deleted);
  }

  @Test
  void resolveRetentionDaysAppliesDefaultAndBounds() {
    assertEquals(90, FunnelEventRepository.resolveRetentionDays(null));
    assertEquals(90, FunnelEventRepository.resolveRetentionDays(""));
    assertEquals(45, FunnelEventRepository.resolveRetentionDays("45"));
    assertEquals(7, FunnelEventRepository.resolveRetentionDays("1"), "below the floor should clamp to 7");
    assertEquals(3650, FunnelEventRepository.resolveRetentionDays("999999"), "above the ceiling should clamp to 3650");
    assertEquals(90, FunnelEventRepository.resolveRetentionDays("not-a-number"));
  }

  private static void addEvent(String funnelKey, String stage, int daysAgo) {
    FunnelEventRepository.record(funnelKey, stage, "session-1");
    if (daysAgo > 0) {
      backdateMostRecent(daysAgo);
    }
  }

  private static void backdateMostRecent(int daysAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE funnel_events SET occurred = NOW() - (? || ' days')::interval "
                + "WHERE funnel_event_id = (SELECT MAX(funnel_event_id) FROM funnel_events)")) {
      pst.setInt(1, daysAgo);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate funnel_events row", se);
    }
  }

  private static void addEventAt(String funnelKey, String stage, java.sql.Timestamp occurred) {
    FunnelEventRepository.record(funnelKey, stage, "session-1");
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE funnel_events SET occurred = ? "
                + "WHERE funnel_event_id = (SELECT MAX(funnel_event_id) FROM funnel_events)")) {
      pst.setTimestamp(1, occurred);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not set funnel_events row's occurred timestamp", se);
    }
  }

  private static java.sql.Timestamp daysAgo(int days) {
    return new java.sql.Timestamp(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);
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
      statement.execute("DROP TABLE IF EXISTS funnel_events CASCADE");
      statement.execute("CREATE TABLE funnel_events ("
          + "funnel_event_id BIGSERIAL PRIMARY KEY, "
          + "funnel_key VARCHAR(50) NOT NULL, "
          + "stage VARCHAR(30) NOT NULL, "
          + "session_id VARCHAR(255), "
          + "occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the funnel_events schema", se);
    }
  }
}
