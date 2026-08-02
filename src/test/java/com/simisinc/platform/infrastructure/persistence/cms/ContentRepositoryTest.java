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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
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
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
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
    // A focused subset of the real content table - enough for the add/find/remove path, plus the
    // real tsv column/trigger/text-search config (mirrored from NEW_10010__new_cms.sql) so the
    // search-OR-match tests below exercise the actual PLAINTO_TSQUERY('content_stem', ...) clause,
    // not a stand-in. The created_by/modified_by foreign keys are intentionally omitted; nothing
    // references content.
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
          + "draft_status VARCHAR(20), "
          + "submitted_by BIGINT DEFAULT -1, "
          + "approved_by BIGINT DEFAULT -1, "
          + "release_reference VARCHAR(255), "
          + "created_by BIGINT, "
          + "modified_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "tsv TSVECTOR)");

      statement.execute("CREATE TEXT SEARCH DICTIONARY content_stem (TEMPLATE = snowball, Language = english)");
      statement.execute("CREATE TEXT SEARCH CONFIGURATION content_stem (copy = english)");
      statement.execute("ALTER TEXT SEARCH CONFIGURATION content_stem "
          + "ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word WITH content_stem");

      statement.execute("CREATE OR REPLACE FUNCTION content_tsv_trigger() RETURNS trigger AS $$ "
          + "begin new.tsv := setweight(to_tsvector('content_stem', new.content_text), 'A'); return new; end "
          + "$$ LANGUAGE plpgsql");
      statement.execute("CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE "
          + "ON content FOR EACH ROW EXECUTE PROCEDURE content_tsv_trigger()");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the content schema", se);
    }
  }

  @Test
  void reviewWorkflowFieldsRoundTrip() {
    // A submitted-and-approved draft persists its review state (P1). submitted_by != approved_by is
    // the separation-of-duties data the audit evidence rests on, so prove it survives a save/reload.
    Content content = new Content();
    content.setUniqueId("under-review");
    content.setContent("<p>live</p>");
    content.setDraftContent("<p>proposed</p>");
    content.setDraftStatus("submitted");
    content.setSubmittedBy(10L);
    content.setApprovedBy(20L);
    content.setReleaseReference("cleared per PA case 2026-114");
    ContentRepository.save(content);

    Content reloaded = ContentRepository.findByUniqueId("under-review");
    assertNotNull(reloaded);
    assertEquals("submitted", reloaded.getDraftStatus());
    assertEquals(10L, reloaded.getSubmittedBy());
    assertEquals(20L, reloaded.getApprovedBy());
    assertEquals("cleared per PA case 2026-114", reloaded.getReleaseReference());
  }

  @Test
  void publishClearsTheReviewWorkflow() {
    // Publishing consumes the draft, so its workflow state resets; the durable record is the audit trail.
    Content content = new Content();
    content.setUniqueId("to-publish");
    content.setDraftContent("<p>approved draft</p>");
    content.setDraftStatus("submitted");
    content.setSubmittedBy(10L);
    content.setApprovedBy(20L);
    content.setReleaseReference("cleared per PA case 2026-115");
    ContentRepository.save(content);

    ContentRepository.publish(content);

    Content published = ContentRepository.findByUniqueId("to-publish");
    assertEquals("<p>approved draft</p>", published.getContent(), "the draft became the live content");
    assertNull(published.getDraftStatus());
    assertEquals(-1L, published.getSubmittedBy());
    assertEquals(-1L, published.getApprovedBy());
    assertNull(published.getReleaseReference());
  }

  private static Content addContent(String uniqueId, String html) {
    Content content = new Content();
    content.setUniqueId(uniqueId);
    content.setContent(html);
    return ContentRepository.save(content);
  }

  /** Runs the status filter and returns the matching unique ids (empty list, never null, when there are none). */
  private static List<String> uniqueIdsForStatus(String status) {
    ContentSpecification specification = new ContentSpecification();
    specification.setStatus(status);
    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());
    if (results == null) {
      return List.of();
    }
    return results.stream().map(Content::getUniqueId).toList();
  }

  /** Directly sets the modified timestamp (bypassing ContentRepository.update()'s "now" stamp) so date-range tests can pin known values. */
  private static void setModified(String uniqueId, Timestamp modified) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE content SET modified = ? WHERE content_unique_id = ?")) {
      pst.setTimestamp(1, modified);
      pst.setString(2, uniqueId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not set modified", se);
    }
  }

  /**
   * Directly overwrites content_text via raw SQL (bypassing HtmlCommand.text's HTML-stripping) so
   * character-count tests can pin an exact known length. The tsvectorupdate trigger still fires on
   * this UPDATE and regenerates tsv from the new content_text, so full-text search stays consistent.
   */
  private static void setContentText(String uniqueId, String text) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE content SET content_text = ? WHERE content_unique_id = ?")) {
      pst.setString(1, text);
      pst.setString(2, uniqueId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not set content_text", se);
    }
  }

  @Test
  void searchMatchesEitherUniqueIdSubstringOrBodyFullText() {
    // Assumptions.assumeTrue in startDatabase() already skipped this class when Docker/postgres isn't running
    addContent("careers-page-header", "<p>Welcome to our team</p>");
    addContent("generic-block", "<p>We are hiring for many career paths across the company</p>");
    addContent("unrelated-block", "<p>Nothing relevant here at all</p>");

    ContentSpecification specification = new ContentSpecification();
    specification.setSearchTerm("career");

    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());

    assertNotNull(results);
    List<String> uniqueIds = results.stream().map(Content::getUniqueId).toList();
    assertTrue(uniqueIds.contains("careers-page-header"), "should match by unique-id substring ('career' in 'careers-page-header')");
    assertTrue(uniqueIds.contains("generic-block"), "should match by body full-text ('career' stems from 'career paths' in the body)");
    assertFalse(uniqueIds.contains("unrelated-block"), "should not match a block whose id and body both lack the term");
    assertEquals(2, uniqueIds.size());
  }

  @Test
  void dateModifiedRangeFiltersCorrectly() {
    addContent("modified-early", "<p>Early</p>");
    addContent("modified-middle", "<p>Middle</p>");
    addContent("modified-late", "<p>Late</p>");
    setModified("modified-early", Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0)));
    setModified("modified-middle", Timestamp.valueOf(LocalDateTime.of(2026, 6, 15, 9, 0)));
    setModified("modified-late", Timestamp.valueOf(LocalDateTime.of(2026, 12, 31, 9, 0)));

    ContentSpecification specification = new ContentSpecification();
    specification.setDateModifiedAfter(Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 0, 0)));
    specification.setDateModifiedBefore(Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 0, 0)));

    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());

    assertNotNull(results);
    List<String> uniqueIds = results.stream().map(Content::getUniqueId).toList();
    assertEquals(List.of("modified-middle"), uniqueIds);
  }

  @Test
  void countByDateRangeCountsOnlyRowsInTheOpenClosedWindow() {
    // Same open-closed semantics as dateModifiedRangeFiltersCorrectly above (issue #634's
    // WebPage search date facet) -- exercised here as a count rather than a fetch.
    addContent("count-early", "<p>Early</p>");
    addContent("count-middle", "<p>Middle</p>");
    addContent("count-late", "<p>Late</p>");
    setModified("count-early", Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0)));
    setModified("count-middle", Timestamp.valueOf(LocalDateTime.of(2026, 6, 15, 9, 0)));
    setModified("count-late", Timestamp.valueOf(LocalDateTime.of(2026, 12, 31, 9, 0)));

    long count = ContentRepository.countByDateRange(null,
        Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 0, 0)),
        Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 0, 0)));

    assertEquals(1, count);
  }

  @Test
  void countByDateRangeWithAnOpenEndedStartCountsEverythingFromThatPointOn() {
    addContent("open-early", "<p>Early</p>");
    addContent("open-late", "<p>Late</p>");
    setModified("open-early", Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 9, 0)));
    setModified("open-late", Timestamp.valueOf(LocalDateTime.of(2026, 12, 31, 9, 0)));

    long count = ContentRepository.countByDateRange(null,
        Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)), null);

    assertEquals(1, count);
  }

  @Test
  void countByDateRangeCombinesWithTheSearchTermAsAnd() {
    addContent("term-match-in-range", "<p>career opportunities await</p>");
    addContent("term-match-out-of-range", "<p>career opportunities await</p>");
    addContent("no-term-in-range", "<p>unrelated content</p>");
    setModified("term-match-in-range", Timestamp.valueOf(LocalDateTime.of(2026, 6, 15, 9, 0)));
    setModified("term-match-out-of-range", Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 9, 0)));
    setModified("no-term-in-range", Timestamp.valueOf(LocalDateTime.of(2026, 6, 15, 9, 0)));

    long count = ContentRepository.countByDateRange("career",
        Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)), null);

    assertEquals(1, count);
  }

  @Test
  void characterCountRangeFiltersCorrectly() {
    addContent("chars-short", "<p>short</p>");
    addContent("chars-medium", "<p>medium</p>");
    addContent("chars-long", "<p>long</p>");
    setContentText("chars-short", "x".repeat(10));
    setContentText("chars-medium", "x".repeat(500));
    setContentText("chars-long", "x".repeat(5000));

    ContentSpecification specification = new ContentSpecification();
    specification.setMinLength(100);
    specification.setMaxLength(1000);

    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());

    assertNotNull(results);
    List<String> uniqueIds = results.stream().map(Content::getUniqueId).toList();
    assertEquals(List.of("chars-medium"), uniqueIds);
  }

  @Test
  void combinedFiltersAreAnded() {
    // The search term ORs id-vs-body internally, but combines with the date/length filters as AND
    // (SqlUtils.addIfExists chains are ANDed) -- a block matching the search but outside the date
    // range must not appear.
    addContent("combo-in-range", "<p>career opportunities await</p>");
    addContent("combo-out-of-range", "<p>career opportunities await</p>");
    setModified("combo-in-range", Timestamp.valueOf(LocalDateTime.of(2026, 6, 15, 9, 0)));
    setModified("combo-out-of-range", Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 9, 0)));

    ContentSpecification specification = new ContentSpecification();
    specification.setSearchTerm("career");
    specification.setDateModifiedAfter(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));

    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());

    assertNotNull(results);
    List<String> uniqueIds = results.stream().map(Content::getUniqueId).toList();
    assertEquals(List.of("combo-in-range"), uniqueIds);
  }

  // --- Status filter (issue #499: the /admin/content-list status column and dropdown) ---

  @Test
  void statusFilterReturnsOnlyTheMatchingRowForEachOfTheFourStates() {
    // Live: no draft in progress.
    addContent("status-live", "<p>live</p>");

    // Draft: a draft in progress, never submitted.
    Content draftBlock = addContent("status-draft", "<p>live</p>");
    draftBlock.setDraftContent("<p>editing</p>");
    ContentRepository.save(draftBlock);

    // Pending Review: submitted, not yet approved.
    Content pending = addContent("status-pending", "<p>live</p>");
    pending.setDraftContent("<p>proposed</p>");
    pending.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    pending.setSubmittedBy(10L);
    ContentRepository.save(pending);

    // Approved: submitted AND approved, but not yet published (still a distinct, later state).
    Content approved = addContent("status-approved", "<p>live</p>");
    approved.setDraftContent("<p>proposed</p>");
    approved.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    approved.setSubmittedBy(10L);
    approved.setApprovedBy(20L);
    ContentRepository.save(approved);

    // Each filter returns exactly its own row -- this also proves the other three rows are
    // correctly excluded, not just that the target row is included.
    assertEquals(List.of("status-live"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_LIVE));
    assertEquals(List.of("status-draft"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_DRAFT));
    assertEquals(List.of("status-pending"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_PENDING_REVIEW));
    assertEquals(List.of("status-approved"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_APPROVED));
  }

  @Test
  void noStatusFilterReturnsEveryRowRegardlessOfWorkflowState() {
    addContent("nofilter-live", "<p>live</p>");
    Content draftBlock = addContent("nofilter-draft", "<p>live</p>");
    draftBlock.setDraftContent("<p>editing</p>");
    ContentRepository.save(draftBlock);

    List<String> uniqueIds = uniqueIdsForStatus(null);

    assertTrue(uniqueIds.contains("nofilter-live"));
    assertTrue(uniqueIds.contains("nofilter-draft"));
  }

  @Test
  void aRejectedDraftFiltersAsDraftNotPendingOrApproved() throws DataException {
    // Exercises the real ContentReviewCommand transitions (submit -> reject), not hand-seeded
    // columns, so this proves the SQL filter matches what the actual workflow produces: reject()
    // resets draft_status back to STATUS_DRAFT, and there is no separate "Rejected" state.
    Content content = addContent("status-rejected", "<p>live</p>");
    content.setDraftContent("<p>revise me</p>");
    ContentReviewCommand.submitForReview(content, 10L);
    ContentRepository.save(content);

    assertEquals(List.of("status-rejected"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_PENDING_REVIEW));
    assertTrue(uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_DRAFT).isEmpty());

    ContentReviewCommand.reject(content, 20L);
    ContentRepository.save(content);

    assertEquals(List.of("status-rejected"), uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_DRAFT));
    assertTrue(uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_PENDING_REVIEW).isEmpty());
    assertTrue(uniqueIdsForStatus(ContentReviewCommand.LIST_STATUS_APPROVED).isEmpty());
  }

  @Test
  void statusFilterCombinesWithOtherFiltersAsAnd() {
    // The status filter must AND with the other filters (same convention as combinedFiltersAreAnded
    // above), not silently override or ignore them.
    Content matches = addContent("combo-status-match", "<p>career opportunities await</p>");
    matches.setDraftContent("<p>revised career copy</p>");
    ContentRepository.save(matches);

    Content wrongTerm = addContent("combo-status-wrong-term", "<p>unrelated</p>");
    wrongTerm.setDraftContent("<p>also unrelated</p>");
    ContentRepository.save(wrongTerm);

    addContent("combo-status-live-only", "<p>career opportunities await</p>"); // no draft -- wrong status

    ContentSpecification specification = new ContentSpecification();
    specification.setSearchTerm("career");
    specification.setStatus(ContentReviewCommand.LIST_STATUS_DRAFT);

    List<Content> results = ContentRepository.findAll(specification, new DataConstraints());

    assertNotNull(results);
    List<String> uniqueIds = results.stream().map(Content::getUniqueId).toList();
    assertEquals(List.of("combo-status-match"), uniqueIds);
  }
}
