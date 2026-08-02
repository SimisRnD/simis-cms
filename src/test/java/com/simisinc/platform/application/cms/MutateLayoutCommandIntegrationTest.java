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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Verifies {@link MutateLayoutCommand}'s structural mutations against a real PostgreSQL instance.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises {@code MutateLayoutCommand} through the real {@code WebPageRepository}/JDBC stack,
 * including the {@code web_pages_modified_by_fkey} foreign key that a mock-based test (see
 * {@link MutateLayoutCommandTest}) cannot exercise -- a mock never rejects an invalid id. It is
 * skipped automatically when Docker is not available, so it does not break the build on hosts
 * without a Docker daemon.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 7/30/26
 */
class MutateLayoutCommandIntegrationTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String PAGE_XML =
      "<page><section class=\"first\"><column class=\"small-12 cell\" /></section></page>";

  private static GenericContainer<?> postgres;
  private static long validUserId;

  @BeforeAll
  static void startDatabase() {
    // Only run when a Docker daemon is reachable; otherwise mark the whole class as skipped
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping MutateLayoutCommand integration test");

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

    // Point the application's shared DataSource at the container
    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    createSchema();
    validUserId = insertUser("editor1");
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
  void resetPages() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE web_pages RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_pages table", se);
    }
  }

  @Test
  void structuralMutationPersistsToTheDatabase() throws Exception {
    WebPage page = insertPage("/integration-test-page");

    MutateLayoutCommand.addSection(page, 0, "new-section", validUserId);

    WebPage reloaded = WebPageRepository.findById(page.getId());
    assertNotNull(reloaded.getDraftPageXml(),
        "draftPageXml should be persisted to the row, not just returned to the in-memory caller");
    assertTrue(reloaded.getDraftPageXml().contains("new-section"));
    assertTrue(reloaded.getDraft(), "draft flag should be persisted");
    assertEquals(validUserId, reloaded.getModifiedBy());
  }

  @Test
  void structuralMutationWithInvalidModifiedByThrowsAndDoesNotPersist() throws Exception {
    WebPage page = insertPage("/integration-test-page-2");
    long noSuchUserId = validUserId + 999;

    assertThrows(DataException.class,
        () -> MutateLayoutCommand.addSection(page, 0, "new-section", noSuchUserId),
        "modified_by referencing a nonexistent user must violate web_pages_modified_by_fkey and "
            + "surface as a real error, not a silent no-op");

    WebPage reloaded = WebPageRepository.findById(page.getId());
    assertNull(reloaded.getDraftPageXml(),
        "the mutation must not silently succeed: draftPageXml must remain unset when the save was rejected");
    assertFalse(reloaded.getDraft(), "draft flag must not be set either, since nothing was actually saved");
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
    // `users` is a focused subset -- just enough to satisfy web_pages' created_by/modified_by
    // foreign keys (the real table's `geom` column needs PostGIS, which this test has no need
    // for). `web_pages` is the full real column set: WebPageRepository.update() writes every one
    // of these columns in a single UPDATE, so a narrower subset would fail with an unrelated
    // "column does not exist" instead of exercising the modified_by fkey this test targets.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL, "
          + "redirect_url VARCHAR(255), "
          + "page_title VARCHAR(255), "
          + "page_keywords VARCHAR(255), "
          + "page_description VARCHAR(255), "
          + "draft BOOLEAN DEFAULT false, "
          + "enabled BOOLEAN DEFAULT true, "
          + "created_by BIGINT REFERENCES users(user_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT REFERENCES users(user_id), "
          + "role_id_list VARCHAR(100) DEFAULT NULL, "
          + "template VARCHAR(255), "
          + "page_xml TEXT, "
          + "comments TEXT, "
          + "draft_page_xml TEXT, "
          + "page_image_url VARCHAR(255), "
          + "searchable BOOLEAN DEFAULT true, "
          + "show_in_sitemap BOOLEAN DEFAULT true, "
          + "has_redirect BOOLEAN DEFAULT false, "
          + "sitemap_priority NUMERIC(2,1) DEFAULT 0.5, "
          + "sitemap_changefreq VARCHAR(20), "
          + "publish_at TIMESTAMP, "
          + "expires_at TIMESTAMP, "
          + "solution_type VARCHAR(255))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_pages/users schema", se);
    }
  }

  private static long insertUser(String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password) VALUES (?, ?, ?) RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test user", se);
    }
  }

  private static WebPage insertPage(String link) {
    WebPage page = new WebPage();
    page.setLink(link);
    page.setPageXml(PAGE_XML);
    page.setCreatedBy(validUserId);
    WebPage saved = WebPageRepository.save(page);
    if (saved == null) {
      throw new IllegalStateException("Test setup failed: could not insert page " + link);
    }
    return saved;
  }
}
