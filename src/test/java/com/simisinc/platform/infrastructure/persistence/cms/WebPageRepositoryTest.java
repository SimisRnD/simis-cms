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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebPageRepository#search(String, com.simisinc.platform.infrastructure.database.DataConstraints)}
 * against a real PostgreSQL instance, since ranking and tsvector matching cannot be exercised
 * meaningfully with a mock.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack, including the web_pages_tsv_trigger
 * this test schema installs. It is skipped automatically when Docker is not available.
 * </p>
 *
 * @author elizabeth houser
 */
class WebPageRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebPageRepository integration test");

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
      statement.execute("TRUNCATE TABLE web_pages RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_pages table", se);
    }
  }

  @Test
  void searchRanksATitleMatchAboveADescriptionOnlyMatch() {
    addWebPage("/about", "Widgets", null, "A page about our company", true, true, false);
    addWebPage("/faq", "Frequently Asked Questions", null, "Where do widgets come from?", true, true, false);

    List<WebPage> results = WebPageRepository.search("widgets", null);

    assertEquals(2, results.size());
    assertEquals("/about", results.get(0).getLink(), "the title match should outrank the description-only match");
  }

  @Test
  void searchExcludesPagesThatAreNotSearchable() {
    addWebPage("/hidden", "Widgets", null, "A page about widgets", true, false, false);

    List<WebPage> results = WebPageRepository.search("widgets", null);

    assertTrue(results.isEmpty(), "a page with searchable=false must not appear in results");
  }

  @Test
  void searchExcludesDisabledPages() {
    addWebPage("/disabled", "Widgets", null, "A page about widgets", false, true, false);

    List<WebPage> results = WebPageRepository.search("widgets", null);

    assertTrue(results.isEmpty(), "a page with enabled=false must not appear in results");
  }

  @Test
  void searchMatchesOnKeywordsToo() {
    addWebPage("/products", "Our Catalog", "widgets, gadgets", "See what we sell", true, true, false);

    List<WebPage> results = WebPageRepository.search("gadgets", null);

    assertEquals(1, results.size());
    assertEquals("/products", results.get(0).getLink());
  }

  @Test
  void searchReturnsAllSearchablePagesWhenTheTermIsBlank() {
    addWebPage("/one", "One", null, null, true, true, false);
    addWebPage("/two", "Two", null, null, true, true, false);

    List<WebPage> results = WebPageRepository.search("", null);

    assertEquals(2, results.size());
  }

  @Test
  void searchReturnsNoMatchesForAnUnrelatedTerm() {
    addWebPage("/about", "Widgets", null, "A page about our company", true, true, false);

    List<WebPage> results = WebPageRepository.search("xylophone", null);

    assertTrue(results.isEmpty());
  }

  @Test
  void countExpiringSoonCountsAPageWithAFutureExpiresAt() {
    addWebPageWithExpiresAt("/soon-to-expire", new Timestamp(System.currentTimeMillis() + Duration.ofDays(1).toMillis()));

    assertEquals(1, WebPageRepository.countExpiringSoon());
  }

  @Test
  void countExpiringSoonExcludesAPageWithNoExpiresAtSet() {
    addWebPage("/no-expiry", "No Expiry", null, null, true, true, false);

    assertEquals(0, WebPageRepository.countExpiringSoon());
  }

  @Test
  void countExpiringSoonExcludesAPageWhoseExpiresAtHasAlreadyPassed() {
    addWebPageWithExpiresAt("/already-expired", new Timestamp(System.currentTimeMillis() - Duration.ofDays(1).toMillis()));

    assertEquals(0, WebPageRepository.countExpiringSoon());
  }

  // --- solution_type persistence (issue #570) ---

  @Test
  void savingANewPageWithASolutionTypePersistsAndReloadsIt() {
    WebPage webPage = new WebPage();
    webPage.setLink("/solutions/cmmc");
    webPage.setTitle("CMMC Compliance");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setSolutionType("government-solution");
    WebPage saved = WebPageRepository.save(webPage);

    assertEquals("government-solution", saved.getSolutionType());
    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("government-solution", reloaded.getSolutionType());
  }

  @Test
  void savingAPageWithNoSolutionTypeLeavesItNull() {
    WebPage webPage = addWebPage("/about", "About", null, null, true, true, false);

    WebPage reloaded = WebPageRepository.findById(webPage.getId());

    assertNull(reloaded.getSolutionType());
  }

  @Test
  void updatingAPageCanClearAPreviouslySetSolutionType() {
    WebPage webPage = new WebPage();
    webPage.setLink("/careers/engineering");
    webPage.setTitle("Engineering Careers");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setSolutionType("careers");
    WebPage saved = WebPageRepository.save(webPage);
    assertEquals("careers", WebPageRepository.findById(saved.getId()).getSolutionType());

    saved.setSolutionType(null);
    saved.setModifiedBy(1L);
    WebPageRepository.save(saved);

    assertNull(WebPageRepository.findById(saved.getId()).getSolutionType());
  }

  // --- governed publish workflow persistence (issue #407) ---

  @Test
  void savingAPageMidReviewPersistsAndReloadsAllFourFields() {
    WebPage webPage = new WebPage();
    webPage.setLink("/solutions/mid-review");
    webPage.setTitle("Mid Review");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    webPage.setSubmittedBy(5L);
    webPage.setApprovedBy(-1L);
    webPage.setReleaseReference(null);
    WebPage saved = WebPageRepository.save(webPage);

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals(ContentReviewCommand.STATUS_SUBMITTED, reloaded.getDraftStatus());
    assertEquals(5L, reloaded.getSubmittedBy());
    assertEquals(-1L, reloaded.getApprovedBy());
    assertNull(reloaded.getReleaseReference());
  }

  @Test
  void savingAnApprovalPersistsTheApproverAndReleaseReference() {
    WebPage webPage = addWebPage("/solutions/approved-page", "Approved Page", null, null, true, true, false);
    webPage.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    webPage.setSubmittedBy(5L);
    webPage.setModifiedBy(1L);
    WebPageRepository.save(webPage);

    webPage.setApprovedBy(9L);
    webPage.setReleaseReference("cleared per PA case 2026-114");
    webPage.setModifiedBy(1L);
    WebPageRepository.save(webPage);

    WebPage reloaded = WebPageRepository.findById(webPage.getId());
    assertEquals(9L, reloaded.getApprovedBy());
    assertEquals("cleared per PA case 2026-114", reloaded.getReleaseReference());
  }

  @Test
  void publishResetsTheReviewWorkflowFields() {
    WebPage webPage = new WebPage();
    webPage.setLink("/solutions/publish-resets-review");
    webPage.setTitle("Publish Resets Review");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>live</xml>");
    webPage.setDraftPageXml("<xml>draft</xml>");
    webPage.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    webPage.setSubmittedBy(5L);
    webPage.setApprovedBy(9L);
    webPage.setReleaseReference("cleared per PA case 2026-114");
    WebPage saved = WebPageRepository.save(webPage);

    WebPageRepository.publish(saved);

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>draft</xml>", reloaded.getPageXml());
    assertNull(reloaded.getDraftPageXml());
    assertNull(reloaded.getDraftStatus());
    assertEquals(-1L, reloaded.getSubmittedBy());
    assertEquals(-1L, reloaded.getApprovedBy());
    assertNull(reloaded.getReleaseReference());
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
    // A focused subset of the real web_pages schema, plus the title_stem text search config and
    // web_pages_tsv_trigger this migration installs (title_stem itself lives in the real app's
    // NEW_10024__new_items.sql, which this throwaway DB never runs, so it's recreated here too).
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL, "
          + "redirect_url VARCHAR(255), "
          + "page_title VARCHAR(255), "
          + "page_keywords VARCHAR(255), "
          + "page_description VARCHAR(255), "
          + "draft BOOLEAN DEFAULT false, "
          + "enabled BOOLEAN DEFAULT true, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "role_id_list VARCHAR(100), "
          + "page_xml TEXT, "
          + "draft_page_xml TEXT, "
          + "comments TEXT, "
          + "page_image_url VARCHAR(255), "
          + "searchable BOOLEAN DEFAULT true, "
          + "show_in_sitemap BOOLEAN DEFAULT true, "
          + "has_redirect BOOLEAN DEFAULT false, "
          + "sitemap_priority NUMERIC(2,1) DEFAULT 0.5, "
          + "sitemap_changefreq VARCHAR(20), "
          + "publish_at TIMESTAMP, "
          + "expires_at TIMESTAMP, "
          + "solution_type VARCHAR(255), "
          + "draft_status VARCHAR(20), "
          + "submitted_by BIGINT DEFAULT -1, "
          + "approved_by BIGINT DEFAULT -1, "
          + "release_reference VARCHAR(255), "
          + "tsv tsvector)");

      statement.execute("CREATE TEXT SEARCH DICTIONARY title_stem (TEMPLATE = snowball, Language = english)");
      statement.execute("CREATE TEXT SEARCH CONFIGURATION title_stem (copy = english)");
      statement.execute("ALTER TEXT SEARCH CONFIGURATION title_stem "
          + "ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word WITH title_stem");

      statement.execute("CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$ "
          + "begin "
          + "  new.tsv := "
          + "    setweight(to_tsvector('title_stem', COALESCE(new.page_title, '')), 'A') || "
          + "    setweight(to_tsvector('title_stem', COALESCE(new.page_keywords, '')), 'B') || "
          + "    setweight(to_tsvector('title_stem', COALESCE(new.page_description, '')), 'C'); "
          + "  return new; "
          + "end $$ LANGUAGE plpgsql");
      statement.execute("CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE "
          + "ON web_pages FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger()");
      statement.execute("CREATE INDEX web_pages_tsv_idx ON web_pages USING gin(tsv)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_pages schema", se);
    }
  }

  private static WebPage addWebPage(String link, String title, String keywords, String description,
      boolean enabled, boolean searchable, boolean draft) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle(title);
    webPage.setKeywords(keywords);
    webPage.setDescription(description);
    webPage.setEnabled(enabled);
    webPage.setSearchable(searchable);
    webPage.setDraft(draft);
    webPage.setCreatedBy(1L);
    return WebPageRepository.save(webPage);
  }

  private static WebPage addWebPageWithExpiresAt(String link, Timestamp expiresAt) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle(link);
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setExpiresAt(expiresAt);
    return WebPageRepository.save(webPage);
  }
}
