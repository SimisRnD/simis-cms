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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies FormSubmissionFailureRepository (issue #563) against a real PostgreSQL instance.
 *
 * @author SimIS Inc.
 * @created 7/28/26
 */
class FormSubmissionFailureRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping FormSubmissionFailureRepository integration test");

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
      statement.execute("TRUNCATE TABLE form_submission_failures RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset form_submission_failures table", se);
    }
  }

  @Test
  void recordSavesAFailureRow() {
    FormSubmissionFailureRepository.record("contact-us", FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED,
        "203.0.113.5", "https://example.org/contact-us");

    assertEquals(1, DB.selectCountFrom("form_submission_failures"));
  }

  @Test
  void recordAcceptsTheFormUnavailableAndSystemErrorReasons() {
    // issue #563 follow-up -- new reasons for FormWidget.post()'s previously-silent early returns
    // (REASON_FORM_UNAVAILABLE) and a genuine FormDataRepository.save() failure (REASON_SYSTEM_ERROR);
    // also guards against either value exceeding the reason column's VARCHAR(30) constraint
    FormSubmissionFailureRepository.record("contact-us", FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE,
        "203.0.113.5", "https://example.org/contact-us");
    FormSubmissionFailureRepository.record("contact-us", FormSubmissionFailureRepository.REASON_SYSTEM_ERROR,
        "203.0.113.5", "https://example.org/contact-us");

    assertEquals(2, DB.selectCountFrom("form_submission_failures"));
  }

  @Test
  void recordNeverThrowsEvenWithNullFields() {
    // record() is called from a security-sensitive rejection path; a recording failure must never
    // become a second, unrelated failure for the person submitting the form.
    assertDoesNotThrow(() -> FormSubmissionFailureRepository.record(null, null, null, null));
  }

  @Test
  void countTotalFailuresScopesByDateRange() {
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_RATE_LIMITED, 0);
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_RATE_LIMITED, 40);

    // The end bound is daysAgo(-1) (this time tomorrow) rather than daysAgo(0) -- see the note on
    // daysAgo(). What this test is actually about is the lower bound: the 40-day-old row is still
    // 10 days outside it, so giving the upper bound a day of slack costs no coverage here. The
    // exact half-open boundary is pinned separately, and deterministically, by
    // countTotalFailuresIncludesTheStartInstantAndExcludesTheEndInstant.
    assertEquals(1, FormSubmissionFailureRepository.countTotalFailures(daysAgo(30), daysAgo(-1)));
  }

  @Test
  void countTotalFailuresIncludesTheStartInstantAndExcludesTheEndInstant() {
    // countTotalFailures filters occurred >= start AND occurred < end -- a half-open window. A row
    // sitting at exactly `start` must count; one at exactly `end` must not. Both rows get their
    // occurred value written explicitly from the same JVM clock the bounds come from, so this
    // assertion is about the query's boundary semantics only and cannot race the database clock.
    java.sql.Timestamp start = daysAgo(30);
    java.sql.Timestamp end = daysAgo(-1);
    addFailureAt("contact-us", FormSubmissionFailureRepository.REASON_RATE_LIMITED, start);
    addFailureAt("contact-us", FormSubmissionFailureRepository.REASON_RATE_LIMITED, end);

    assertEquals(1, FormSubmissionFailureRepository.countTotalFailures(start, end));
  }

  @Test
  void findFailureCountsByReasonRanksByVolumeDescending() {
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED, 0);
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED, 0);
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_RATE_LIMITED, 0);

    List<StatisticsData> breakdown = FormSubmissionFailureRepository.findFailureCountsByReason(30, 10);

    assertEquals(2, breakdown.size());
    assertEquals(FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED, breakdown.get(0).getLabel());
    assertEquals("2", breakdown.get(0).getValue());
  }

  @Test
  void deleteOlderThanRemovesOnlyRowsPastTheWindow() {
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_BLANK, 0);
    addFailure("contact-us", FormSubmissionFailureRepository.REASON_BLANK, 100);

    int deleted = FormSubmissionFailureRepository.deleteOlderThan(90);

    assertEquals(1, deleted);
    assertEquals(1, DB.selectCountFrom("form_submission_failures"));
  }

  @Test
  void resolveRetentionDaysAppliesDefaultAndBounds() {
    assertEquals(90, FormSubmissionFailureRepository.resolveRetentionDays(null));
    assertEquals(90, FormSubmissionFailureRepository.resolveRetentionDays(""));
    assertEquals(45, FormSubmissionFailureRepository.resolveRetentionDays("45"));
    assertEquals(7, FormSubmissionFailureRepository.resolveRetentionDays("1"), "below the floor should clamp to 7");
    assertEquals(3650, FormSubmissionFailureRepository.resolveRetentionDays("999999"), "above the ceiling should clamp to 3650");
    assertEquals(90, FormSubmissionFailureRepository.resolveRetentionDays("not-a-number"));
  }

  private static void addFailure(String formUniqueId, String reason, int daysAgo) {
    FormSubmissionFailureRepository.record(formUniqueId, reason, "203.0.113.5", "https://example.org/" + formUniqueId);
    if (daysAgo > 0) {
      backdateMostRecent(daysAgo);
    }
  }

  private static void backdateMostRecent(int daysAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE form_submission_failures SET occurred = NOW() - (? || ' days')::interval "
                + "WHERE failure_id = (SELECT MAX(failure_id) FROM form_submission_failures)")) {
      pst.setInt(1, daysAgo);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate form_submission_failures row", se);
    }
  }

  /** Records a failure and then pins its occurred value to a caller-supplied instant. */
  private static void addFailureAt(String formUniqueId, String reason, java.sql.Timestamp occurred) {
    FormSubmissionFailureRepository.record(formUniqueId, reason, "203.0.113.5", "https://example.org/" + formUniqueId);
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE form_submission_failures SET occurred = ? "
                + "WHERE failure_id = (SELECT MAX(failure_id) FROM form_submission_failures)")) {
      pst.setTimestamp(1, occurred);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not set the form_submission_failures row's occurred timestamp", se);
    }
  }

  /**
   * A bound measured from the JVM clock. Negative values are in the future -- {@code daysAgo(-1)}
   * is this time tomorrow.
   *
   * <p>
   * Never use {@code daysAgo(0)} as a bound against rows this test just inserted. Those rows take
   * their {@code occurred} value from the <em>database</em> clock, via the column's
   * {@code TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP}, and the two clocks disagree at the millisecond
   * the comparison happens on:
   * </p>
   * <ul>
   * <li>{@code TIMESTAMP(3)} <em>rounds</em> to the nearest millisecond, so a row inserted at
   * x.4996s is stored as x.500 -- half a millisecond in the future.</li>
   * <li>{@code System.currentTimeMillis()} truncates, so the JVM's "now" sits at or below real
   * time.</li>
   * <li>Any skew between the Testcontainers Postgres container's clock and the host's adds to
   * that, in either direction and without bound.</li>
   * </ul>
   * <p>
   * {@link FormSubmissionFailureRepository#countTotalFailures} filters with a strict
   * {@code occurred < ?} upper bound, so whenever the stored timestamp lands past the JVM's
   * reading, a row that really was inserted first is silently dropped from the count and the
   * assertion fails with "expected: &lt;1&gt; but was: &lt;0&gt;" -- then passes on an immediate
   * re-run of the same code. Bound with a value that is unambiguously on the correct side of both
   * clocks instead, and use {@link #addFailureAt} when the boundary itself is what is under test.
   * Same defect as the one fixed in {@code FormDataRepositoryTest}; see its {@code daysAgo()} note.
   * </p>
   */
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
      statement.execute("DROP TABLE IF EXISTS form_submission_failures CASCADE");
      statement.execute("CREATE TABLE form_submission_failures ("
          + "failure_id BIGSERIAL PRIMARY KEY, "
          + "form_unique_id VARCHAR(255), "
          + "occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "reason VARCHAR(30) NOT NULL, "
          + "ip_address VARCHAR(200), "
          + "url VARCHAR(512))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the form_submission_failures schema", se);
    }
  }
}
