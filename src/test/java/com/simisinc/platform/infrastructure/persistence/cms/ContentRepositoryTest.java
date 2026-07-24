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

import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ContentRepository#remove(Content)} against a real PostgreSQL instance.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available, so it does not break the build on hosts without a Docker daemon.
 * </p>
 *
 * <p>
 * The test creates a focused subset of the real {@code content} schema (the columns the delete path
 * touches). No other table has a foreign key to {@code content}, so there are no cascades to model;
 * removing the row is the whole operation.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 7/19/26
 */
class ContentRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    // Only run when a Docker daemon is reachable; otherwise mark the whole class as skipped
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ContentRepository integration test");

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
      statement.execute("TRUNCATE TABLE content RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset content table", se);
    }
  }

  @Test
  void removeDeletesTheContentRow() {
    Content content = addContent("delete-me", "<p>Delete me</p>");
    assertNotNull(content);
    assertTrue(content.getId() > 0);
    assertNotNull(ContentRepository.findByUniqueId("delete-me"));

    boolean removed = ContentRepository.remove(content);

    assertTrue(removed, "remove() should report success");
    assertNull(ContentRepository.findByUniqueId("delete-me"), "the content should be gone");
    assertEquals(0, DB.selectCountFrom("content"), "no content rows should remain");
  }

  @Test
  void removeOnlyDeletesTheTargetedRow() {
    Content keep = addContent("keep-me", "<p>Keep me</p>");
    Content remove = addContent("remove-me", "<p>Remove me</p>");

    assertTrue(ContentRepository.remove(remove));

    assertNull(ContentRepository.findByUniqueId("remove-me"));
    assertNotNull(ContentRepository.findByUniqueId("keep-me"), "unrelated content must be untouched");
    assertEquals(1, DB.selectCountFrom("content"));
    // The surviving row is unchanged
    Content survivor = ContentRepository.findByUniqueId("keep-me");
    assertEquals(keep.getId(), survivor.getId());
  }

  @Test
  void removeIsSafeWhenTheRowIsAlreadyGone() {
    Content content = addContent("gone", "<p>Gone</p>");

    assertTrue(ContentRepository.remove(content));
    // A second removal of the same record commits cleanly and leaves the table empty
    assertTrue(ContentRepository.remove(content));
    assertEquals(0, DB.selectCountFrom("content"));
  }

  @Test
  void removeRejectsTransientOrNullRecords() {
    assertFalse(ContentRepository.remove(null), "null record should be rejected");
    assertFalse(ContentRepository.remove(new Content()), "unsaved record (id = -1) should be rejected");
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
    // A focused subset of the real content table - enough for the add/find/remove path.
    // The tsvector column/trigger and the created_by/modified_by foreign keys are intentionally
    // omitted; the delete path only needs the primary key, and nothing references content.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS content CASCADE");
      statement.execute("CREATE TABLE content ("
          + "content_id BIGSERIAL PRIMARY KEY, "
          + "content_unique_id VARCHAR(255) UNIQUE, "
          + "content TEXT, "
          + "content_text TEXT, "
          + "draft_content TEXT, "
          + "content_format INTEGER NOT NULL DEFAULT 0, "
          + "draft_content_format INTEGER NOT NULL DEFAULT 0, "
          + "created_by BIGINT, "
          + "modified_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the content schema", se);
    }
  }

  private static Content addContent(String uniqueId, String html) {
    Content content = new Content();
    content.setUniqueId(uniqueId);
    content.setContent(html);
    return ContentRepository.save(content);
  }
}
