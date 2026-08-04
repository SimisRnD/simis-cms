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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebPagePreviewTokenRepository} against a real PostgreSQL instance (#419).
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available.
 * </p>
 *
 * @author elizabeth houser
 */
class WebPagePreviewTokenRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String PAGE_PATH = "/find-me";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebPagePreviewTokenRepository integration test");

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
      statement.execute("TRUNCATE TABLE web_page_preview_tokens, web_pages RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_page_preview_tokens table", se);
    }
  }

  @Test
  void addedTokenCanBeFoundByFindValidToken() {
    long webPageId = addWebPage("/find-me");
    String token = UUID.randomUUID().toString();

    WebPagePreviewToken saved = addToken(webPageId, PAGE_PATH, token, future(60));

    assertNotEquals(-1L, saved.getId());
    WebPagePreviewToken found = WebPagePreviewTokenRepository.findValidToken(token, webPageId, PAGE_PATH);
    assertNotNull(found);
    assertEquals(webPageId, found.getWebPageId());
    assertEquals(PAGE_PATH, found.getPagePath());
    assertEquals(token, found.getToken());
  }

  @Test
  void findValidTokenReturnsNullForAnExpiredToken() {
    long webPageId = addWebPage("/expired");
    String token = UUID.randomUUID().toString();
    addToken(webPageId, "/expired", token, past(60));

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, webPageId, "/expired"));
  }

  @Test
  void findValidTokenReturnsNullForAnUnknownToken() {
    long webPageId = addWebPage("/unknown-token");

    assertNull(WebPagePreviewTokenRepository.findValidToken("not-a-real-token", webPageId, "/unknown-token"));
  }

  @Test
  void findValidTokenReturnsNullWhenTheTokenBelongsToADifferentPage() {
    long pageOne = addWebPage("/page-one");
    long pageTwo = addWebPage("/page-two");
    String token = UUID.randomUUID().toString();
    addToken(pageOne, "/page-one", token, future(60));

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, pageTwo, "/page-one"));
  }

  @Test
  void findValidTokenReturnsNullForABlankToken() {
    long webPageId = addWebPage("/blank-token");

    assertNull(WebPagePreviewTokenRepository.findValidToken("", webPageId, "/blank-token"));
    assertNull(WebPagePreviewTokenRepository.findValidToken(null, webPageId, "/blank-token"));
  }

  @Test
  void findValidTokenReturnsNullWhenThePathDoesNotMatchTheOneItWasMintedFor() {
    // Regression test (#419 review finding): a wildcard page like "/news/*" backs many distinct
    // URLs from a single web_page_id -- a token minted while previewing one article must not
    // validate for any other URL that happens to resolve to the same page row.
    long webPageId = addWebPage("/news/*");
    String token = UUID.randomUUID().toString();
    addToken(webPageId, "/news/my-post-slug", token, future(60));

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, webPageId, "/news/some-other-slug"));
    assertNotNull(WebPagePreviewTokenRepository.findValidToken(token, webPageId, "/news/my-post-slug"));
  }

  @Test
  void findValidTokenReturnsNullForABlankPagePath() {
    long webPageId = addWebPage("/blank-path");

    assertNull(WebPagePreviewTokenRepository.findValidToken("some-token", webPageId, ""));
    assertNull(WebPagePreviewTokenRepository.findValidToken("some-token", webPageId, null));
  }

  @Test
  void removeAllForPageDeletesOnlyThatPagesTokens() {
    long pageOne = addWebPage("/page-one");
    long pageTwo = addWebPage("/page-two");
    String tokenOne = UUID.randomUUID().toString();
    String tokenTwo = UUID.randomUUID().toString();
    addToken(pageOne, "/page-one", tokenOne, future(60));
    addToken(pageTwo, "/page-two", tokenTwo, future(60));

    WebPagePreviewTokenRepository.removeAllForPage(pageOne);

    assertNull(WebPagePreviewTokenRepository.findValidToken(tokenOne, pageOne, "/page-one"),
        "the removed page's token must no longer validate");
    assertNotNull(WebPagePreviewTokenRepository.findValidToken(tokenTwo, pageTwo, "/page-two"),
        "an unrelated page's token must be untouched");
  }

  @Test
  void removeAllForPageWithAConnectionParticipatesInTheCallersTransaction() throws SQLException {
    long webPageId = addWebPage("/tx-page");
    String token = UUID.randomUUID().toString();
    addToken(webPageId, "/tx-page", token, future(60));

    try (Connection connection = DB.getConnection()) {
      WebPagePreviewTokenRepository.removeAllForPage(connection, webPageId);
    }

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, webPageId, "/tx-page"));
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
    // A focused subset of the real schema -- no users table here, so created_by is a plain
    // column rather than an FK, matching the same simplification used elsewhere in this test suite.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_page_preview_tokens CASCADE");
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL)");
      statement.execute("CREATE TABLE web_page_preview_tokens ("
          + "web_page_preview_token_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE, "
          + "page_path VARCHAR(255) NOT NULL, "
          + "token VARCHAR(255) UNIQUE NOT NULL, "
          + "expires_at TIMESTAMP(3) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL)");
      statement.execute("CREATE INDEX web_page_preview_tokens_token_idx ON web_page_preview_tokens(token)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_page_preview_tokens schema", se);
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

  private static WebPagePreviewToken addToken(long webPageId, String pagePath, String token, Timestamp expiresAt) {
    WebPagePreviewToken record = new WebPagePreviewToken();
    record.setWebPageId(webPageId);
    record.setPagePath(pagePath);
    record.setToken(token);
    record.setExpiresAt(expiresAt);
    record.setCreatedBy(1L);
    return WebPagePreviewTokenRepository.add(record);
  }

  private static Timestamp future(int seconds) {
    return Timestamp.from(Instant.now().plusSeconds(seconds));
  }

  private static Timestamp past(int seconds) {
    return Timestamp.from(Instant.now().minusSeconds(seconds));
  }
}
