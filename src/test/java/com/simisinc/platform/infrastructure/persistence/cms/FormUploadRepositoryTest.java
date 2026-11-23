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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link FormUploadRepository} against a real PostgreSQL instance (issue #2130), including
 * the constraints the form_uploads table itself enforces.
 */
class FormUploadRepositoryTest {

  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping FormUploadRepository integration test");
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    postgres = new GenericContainer<>(DockerImageName.parse(image != null && !image.isBlank() ? image : "postgres:15-alpine"))
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
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/" + DB_NAME);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);
    execute("CREATE TABLE form_data (form_data_id BIGSERIAL PRIMARY KEY, form_unique_id VARCHAR(255))");
    // Mirrors NEW_10010__new_cms.sql's form_uploads
    execute("CREATE TABLE form_uploads ("
        + "form_upload_id BIGSERIAL PRIMARY KEY, "
        + "form_unique_id VARCHAR(255) NOT NULL, "
        + "form_data_id BIGINT REFERENCES form_data(form_data_id) ON DELETE SET NULL, "
        + "field_name VARCHAR(255) NOT NULL, "
        + "original_filename VARCHAR(255), "
        + "file_path VARCHAR(255) NOT NULL, "
        + "content_type VARCHAR(50) NOT NULL, "
        + "file_length BIGINT NOT NULL, "
        + "width INTEGER, "
        + "height INTEGER, "
        + "thumbnail_path VARCHAR(255), "
        + "thumbnail_content_type VARCHAR(50), "
        + "scan_status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (scan_status IN ('pending', 'clean', 'rejected')), "
        + "scan_checked TIMESTAMP(3), "
        + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL)");
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
  void resetTables() {
    if (postgres != null && postgres.isRunning()) {
      execute("TRUNCATE TABLE form_uploads, form_data RESTART IDENTITY CASCADE");
    }
  }

  private static void execute(String sql) {
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException se) {
      throw new IllegalStateException(sql, se);
    }
  }

  private static long insertFormData() throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("INSERT INTO form_data (form_unique_id) VALUES ('contact-us') RETURNING form_data_id")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static FormUpload upload(String scanStatus) {
    FormUpload upload = new FormUpload();
    upload.setFormUniqueId("contact-us");
    upload.setFieldName("screenshot");
    upload.setOriginalFilename("shot.png");
    upload.setFilePath("forms/contact-us/abc.png");
    upload.setContentType("image/png");
    upload.setFileLength(1234);
    upload.setWidth(640);
    upload.setHeight(320);
    upload.setThumbnailPath("forms/contact-us/abc-thumbnail.jpg");
    upload.setThumbnailContentType("image/jpeg");
    upload.setScanStatus(scanStatus);
    upload.setScanChecked(new Timestamp(System.currentTimeMillis()));
    return upload;
  }

  @Test
  void anUploadIsStoredAndReadBack() {
    FormUpload saved = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    assertNotNull(saved);

    FormUpload found = FormUploadRepository.findById(saved.getId());

    assertEquals("contact-us", found.getFormUniqueId());
    assertEquals("screenshot", found.getFieldName());
    assertEquals("shot.png", found.getOriginalFilename());
    assertEquals("forms/contact-us/abc.png", found.getFilePath());
    assertEquals("image/png", found.getContentType());
    assertEquals(1234, found.getFileLength());
    assertEquals(640, found.getWidth());
    assertEquals(320, found.getHeight());
    assertEquals("forms/contact-us/abc-thumbnail.jpg", found.getThumbnailPath());
    assertEquals("image/jpeg", found.getThumbnailContentType());
    assertTrue(found.isClean());
    assertNotNull(found.getScanChecked());
    assertNotNull(found.getCreated());
    assertEquals(-1, found.getFormDataId(), "not linked to a submission yet");
  }

  @Test
  void missingDimensionsAndLinksReadBackAsMinusOne() {
    FormUpload bare = upload(FormUpload.SCAN_CLEAN);
    bare.setWidth(-1);
    bare.setHeight(-1);

    FormUpload found = FormUploadRepository.findById(FormUploadRepository.add(bare).getId());

    assertEquals(-1, found.getWidth());
    assertEquals(-1, found.getHeight());
    assertEquals(-1, found.getFormDataId());
  }

  @Test
  void uploadsAreLinkedToTheirSubmission() throws SQLException {
    long formDataId = insertFormData();
    FormUpload first = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUpload second = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));

    assertTrue(FormUploadRepository.linkToFormData(List.of(first, second), formDataId));

    assertEquals(formDataId, FormUploadRepository.findById(first.getId()).getFormDataId());
    assertEquals(formDataId, FormUploadRepository.findById(second.getId()).getFormDataId());
  }

  @Test
  void deletingTheSubmissionKeepsTheUploadRowSoItsFilesCanStillBeFound() throws SQLException {
    long formDataId = insertFormData();
    FormUpload upload = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUploadRepository.linkToFormData(List.of(upload), formDataId);

    execute("DELETE FROM form_data WHERE form_data_id = " + formDataId);

    FormUpload orphan = FormUploadRepository.findById(upload.getId());
    assertNotNull(orphan, "ON DELETE SET NULL, not CASCADE");
    assertEquals(-1, orphan.getFormDataId());
    assertEquals("forms/contact-us/abc.png", orphan.getFilePath());
  }

  @Test
  void theDatabaseRefusesAScanStatusOutsideTheList() {
    assertNull(FormUploadRepository.add(upload("probably-fine")));
  }

  @Test
  void anUploadCanBeRemoved() {
    FormUpload upload = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));

    assertTrue(FormUploadRepository.remove(upload));
    assertNull(FormUploadRepository.findById(upload.getId()));
  }

  @Test
  void anIdThatCannotExistFindsNothingWithoutAQuery() {
    assertNull(FormUploadRepository.findById(0));
    assertNull(FormUploadRepository.findById(-1));
  }

  private static List<Long> ids(List<FormUpload> uploads) {
    return uploads.stream().map(FormUpload::getId).toList();
  }

  @Test
  void uploadsAreFoundBySubmissionAndOnlyForThatSubmission() throws SQLException {
    long first = insertFormData();
    long second = insertFormData();
    FormUpload a = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUpload b = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUpload c = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUpload unlinked = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUploadRepository.linkToFormData(List.of(a, b), first);
    FormUploadRepository.linkToFormData(List.of(c), second);

    assertEquals(List.of(a.getId(), b.getId()), ids(FormUploadRepository.findAllByFormDataId(first)));
    assertEquals(List.of(a.getId(), b.getId(), c.getId()), ids(FormUploadRepository.findAllByFormDataIds(List.of(first, second))));
    assertEquals(List.of(c.getId()), ids(FormUploadRepository.findAllByFormDataIds(List.of(second, 999L))));
    assertFalse(ids(FormUploadRepository.findAllByFormDataIds(List.of(first, second))).contains(unlinked.getId()));
  }

  @Test
  void noSubmissionsMeansNoUploadsAndNoQuery() {
    FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));

    assertEquals(List.of(), FormUploadRepository.findAllByFormDataIds(List.of()));
    assertEquals(List.of(), FormUploadRepository.findAllByFormDataIds(null));
    assertEquals(List.of(), FormUploadRepository.findAllByFormDataId(-1));
  }

  @Test
  void unlinkedUploadsAreFoundOnlyOnceOlderThanTheCutoff() throws SQLException {
    long formDataId = insertFormData();
    FormUpload linkedOld = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUploadRepository.linkToFormData(List.of(linkedOld), formDataId);
    FormUpload unlinkedOld = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUpload unlinkedNew = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    execute("UPDATE form_uploads SET created = NOW() - INTERVAL '2 days' WHERE form_upload_id IN ("
        + linkedOld.getId() + ", " + unlinkedOld.getId() + ")");

    Timestamp dayAgo = new Timestamp(System.currentTimeMillis() - Duration.ofDays(1).toMillis());
    assertEquals(List.of(unlinkedOld.getId()), ids(FormUploadRepository.findUnlinkedCreatedBefore(dayAgo)),
        "not the linked one, and not the one a submission may still be about to claim");
  }

  @Test
  void aSubmissionDeletedLaterLeavesItsUploadsToBeFoundAsUnlinked() throws SQLException {
    long formDataId = insertFormData();
    FormUpload upload = FormUploadRepository.add(upload(FormUpload.SCAN_CLEAN));
    FormUploadRepository.linkToFormData(List.of(upload), formDataId);
    execute("UPDATE form_uploads SET created = NOW() - INTERVAL '100 days'");

    execute("DELETE FROM form_data WHERE form_data_id = " + formDataId);

    Timestamp dayAgo = new Timestamp(System.currentTimeMillis() - Duration.ofDays(1).toMillis());
    assertEquals(List.of(upload.getId()), ids(FormUploadRepository.findUnlinkedCreatedBefore(dayAgo)));
  }

  @Test
  void theStoredPathsAreEveryFileAndThumbnailPath() {
    FormUpload withThumbnail = upload(FormUpload.SCAN_CLEAN);
    withThumbnail.setFilePath("forms/a/1.png");
    withThumbnail.setThumbnailPath("forms/a/1-thumbnail.png");
    FormUploadRepository.add(withThumbnail);
    FormUpload withoutThumbnail = upload(FormUpload.SCAN_CLEAN);
    withoutThumbnail.setFilePath("forms/b/2.webp");
    withoutThumbnail.setThumbnailPath(null);
    FormUploadRepository.add(withoutThumbnail);

    assertEquals(Set.of("forms/a/1.png", "forms/a/1-thumbnail.png", "forms/b/2.webp"), FormUploadRepository.findAllStoredPaths());
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }
}
