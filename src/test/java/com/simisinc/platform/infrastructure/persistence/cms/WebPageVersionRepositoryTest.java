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

import com.simisinc.platform.domain.model.cms.WebPageVersion;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebPageVersionRepository} against a real PostgreSQL instance (#405).
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available.
 * </p>
 *
 * @author elizabeth houser
 */
class WebPageVersionRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebPageVersionRepository integration test");

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
      statement.execute("TRUNCATE TABLE web_page_versions, web_pages RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_page_versions table", se);
    }
  }

  @Test
  void insertedVersionCanBeFoundById() {
    long webPageId = addWebPage("/insert-find");

    long versionId = insertVersion(webPageId, "<xml>v1</xml>", 7L, "First");

    WebPageVersion found = WebPageVersionRepository.findById(versionId);
    assertEquals(webPageId, found.getWebPageId());
    assertEquals("<xml>v1</xml>", found.getPageXml());
    assertEquals(7L, found.getPublishedBy());
    assertEquals("First", found.getLabel());
  }

  @Test
  void findByIdReturnsNullForAnUnknownId() {
    assertNull(WebPageVersionRepository.findById(999999L));
  }

  @Test
  void findByWebPageIdReturnsNullWhenThereAreNoVersions() {
    long webPageId = addWebPage("/no-versions");

    assertNull(WebPageVersionRepository.findByWebPageId(webPageId, null));
  }

  @Test
  void findByWebPageIdOnlyReturnsVersionsForThatPage() {
    long pageOne = addWebPage("/page-one");
    long pageTwo = addWebPage("/page-two");
    insertVersion(pageOne, "<xml>one</xml>", 1L, null);
    insertVersion(pageTwo, "<xml>two</xml>", 1L, null);

    List<WebPageVersion> results = WebPageVersionRepository.findByWebPageId(pageOne, null);

    assertEquals(1, results.size());
    assertEquals("<xml>one</xml>", results.get(0).getPageXml());
  }

  @Test
  void findByWebPageIdSortsNewestFirst() throws InterruptedException {
    long webPageId = addWebPage("/sort-order");
    long olderId = insertVersion(webPageId, "<xml>older</xml>", 1L, null);
    Thread.sleep(5);
    long newerId = insertVersion(webPageId, "<xml>newer</xml>", 1L, null);

    List<WebPageVersion> results = WebPageVersionRepository.findByWebPageId(webPageId, null);

    assertEquals(2, results.size());
    assertEquals(newerId, results.get(0).getId(), "the most recently published version should be listed first");
    assertEquals(olderId, results.get(1).getId());
  }

  @Test
  void pruneOldestKeepsOnlyTheMostRecentVersions() throws SQLException {
    long webPageId = addWebPage("/prune-me");
    for (int i = 0; i < 5; i++) {
      insertVersion(webPageId, "<xml>v" + i + "</xml>", 1L, null);
    }

    try (Connection connection = DB.getConnection()) {
      WebPageVersionRepository.pruneOldest(connection, webPageId, 2);
    }

    // Rapid-fire inserts can land in the same published_at millisecond, and findByWebPageId only
    // orders by published_at DESC (no id tiebreaker) -- so which of two same-millisecond rows sorts
    // first is not guaranteed. Assert the surviving *set* (what pruneOldest is actually responsible
    // for), not a tie-broken order that belongs to a different method.
    List<WebPageVersion> results = WebPageVersionRepository.findByWebPageId(webPageId, null);
    Set<String> remaining = results.stream().map(WebPageVersion::getPageXml).collect(Collectors.toSet());
    assertEquals(Set.of("<xml>v3</xml>", "<xml>v4</xml>"), remaining);
  }

  @Test
  void pruneOldestLeavesEverythingAloneWhenUnderTheLimit() throws SQLException {
    long webPageId = addWebPage("/under-limit");
    insertVersion(webPageId, "<xml>only</xml>", 1L, null);

    try (Connection connection = DB.getConnection()) {
      WebPageVersionRepository.pruneOldest(connection, webPageId, 20);
    }

    assertEquals(1, WebPageVersionRepository.findByWebPageId(webPageId, null).size());
  }

  @Test
  void findByWebPageIdHonorsDataConstraintsPaging() {
    long webPageId = addWebPage("/paged");
    for (int i = 0; i < 5; i++) {
      insertVersion(webPageId, "<xml>v" + i + "</xml>", 1L, null);
    }

    DataConstraints constraints = new DataConstraints(1, 2);
    List<WebPageVersion> firstPage = WebPageVersionRepository.findByWebPageId(webPageId, constraints);

    assertEquals(2, firstPage.size());
    assertNotEquals(firstPage.get(0).getId(), firstPage.get(1).getId());
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
    // A focused subset of the real schema -- no users table here, so published_by is a plain
    // column rather than an FK, matching the same simplification used elsewhere in this test suite.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_page_versions CASCADE");
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL)");
      statement.execute("CREATE TABLE web_page_versions ("
          + "web_page_version_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE, "
          + "page_xml TEXT, "
          + "published_by BIGINT, "
          + "published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "label VARCHAR(255))");
      statement.execute("CREATE INDEX web_page_versions_web_idx ON web_page_versions(web_page_id, published_at DESC)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_page_versions schema", se);
    }
  }

  private static long addWebPage(String link) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement.executeQuery(
            "INSERT INTO web_pages (link) VALUES ('" + link.replace("'", "''") + "') RETURNING web_page_id")) {
      rs.next();
      return rs.getLong("web_page_id");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a web page", se);
    }
  }

  private static long insertVersion(long webPageId, String pageXml, long publishedBy, String label) {
    WebPageVersion version = new WebPageVersion();
    version.setWebPageId(webPageId);
    version.setPageXml(pageXml);
    version.setPublishedBy(publishedBy);
    version.setLabel(label);
    try (Connection connection = DB.getConnection()) {
      return WebPageVersionRepository.insert(connection, version);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a web page version", se);
    }
  }
}
