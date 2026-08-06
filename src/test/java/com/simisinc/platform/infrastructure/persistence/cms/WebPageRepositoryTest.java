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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.domain.model.cms.WebPageVersion;
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
      statement.execute("TRUNCATE TABLE web_pages RESTART IDENTITY CASCADE");
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
  void searchExcludesArchivedPages() {
    // Issue #427: bulk Archive must actually take a page out of the internal search index too --
    // this was found missing in review (search() built its own raw WHERE clause and never
    // considered the archived column at all).
    WebPage webPage = addWebPage("/archived", "Widgets", null, "A page about widgets", true, true, false);
    webPage.setArchived(new Timestamp(System.currentTimeMillis()));
    WebPageRepository.save(webPage);

    List<WebPage> results = WebPageRepository.search("widgets", null);

    assertTrue(results.isEmpty(), "an archived page must not appear in search results");
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

  @Test
  void countExpiringSoonExcludesAPageWhoseExpiresAtIsFarInTheFuture() {
    // Regression test: countExpiringSoon() used to have no upper bound at all -- expires_at > now
    // -- so a page set to expire years out counted the same as one expiring tomorrow, and this
    // tile would sit permanently non-zero on any site that sets far-future expiration dates.
    addWebPageWithExpiresAt("/expires-way-later", new Timestamp(System.currentTimeMillis() + Duration.ofDays(60).toMillis()));

    assertEquals(0, WebPageRepository.countExpiringSoon());
  }

  // --- date-range and author filters (issue #426, editorial calendar) ---

  @Test
  void dateRangeFilterMatchesAPageWithAPublishAtInsideTheRange() {
    addWebPageWithPublishAt("/scheduled-in-range", Timestamp.valueOf("2026-08-15 09:00:00"));
    addWebPageWithPublishAt("/scheduled-outside-range", Timestamp.valueOf("2026-10-01 09:00:00"));

    WebPageSpecification specification = new WebPageSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    List<WebPage> results = WebPageRepository.findAll(specification, null);

    assertEquals(1, results.size());
    assertEquals("/scheduled-in-range", results.get(0).getLink());
  }

  @Test
  void dateRangeFilterMatchesAPageWithAnExpiresAtInsideTheRangeEvenWithNoPublishAt() {
    // Proves the OR: a page can match on expires_at alone, with publish_at left unset entirely.
    WebPage webPage = new WebPage();
    webPage.setLink("/expiring-in-range");
    webPage.setTitle("Expiring In Range");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setExpiresAt(Timestamp.valueOf("2026-08-20 09:00:00"));
    WebPageRepository.save(webPage);

    WebPageSpecification specification = new WebPageSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    List<WebPage> results = WebPageRepository.findAll(specification, null);

    assertEquals(1, results.size());
    assertEquals("/expiring-in-range", results.get(0).getLink());
  }

  @Test
  void dateRangeFilterExcludesAPageWithNeitherDateSet() {
    addWebPage("/no-schedule", "No Schedule", null, null, true, true, false);

    WebPageSpecification specification = new WebPageSpecification();
    specification.setStartingDateRange(Timestamp.valueOf("2026-08-01 00:00:00"));
    specification.setEndingDateRange(Timestamp.valueOf("2026-09-01 00:00:00"));

    assertTrue(WebPageRepository.findAll(specification, null).isEmpty());
  }

  @Test
  void createdByFilterReturnsOnlyPagesFromThatAuthor() {
    addWebPageWithAuthor("/from-author-1", 1L);
    addWebPageWithAuthor("/from-author-2", 2L);

    WebPageSpecification specification = new WebPageSpecification();
    specification.setCreatedBy(2L);

    List<WebPage> results = WebPageRepository.findAll(specification, null);

    assertEquals(1, results.size());
    assertEquals("/from-author-2", results.get(0).getLink());
  }

  @Test
  void createdByUnsetReturnsPagesFromEveryAuthor() {
    addWebPageWithAuthor("/from-author-1", 1L);
    addWebPageWithAuthor("/from-author-2", 2L);

    assertEquals(2, WebPageRepository.findAll(new WebPageSpecification(), null).size());
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

  // --- publish() version history (#405) ---

  @Test
  void publishSnapshotsTheOutgoingPageXmlAndPromotesTheDraft() {
    WebPage webPage = new WebPage();
    webPage.setLink("/versioned");
    webPage.setTitle("Versioned Page");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>v1</xml>");
    webPage.setDraftPageXml("<xml>v2</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);

    WebPageRepository.publish(saved, 42L, 20);

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>v2</xml>", reloaded.getPageXml());
    assertNull(reloaded.getDraftPageXml());
    assertFalse(reloaded.getDraft());

    List<WebPageVersion> versions = WebPageVersionRepository.findByWebPageId(saved.getId(), null);
    assertEquals(1, versions.size());
    assertEquals("<xml>v1</xml>", versions.get(0).getPageXml(), "the outgoing (pre-publish) xml must be snapshotted, not the incoming one");
    assertEquals(42L, versions.get(0).getPublishedBy());
  }

  @Test
  void publishIsANoOpWhenThereIsNoPendingDraft() {
    WebPage webPage = new WebPage();
    webPage.setLink("/no-draft");
    webPage.setTitle("No Draft");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>live</xml>");
    WebPage saved = WebPageRepository.save(webPage);

    WebPageRepository.publish(saved, 42L, 20);

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>live</xml>", reloaded.getPageXml());
    assertNull(WebPageVersionRepository.findByWebPageId(saved.getId(), null),
        "no version should be recorded when there was nothing to publish");
  }

  @Test
  void publishDoesNotSnapshotOnTheFirstEverPublish() {
    WebPage webPage = new WebPage();
    webPage.setLink("/first-publish");
    webPage.setTitle("First Publish");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setDraftPageXml("<xml>v1</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);

    WebPageRepository.publish(saved, 42L, 20);

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>v1</xml>", reloaded.getPageXml());
    assertNull(WebPageVersionRepository.findByWebPageId(saved.getId(), null),
        "a page with no prior live content has nothing to snapshot");
  }

  @Test
  void publishPrunesVersionsBeyondTheConfiguredLimit() {
    WebPage webPage = new WebPage();
    webPage.setLink("/many-versions");
    webPage.setTitle("Many Versions");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>v0</xml>");
    WebPage saved = WebPageRepository.save(webPage);

    for (int i = 1; i <= 5; i++) {
      saved.setDraftPageXml("<xml>v" + i + "</xml>");
      saved.setDraft(true);
      saved.setModifiedBy(1L);
      WebPageRepository.save(saved);
      WebPageRepository.publish(saved, 1L, 3);
      saved = WebPageRepository.findById(saved.getId());
    }

    List<WebPageVersion> versions = WebPageVersionRepository.findByWebPageId(saved.getId(), null);
    assertEquals(3, versions.size(), "only the configured limit of prior versions should be retained");
  }

  // --- publish() clears the review workflow (#948) ---

  @Test
  void publishClearsTheReviewWorkflowSoAFreshDraftMustBeReapproved() throws DataException {
    WebPage webPage = new WebPage();
    webPage.setLink("/governed");
    webPage.setTitle("Governed Page");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setDraftPageXml("<xml>v1</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);

    // Cycle 1: an ordinary submit -> approve -> publish.
    ContentReviewCommand.submitForReview(saved, 10L);
    WebPageRepository.save(saved);
    ContentReviewCommand.approve(saved, 20L, "CAB-123");
    WebPageRepository.publish(saved, 20L, 20);

    WebPage afterFirstPublish = WebPageRepository.findById(saved.getId());
    assertNull(afterFirstPublish.getDraftStatus(),
        "the consumed draft's review workflow must be cleared, not left \"submitted\"");
    assertEquals(-1L, afterFirstPublish.getSubmittedBy());
    assertEquals(-1L, afterFirstPublish.getApprovedBy());
    assertNull(afterFirstPublish.getReleaseReference());

    // Cycle 2: an entirely new, never-reviewed draft is staged on the same page (e.g. via the
    // layout builder's SaveDraftLayoutCommand) -- the exact scenario reported in #948.
    afterFirstPublish.setDraftPageXml("<xml>v2 -- never reviewed</xml>");
    afterFirstPublish.setDraft(true);
    afterFirstPublish.setModifiedBy(10L);
    WebPageRepository.save(afterFirstPublish);

    WebPage beforeSecondPublish = WebPageRepository.findById(saved.getId());
    assertFalse(ContentReviewCommand.mayPublish(beforeSecondPublish, true),
        "a fresh draft must not inherit the prior cycle's approval -- it has to be resubmitted and reapproved");
  }

  // --- publish() invalidates outstanding preview links (#419 review finding) ---

  @Test
  void publishInvalidatesAnyOutstandingPreviewTokenForThePage() {
    // Without this, a still-unexpired preview link generated for the draft that was just published
    // would keep validating afterward and could later resurface a completely different, unrelated
    // draft staged on this page before the token expires -- the review finding on issue #419.
    WebPage webPage = new WebPage();
    webPage.setLink("/preview-then-publish");
    webPage.setTitle("Preview Then Publish");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setDraftPageXml("<xml>v1</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);
    String token = addPreviewToken(saved.getId(), "/preview-then-publish");

    WebPageRepository.publish(saved, 42L, 20);

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, saved.getId(), "/preview-then-publish"),
        "a preview token must stop working once its draft has been published");
  }

  @Test
  void publishLeavesOtherPagesPreviewTokensAlone() {
    WebPage published = new WebPage();
    published.setLink("/gets-published");
    published.setEnabled(true);
    published.setSearchable(true);
    published.setCreatedBy(1L);
    published.setDraftPageXml("<xml>v1</xml>");
    published.setDraft(true);
    WebPage savedPublished = WebPageRepository.save(published);

    WebPage untouched = new WebPage();
    untouched.setLink("/stays-in-draft");
    untouched.setEnabled(true);
    untouched.setSearchable(true);
    untouched.setCreatedBy(1L);
    untouched.setDraftPageXml("<xml>still pending</xml>");
    untouched.setDraft(true);
    WebPage savedUntouched = WebPageRepository.save(untouched);
    String untouchedToken = addPreviewToken(savedUntouched.getId(), "/stays-in-draft");

    WebPageRepository.publish(savedPublished, 42L, 20);

    assertNotNull(WebPagePreviewTokenRepository.findValidToken(untouchedToken, savedUntouched.getId(), "/stays-in-draft"),
        "publishing one page must not invalidate an unrelated page's preview token");
  }

  // --- removeDraft() clears the review workflow (#958) ---

  @Test
  void removeDraftIsANoOpForAnUnsavedRecord() {
    // The only real caller (PageServlet's discardDraft action) already pre-checks id == -1 before
    // ever reaching this method, leaving the guard itself unexercised by anything -- this proves it
    // directly, so a future refactor that loosens or reorders the guard, or a new caller that skips
    // PageServlet's pre-check, fails loudly instead of throwing on an unsaved WebPage's null link.
    WebPage neverSaved = new WebPage();
    neverSaved.setLink("/never-saved");

    assertDoesNotThrow(() -> WebPageRepository.removeDraft(neverSaved));
    assertDoesNotThrow(() -> WebPageRepository.removeDraft(null));
  }

  @Test
  void removeDraftClearsTheReviewWorkflowSoADiscardedDraftCannotLeaveAStaleApproval() throws DataException {
    WebPage webPage = new WebPage();
    webPage.setLink("/discarded");
    webPage.setTitle("Discarded Draft Page");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>live</xml>");
    webPage.setDraftPageXml("<xml>pending review</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);

    // Submit and approve, then discard instead of publishing -- the exact scenario from #958: the
    // approval must not survive to be inherited by whatever draft comes next.
    ContentReviewCommand.submitForReview(saved, 10L);
    WebPageRepository.save(saved);
    ContentReviewCommand.approve(saved, 20L, "CAB-456");
    WebPageRepository.save(saved);

    WebPageRepository.removeDraft(saved);

    WebPage afterDiscard = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>live</xml>", afterDiscard.getPageXml(), "discarding a draft must not touch the live page");
    assertNull(afterDiscard.getDraftPageXml());
    assertFalse(afterDiscard.getDraft());
    assertNull(afterDiscard.getDraftStatus(),
        "a discarded draft's review workflow must be cleared, not left \"submitted\"/approved");
    assertEquals(-1L, afterDiscard.getSubmittedBy());
    assertEquals(-1L, afterDiscard.getApprovedBy());
    assertNull(afterDiscard.getReleaseReference());
  }

  @Test
  void removeDraftInvalidatesAnyOutstandingPreviewTokenForThePage() {
    // Same reasoning as publish() above (#419 review finding): a preview link for a discarded
    // draft must stop working immediately, or it could later resurface a different, unrelated
    // draft staged on this page before the token expires.
    WebPage webPage = new WebPage();
    webPage.setLink("/preview-then-discard");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setDraftPageXml("<xml>pending</xml>");
    webPage.setDraft(true);
    WebPage saved = WebPageRepository.save(webPage);
    String token = addPreviewToken(saved.getId(), "/preview-then-discard");

    WebPageRepository.removeDraft(saved);

    assertNull(WebPagePreviewTokenRepository.findValidToken(token, saved.getId(), "/preview-then-discard"),
        "a preview token must stop working once its draft has been discarded");
  }

  @Test
  void restoreDraftFromVersionLoadsXmlIntoTheDraftSlotWithoutTouchingTheLivePage() {
    WebPage webPage = new WebPage();
    webPage.setLink("/restore-me");
    webPage.setTitle("Restore Me");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>live</xml>");
    WebPage saved = WebPageRepository.save(webPage);

    boolean result = WebPageRepository.restoreDraftFromVersion(saved.getId(), "<xml>restored</xml>");

    assertTrue(result);
    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>live</xml>", reloaded.getPageXml(), "restoring must not touch the live page_xml");
    assertEquals("<xml>restored</xml>", reloaded.getDraftPageXml());
    assertTrue(reloaded.getDraft());
  }

  @Test
  void restoreDraftFromVersionClearsAnyStaleApprovalOnTheDraftItReplaces() {
    // A draft already submitted-and-approved must not let its approval carry over to a DIFFERENT
    // draft swapped in by a restore -- otherwise the restored content could publish without ever
    // actually being reviewed (the same bypass shape fixed for #957/#958, and for Content's own
    // restoreDraftFromVersion in #406).
    WebPage webPage = new WebPage();
    webPage.setLink("/restore-clears-approval");
    webPage.setTitle("Restore Clears Approval");
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPageXml("<xml>live</xml>");
    webPage.setDraftPageXml("<xml>a different, already-approved draft</xml>");
    webPage.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    webPage.setSubmittedBy(10L);
    webPage.setApprovedBy(20L);
    webPage.setReleaseReference("cleared per PA case 2026-405");
    WebPage saved = WebPageRepository.save(webPage);

    WebPageRepository.restoreDraftFromVersion(saved.getId(), "<xml>an older version</xml>");

    WebPage reloaded = WebPageRepository.findById(saved.getId());
    assertEquals("<xml>an older version</xml>", reloaded.getDraftPageXml());
    assertNull(reloaded.getDraftStatus());
    assertEquals(-1L, reloaded.getSubmittedBy());
    assertEquals(-1L, reloaded.getApprovedBy(), "the restored draft must require a fresh approval, not inherit the old one");
    assertNull(reloaded.getReleaseReference());
  }

  private static String addPreviewToken(long webPageId, String pagePath) {
    WebPagePreviewToken record = new WebPagePreviewToken();
    record.setWebPageId(webPageId);
    record.setPagePath(pagePath);
    record.setToken(java.util.UUID.randomUUID().toString());
    record.setExpiresAt(Timestamp.from(Instant.now().plusSeconds(3600)));
    record.setCreatedBy(1L);
    WebPagePreviewToken saved = WebPagePreviewTokenRepository.add(record);
    return saved.getToken();
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
          + "archived TIMESTAMP(3), "
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

      // Version history (#405) -- no users table in this focused schema, so published_by is left
      // as a plain column rather than an FK
      statement.execute("DROP TABLE IF EXISTS web_page_versions CASCADE");
      statement.execute("CREATE TABLE web_page_versions ("
          + "web_page_version_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE, "
          + "page_xml TEXT, "
          + "published_by BIGINT, "
          + "published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "label VARCHAR(255))");

      // Draft preview links (#419) -- publish()/removeDraft() both delete a page's outstanding
      // tokens, so this table must exist here even though this test never populates it directly.
      statement.execute("DROP TABLE IF EXISTS web_page_preview_tokens CASCADE");
      statement.execute("CREATE TABLE web_page_preview_tokens ("
          + "web_page_preview_token_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE, "
          + "page_path VARCHAR(255) NOT NULL, "
          + "token VARCHAR(255) UNIQUE NOT NULL, "
          + "expires_at TIMESTAMP(3) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL)");
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

  private static WebPage addWebPageWithPublishAt(String link, Timestamp publishAt) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle(link);
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(1L);
    webPage.setPublishAt(publishAt);
    return WebPageRepository.save(webPage);
  }

  private static WebPage addWebPageWithAuthor(String link, long createdBy) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle(link);
    webPage.setEnabled(true);
    webPage.setSearchable(true);
    webPage.setCreatedBy(createdBy);
    return WebPageRepository.save(webPage);
  }
}
