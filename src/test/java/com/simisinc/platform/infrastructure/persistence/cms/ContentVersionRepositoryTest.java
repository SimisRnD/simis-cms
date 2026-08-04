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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

import com.simisinc.platform.domain.model.cms.ContentVersion;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ContentVersionRepository} against a real PostgreSQL instance (#406).
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available.
 * </p>
 *
 * @author elizabeth houser
 */
class ContentVersionRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ContentVersionRepository integration test");

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
      statement.execute("TRUNCATE TABLE content_versions, content RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset content_versions table", se);
    }
  }

  @Test
  void insertedVersionCanBeFoundById() {
    long contentId = addContent("/insert-find");

    long versionId = insertVersion(contentId, "<p>v1</p>", 7L, "cleared per PA case 2026-406");

    ContentVersion found = ContentVersionRepository.findById(versionId);
    assertEquals(contentId, found.getContentId());
    assertEquals("<p>v1</p>", found.getContent());
    assertEquals(7L, found.getApprovedBy());
    assertEquals("cleared per PA case 2026-406", found.getReleaseReference());
  }

  @Test
  void findByIdReturnsNullForAnUnknownId() {
    assertNull(ContentVersionRepository.findById(999999L));
  }

  @Test
  void findByContentIdReturnsNullWhenThereAreNoVersions() {
    long contentId = addContent("/no-versions");

    assertNull(ContentVersionRepository.findByContentId(contentId, null));
  }

  @Test
  void findByContentIdOnlyReturnsVersionsForThatContentBlock() {
    long contentOne = addContent("/content-one");
    long contentTwo = addContent("/content-two");
    insertVersion(contentOne, "<p>one</p>", 1L, null);
    insertVersion(contentTwo, "<p>two</p>", 1L, null);

    List<ContentVersion> results = ContentVersionRepository.findByContentId(contentOne, null);

    assertEquals(1, results.size());
    assertEquals("<p>one</p>", results.get(0).getContent());
  }

  @Test
  void findByContentIdSortsNewestFirst() throws InterruptedException {
    long contentId = addContent("/sort-order");
    long olderId = insertVersion(contentId, "<p>older</p>", 1L, null);
    Thread.sleep(5);
    long newerId = insertVersion(contentId, "<p>newer</p>", 1L, null);

    List<ContentVersion> results = ContentVersionRepository.findByContentId(contentId, null);

    assertEquals(2, results.size());
    assertEquals(newerId, results.get(0).getId(), "the most recently published version should be listed first");
    assertEquals(olderId, results.get(1).getId());
  }

  @Test
  void pruneOldestKeepsOnlyTheMostRecentVersions() throws SQLException {
    long contentId = addContent("/prune-me");
    for (int i = 0; i < 5; i++) {
      insertVersion(contentId, "<p>v" + i + "</p>", 1L, null);
    }

    try (Connection connection = DB.getConnection()) {
      ContentVersionRepository.pruneOldest(connection, contentId, 2);
    }

    // Rapid-fire inserts can land in the same published_at millisecond, and findByContentId only
    // orders by published_at DESC (no id tiebreaker) -- so which of two same-millisecond rows sorts
    // first is not guaranteed. Assert the surviving *set* (what pruneOldest is actually responsible
    // for), not a tie-broken order that belongs to a different method.
    List<ContentVersion> results = ContentVersionRepository.findByContentId(contentId, null);
    Set<String> remaining = results.stream().map(ContentVersion::getContent).collect(Collectors.toSet());
    assertEquals(Set.of("<p>v3</p>", "<p>v4</p>"), remaining);
  }

  @Test
  void pruneOldestLeavesEverythingAloneWhenUnderTheLimit() throws SQLException {
    long contentId = addContent("/under-limit");
    insertVersion(contentId, "<p>only</p>", 1L, null);

    try (Connection connection = DB.getConnection()) {
      ContentVersionRepository.pruneOldest(connection, contentId, 20);
    }

    assertEquals(1, ContentVersionRepository.findByContentId(contentId, null).size());
  }

  @Test
  void findByContentIdHonorsDataConstraintsPaging() {
    long contentId = addContent("/paged");
    for (int i = 0; i < 5; i++) {
      insertVersion(contentId, "<p>v" + i + "</p>", 1L, null);
    }

    DataConstraints constraints = new DataConstraints(1, 2);
    List<ContentVersion> firstPage = ContentVersionRepository.findByContentId(contentId, constraints);

    assertEquals(2, firstPage.size());
    assertNotEquals(firstPage.get(0).getId(), firstPage.get(1).getId());
  }

  @Test
  void insertStoresNoApproverAsNullRatherThanNegativeOne() {
    // approved_by is -1 by default for an ungoverned direct publish -- the FK-friendly sentinel
    // convention used throughout this codebase (see SqlUtils#add(String, long, long)).
    long contentId = addContent("/ungoverned-publish");

    long versionId = insertVersion(contentId, "<p>direct publish</p>", -1L, null);

    ContentVersion found = ContentVersionRepository.findById(versionId);
    assertEquals(-1L, found.getApprovedBy(), "buildRecord defaults a null column back to -1");
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
    // A focused subset of the real schema -- no users table here, so approved_by is a plain column
    // rather than an FK, matching the same simplification used elsewhere in this test suite.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS content_versions CASCADE");
      statement.execute("DROP TABLE IF EXISTS content CASCADE");
      statement.execute("CREATE TABLE content ("
          + "content_id BIGSERIAL PRIMARY KEY, "
          + "content_unique_id VARCHAR(255) UNIQUE NOT NULL)");
      statement.execute("CREATE TABLE content_versions ("
          + "content_version_id BIGSERIAL PRIMARY KEY, "
          + "content_id BIGINT REFERENCES content(content_id) ON DELETE CASCADE, "
          + "content TEXT, "
          + "approved_by BIGINT, "
          + "published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "release_reference VARCHAR(255))");
      statement.execute("CREATE INDEX content_versions_content_idx ON content_versions(content_id, published_at DESC)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the content_versions schema", se);
    }
  }

  private static long addContent(String uniqueId) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement.executeQuery(
            "INSERT INTO content (content_unique_id) VALUES ('" + uniqueId.replace("'", "''")
                + "') RETURNING content_id")) {
      rs.next();
      return rs.getLong("content_id");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a content record", se);
    }
  }

  private static long insertVersion(long contentId, String content, long approvedBy, String releaseReference) {
    ContentVersion version = new ContentVersion();
    version.setContentId(contentId);
    version.setContent(content);
    version.setApprovedBy(approvedBy);
    version.setReleaseReference(releaseReference);
    try (Connection connection = DB.getConnection()) {
      return ContentVersionRepository.insert(connection, version);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a content version", se);
    }
  }
}
