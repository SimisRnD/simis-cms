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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.database.DB;

/**
 * Verifies the FormDataRepository analytics methods (issue #563) and the CSV
 * {@link FormDataRepository#export(DataConstraints, File)} method (issue #483) against a real
 * PostgreSQL instance -- the count/trend/breakdown queries and the exported CSV content are only
 * meaningful once proven against real rows, not just "the SQL doesn't throw."
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available, so it does not break the build on hosts without a Docker daemon.
 * </p>
 *
 * @author SimIS Inc.
 * @created 7/28/26
 */
class FormDataRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping FormDataRepository integration test");

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
      statement.execute("TRUNCATE TABLE form_data RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset form_data table", se);
    }
  }

  @Test
  void countTotalSubmissionsCountsEverySavedRow() {
    addSubmission("contact-us", false);
    addSubmission("newsletter-signup", false);
    addSubmission("contact-us", true);

    assertEquals(3, FormDataRepository.countTotalSubmissions());
  }

  @Test
  void countSpamFlaggedOnlyCountsFlaggedRowsWithinRange() {
    FormData recentFlagged = addSubmission("contact-us", true);
    addSubmission("contact-us", false);
    FormData oldFlagged = addSubmission("contact-us", true);
    backdate(oldFlagged.getId(), 40);

    Timestamp start = daysAgo(30);
    Timestamp end = daysAgo(0);

    // Only the recent flagged row is both spam-flagged and inside the 30-day window
    assertEquals(1, FormDataRepository.countSpamFlagged(start, end));
    assertNotNull(recentFlagged);
  }

  @Test
  void countSubmissionsScopesByFormAndDateRange() {
    addSubmission("contact-us", false);
    addSubmission("contact-us", false);
    FormData otherForm = addSubmission("newsletter-signup", false);
    FormData oldContactUs = addSubmission("contact-us", false);
    backdate(oldContactUs.getId(), 40);

    Timestamp start = daysAgo(30);
    Timestamp end = daysAgo(0);

    assertEquals(2, FormDataRepository.countSubmissions("contact-us", start, end));
    assertEquals(0, FormDataRepository.countSubmissions("newsletter-signup", daysAgo(0), daysAgo(-1)));
    assertNotNull(otherForm);
  }

  @Test
  void findDailySubmissionsZeroFillsAndIncludesTodaysSubmissions() {
    addSubmission("contact-us", false);
    addSubmission("contact-us", false);

    List<StatisticsData> daily = FormDataRepository.findDailySubmissions(7);

    // generate_series(NOW() - 7 days, NOW(), 1 day) is inclusive on both ends: 8 buckets
    assertEquals(8, daily.size());
    long total = daily.stream().mapToLong(d -> Long.parseLong(d.getValue())).sum();
    assertEquals(2, total, "both submissions should land in one of the zero-filled buckets");
  }

  @Test
  void findSubmissionCountsByFormRanksByVolumeDescending() {
    addSubmission("contact-us", false);
    addSubmission("contact-us", false);
    addSubmission("contact-us", false);
    addSubmission("newsletter-signup", false);

    List<StatisticsData> breakdown = FormDataRepository.findSubmissionCountsByForm(30, 10);

    assertEquals(2, breakdown.size());
    assertEquals("contact-us", breakdown.get(0).getLabel(), "the higher-volume form should rank first");
    assertEquals("3", breakdown.get(0).getValue());
    assertEquals("newsletter-signup", breakdown.get(1).getLabel());
    assertEquals("1", breakdown.get(1).getValue());
  }

  @Test
  void exportWritesTheHeaderAndFormDataRowsToCsv(@TempDir File tempDir) throws IOException {
    FormData clean = addFormData("contact-us", "203.0.113.5", "https://example.org/contact", false);
    FormData spam = addFormData("newsletter-signup", "198.51.100.9", "https://example.org/newsletter", true);

    File file = new File(tempDir, "form-data-export.csv");
    FormDataRepository.export(null, file);

    List<String> lines = Files.readAllLines(file.toPath());
    assertEquals(3, lines.size(), "a header row plus one row per seeded record");

    // Header names come from the SQL column aliases in FormDataRepository.export()
    String header = lines.get(0);
    assertTrue(header.contains("Form"), "header should contain the Form column: " + header);
    assertTrue(header.contains("IP Address"), "header should contain the IP Address column: " + header);
    assertTrue(header.contains("Submitted"), "header should contain the Submitted column: " + header);
    assertTrue(header.contains("URL"), "header should contain the URL column: " + header);
    assertTrue(header.contains("Spam Flagged"), "header should contain the Spam Flagged column: " + header);

    // Default sort is form_data_id desc, so the most recently added (spam) record comes first
    String firstDataRow = lines.get(1);
    assertTrue(firstDataRow.contains(spam.getFormUniqueId()), "row should contain the form id: " + firstDataRow);
    assertTrue(firstDataRow.contains(spam.getIpAddress()), "row should contain the IP address: " + firstDataRow);
    assertTrue(firstDataRow.contains("https://example.org/newsletter"), "row should contain the URL: " + firstDataRow);
    assertTrue(firstDataRow.contains("true"), "the spam-flagged row should report true: " + firstDataRow);

    String secondDataRow = lines.get(2);
    assertTrue(secondDataRow.contains(clean.getFormUniqueId()), "row should contain the form id: " + secondDataRow);
    assertTrue(secondDataRow.contains(clean.getIpAddress()), "row should contain the IP address: " + secondDataRow);
    assertTrue(secondDataRow.contains("https://example.org/contact"), "row should contain the URL: " + secondDataRow);
    assertTrue(secondDataRow.contains("false"), "the non-spam row should report false: " + secondDataRow);
  }

  @Test
  void markAsProcessedIsOneShotSoARepeatCallReturnsFalse() {
    FormData formData = addSubmission("contact-us", false);

    boolean firstCall = FormDataRepository.markAsProcessed(formData, 1L);
    boolean secondCall = FormDataRepository.markAsProcessed(formData, 1L);

    assertTrue(firstCall, "the first call transitions the row from unprocessed to processed");
    assertTrue(!secondCall, "a repeat call on an already-processed row must not report a fresh transition");
  }

  @Test
  void exportProducesOnlyAHeaderRowWhenThereAreNoRecords(@TempDir File tempDir) throws IOException {
    File file = new File(tempDir, "empty-export.csv");
    FormDataRepository.export(null, file);

    List<String> lines = Files.readAllLines(file.toPath());
    assertEquals(1, lines.size(), "only the header row should be written when form_data is empty");
    assertTrue(lines.get(0).contains("IP Address"));
  }

  @Test
  void exportHonorsAnExplicitPageSizeFromTheConstraints(@TempDir File tempDir) throws IOException {
    addFormData("form-a", "192.0.2.1", "https://example.org/a", false);
    addFormData("form-b", "192.0.2.2", "https://example.org/b", false);
    FormData mostRecent = addFormData("form-c", "192.0.2.3", "https://example.org/c", false);

    File file = new File(tempDir, "paged-export.csv");
    // Page 1 of size 1: only the most recently inserted row (form_data_id desc) should come back
    FormDataRepository.export(new DataConstraints(1, 1), file);

    List<String> lines = Files.readAllLines(file.toPath());
    assertEquals(2, lines.size(), "header plus exactly one row when the page size limits the export");
    assertTrue(lines.get(1).contains(mostRecent.getIpAddress()));
  }

  private static FormData addSubmission(String formUniqueId, boolean flaggedAsSpam) {
    FormData formData = new FormData();
    formData.setFormUniqueId(formUniqueId);
    formData.setIpAddress("203.0.113.5");
    formData.setSessionId("test-session");
    formData.setFlaggedAsSpam(flaggedAsSpam);
    FormData saved = FormDataRepository.add(formData);
    assertTrue(saved.getId() > 0);
    return saved;
  }

  private static FormData addFormData(String formUniqueId, String ipAddress, String url, boolean flaggedAsSpam) {
    FormData record = new FormData();
    record.setFormUniqueId(formUniqueId);
    record.setIpAddress(ipAddress);
    record.setUrl(url);
    record.setFlaggedAsSpam(flaggedAsSpam);
    return FormDataRepository.add(record);
  }

  /** Directly rewrites {@code created} -- add() always inserts with the DB default of NOW(). */
  private static void backdate(long formDataId, int daysAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE form_data SET created = NOW() - (? || ' days')::interval WHERE form_data_id = ?")) {
      pst.setInt(1, daysAgo);
      pst.setLong(2, formDataId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate form_data row", se);
    }
  }

  private static Timestamp daysAgo(int days) {
    return new Timestamp(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);
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
    // A focused subset of the real form_data table -- the users() foreign keys are intentionally
    // omitted since these tests don't exercise the created_by/claimed_by/etc relationships.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS form_data CASCADE");
      statement.execute("CREATE TABLE form_data ("
          + "form_data_id BIGSERIAL PRIMARY KEY, "
          + "form_unique_id VARCHAR(255), "
          + "field_values JSONB, "
          + "ip_address VARCHAR(200), "
          + "created_by BIGINT, "
          + "modified_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "claimed TIMESTAMP(3) DEFAULT NULL, "
          + "claimed_by BIGINT, "
          + "dismissed TIMESTAMP(3) DEFAULT NULL, "
          + "url VARCHAR(512), "
          + "query_params VARCHAR(512), "
          + "flagged_as_spam BOOLEAN DEFAULT FALSE, "
          + "session_id VARCHAR(255), "
          + "dismissed_by BIGINT, "
          + "processed TIMESTAMP(3) DEFAULT NULL, "
          + "processed_by BIGINT, "
          + "processed_system VARCHAR(255))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the form_data schema", se);
    }
  }
}
