/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.ContentVersion;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves content objects
 *
 * @author matt rajkowski
 * @created 4/8/18 4:33 PM
 */
public class ContentRepository {

  private static Log LOG = LogFactory.getLog(ContentRepository.class);

  private static String TABLE_NAME = "content";
  private static String[] PRIMARY_KEY = new String[]{"content_id"};

  private static DataResult query(ContentSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = null;
    SqlUtils orderBy = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("content_id = ?", specification.getId(), -1)
          .addIfExists("content_unique_id = ?", specification.getUniqueId());
      if (StringUtils.isNotBlank(specification.getSearchTerm())) {
        String term = specification.getSearchTerm().trim();
        select.add("ts_headline('english', content_text, PLAINTO_TSQUERY('content_stem', ?), 'StartSel=${b}, StopSel=${/b}, MaxWords=30, MinWords=15, ShortWord=3, HighlightAll=FALSE, MaxFragments=2, FragmentDelimiter=\" ... \"') AS highlight", term);
        select.add("TS_RANK_CD(tsv, PLAINTO_TSQUERY('content_stem', ?)) AS rank", term);
        // A single search box matches EITHER the unique id (substring) OR the body text
        // (full-text) -- SqlUtils.addIfExists chains are ANDed, so this needs to be one raw
        // parameterized OR fragment rather than two separate where.add() calls. Fully
        // parameterized (placeholders only) to avoid SQL injection.
        where.add("(content_unique_id ILIKE ? OR tsv @@ PLAINTO_TSQUERY('content_stem', ?))",
            new Object[]{"%" + term + "%", term});
        // Override the order by for rank first (an id-substring-only match ranks 0 and sorts last)
        orderBy = new SqlUtils();
        orderBy.add("rank DESC, content_id");
      }
      where.addIfExists("modified >= ?", specification.getDateModifiedAfter());
      where.addIfExists("modified < ?", specification.getDateModifiedBefore());
      // Character count is measured against content_text (HTML-stripped plain text), the same
      // column the full-text search indexes -- not the raw HTML in the content column.
      where.addIfExists("LENGTH(content_text) >= ?", specification.getMinLength(), -1);
      where.addIfExists("LENGTH(content_text) <= ?", specification.getMaxLength(), -1);
      // Applied in the WHERE clause (not filtered client-side after fetch) so DB.selectAllFrom's
      // paired COUNT(*) query -- and therefore pagination -- stays correct.
      addStatusFilter(where, specification.getStatus());
    }
    return DB.selectAllFrom(TABLE_NAME, select, where, orderBy, constraints, ContentRepository::buildRecord);
  }

  /**
   * Translates a {@code ContentReviewCommand.LIST_STATUS_*} label into the SQL WHERE fragment that
   * selects exactly the rows in that state, mirroring {@link ContentReviewCommand#listStatusLabel}'s
   * derivation column-by-column so the two can't silently drift apart. An unrecognized or blank
   * status applies no filter (equivalent to "All"), matching this query's existing leniency toward
   * malformed filter input (e.g. an unparsable date is likewise just ignored).
   */
  private static void addStatusFilter(SqlUtils where, String status) {
    if (StringUtils.isBlank(status)) {
      return;
    }
    // "No draft" -- ContentRepository#add/#update always normalize draft_content through
    // StringUtils.trimToNull before writing, but TRIM(...) = '' is matched too so this mirrors
    // StringUtils.isBlank(content.getDraftContent()) exactly rather than assuming that invariant.
    String hasDraft = "draft_content IS NOT NULL AND TRIM(draft_content) <> ''";
    if (ContentReviewCommand.LIST_STATUS_LIVE.equals(status)) {
      where.add("(draft_content IS NULL OR TRIM(draft_content) = '')");
    } else if (ContentReviewCommand.LIST_STATUS_APPROVED.equals(status)) {
      // isPendingReview(content) && content.getApprovedBy() > 0
      where.add("(" + hasDraft + " AND draft_status = ? AND approved_by > 0)",
          ContentReviewCommand.STATUS_SUBMITTED);
    } else if (ContentReviewCommand.LIST_STATUS_PENDING_REVIEW.equals(status)) {
      // isPendingReview(content) && !(approvedBy > 0)
      where.add("(" + hasDraft + " AND draft_status = ? AND (approved_by IS NULL OR approved_by <= 0))",
          ContentReviewCommand.STATUS_SUBMITTED);
    } else if (ContentReviewCommand.LIST_STATUS_DRAFT.equals(status)) {
      // Has a draft, but draftStatus is null or STATUS_DRAFT (not submitted) -- includes a
      // rejected-and-not-yet-resubmitted draft, which reject() resets to STATUS_DRAFT.
      where.add("(" + hasDraft + " AND (draft_status IS NULL OR draft_status <> ?))",
          ContentReviewCommand.STATUS_SUBMITTED);
    }
  }

  public static Content findByUniqueId(String contentUniqueId) {
    if (StringUtils.isBlank(contentUniqueId)) {
      return null;
    }
    return (Content) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("content_unique_id = ?", contentUniqueId),
        ContentRepository::buildRecord);
  }

  public static List<Content> findAll() {
    return findAll(null, null);
  }

  public static List<Content> findAll(ContentSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("content_unique_id");
    DataResult result = query(specification, constraints);
    if (result.hasRecords()) {
      return (List<Content>) result.getRecords();
    }
    return null;
  }

  public static long countByDraftStatus(String draftStatus) {
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("draft_status = ?", draftStatus));
  }

  /**
   * Count of content rows matching searchTerm whose modified timestamp falls in [start, end)
   * (either bound may be null for open-ended), for the WebPage search date facet (issue #634).
   * This is a content-match count, not the final displayed web page count -- WebPageSearchResultsWidget
   * cross-references each matching content item against which pages embed it and are visible/
   * navigable to the current user, so the true result count can be slightly lower than this in the
   * rare case a matched content item turns out to live only on an unlisted or permission-restricted
   * page. Exact parity would require re-running that whole cross-reference per bucket; this
   * approximation was chosen instead, mirroring the same search-term matching query() uses.
   */
  public static long countByDateRange(String searchTerm, Timestamp start, Timestamp end) {
    SqlUtils where = new SqlUtils();
    if (StringUtils.isNotBlank(searchTerm)) {
      String term = searchTerm.trim();
      where.add("(content_unique_id ILIKE ? OR tsv @@ PLAINTO_TSQUERY('content_stem', ?))",
          new Object[]{"%" + term + "%", term});
    }
    where.addIfExists("modified >= ?", start);
    where.addIfExists("modified < ?", end);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static Content save(Content record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Content add(Content record) {
    SqlUtils insertValues = new SqlUtils()
        .add("content_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("content", StringUtils.trimToNull(record.getContent()))
        .add("content_text", HtmlCommand.text(StringUtils.trimToNull(record.getContent())))
        .add("draft_content", StringUtils.trimToNull(record.getDraftContent()))
        .add("content_format", record.getContentFormat())
        .add("draft_content_format", record.getDraftContentFormat())
        .add("draft_status", StringUtils.trimToNull(record.getDraftStatus()))
        .add("submitted_by", record.getSubmittedBy())
        .add("approved_by", record.getApprovedBy())
        .add("release_reference", StringUtils.trimToNull(record.getReleaseReference()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Content update(Content record) {
    SqlUtils updateValues = new SqlUtils()
        .add("content", StringUtils.trimToNull(record.getContent()))
        .add("content_text", HtmlCommand.text(StringUtils.trimToNull(record.getContent())))
        .add("draft_content", StringUtils.trimToNull(record.getDraftContent()))
        .add("content_format", record.getContentFormat())
        .add("draft_content_format", record.getDraftContentFormat())
        .add("draft_status", StringUtils.trimToNull(record.getDraftStatus()))
        .add("submitted_by", record.getSubmittedBy())
        .add("approved_by", record.getApprovedBy())
        .add("release_reference", StringUtils.trimToNull(record.getReleaseReference()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("content_unique_id = ?", StringUtils.trimToNull(record.getUniqueId()));
    if (DB.update(TABLE_NAME, updateValues, where)) {
      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static final int DEFAULT_VERSION_HISTORY_LIMIT = 20;

  /** Parses the configured version-history cap to a bounded positive integer, defaulting to 20. */
  public static int resolveVersionHistoryLimit(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_VERSION_HISTORY_LIMIT;
    }
    try {
      int limit = Integer.parseInt(value.trim());
      return Math.max(limit, 1);
    } catch (NumberFormatException e) {
      return DEFAULT_VERSION_HISTORY_LIMIT;
    }
  }

  /**
   * Promotes draftContent to the live content (#406). Before overwriting it, the outgoing content is
   * rendered to plain HTML (format-aware, matching {@link ContentHtmlCommand#toHtml}) and snapshotted
   * into content_versions -- within the same transaction as the publish itself, so a failed snapshot
   * can't silently leave a publish with no recoverable prior state -- then pruned to
   * versionHistoryLimit. The approver and release authority recorded on the version row come straight
   * off {@code record}, which the caller (ContentHtmlCommand) has already stamped via
   * ContentReviewCommand before reaching here; an ungoverned direct publish leaves them at their -1/
   * null defaults, same as the live row itself. A content block with no live content yet (first-ever
   * publish) has nothing to snapshot.
   */
  public static void publish(Content record, int versionHistoryLimit) {
    if (StringUtils.isBlank(record.getUniqueId())) {
      return;
    }
    try (Connection connection = DB.getConnection();
        AutoStartTransaction ignored = new AutoStartTransaction(connection);
        AutoRollback transaction = new AutoRollback(connection)) {

      long contentId = -1;
      String outgoingContent = null;
      int outgoingContentFormat = 0;
      try (PreparedStatement pst = connection.prepareStatement(
          "SELECT content_id, content, content_format FROM " + TABLE_NAME
              + " WHERE content_unique_id = ? AND draft_content IS NOT NULL")) {
        pst.setString(1, record.getUniqueId());
        try (ResultSet rs = pst.executeQuery()) {
          if (!rs.next()) {
            // Nothing to publish (no pending draft) -- matches the prior no-op behavior
            return;
          }
          contentId = rs.getLong("content_id");
          outgoingContent = rs.getString("content");
          outgoingContentFormat = rs.getInt("content_format");
        }
      }

      if (StringUtils.isNotBlank(outgoingContent)) {
        ContentVersion version = new ContentVersion();
        version.setContentId(contentId);
        version.setContent(ContentHtmlCommand.toHtml(outgoingContent, outgoingContentFormat));
        version.setApprovedBy(record.getApprovedBy());
        version.setReleaseReference(record.getReleaseReference());
        if (ContentVersionRepository.insert(connection, version) == -1) {
          throw new SQLException("The prior content version was not saved");
        }
        ContentVersionRepository.pruneOldest(connection, contentId, versionHistoryLimit);
      }

      // Handle publishing and making sure there is content to publish
      SqlUtils updateValues = new SqlUtils();
      updateValues.add("content = draft_content");
      updateValues.add("draft_content = null");
      // Promote the draft's format stamp with its content, then clear it alongside the emptied draft.
      updateValues.add("content_format = draft_content_format");
      updateValues.add("draft_content_format = 0");
      // The draft is consumed, so clear its review workflow. The durable record of who submitted and
      // approved, and under what release authority, lives in the append-only audit trail (and now
      // content_versions above), not here.
      updateValues.add("draft_status = null");
      updateValues.add("submitted_by = -1");
      updateValues.add("approved_by = -1");
      updateValues.add("release_reference = null");
      updateValues.add("content_text", HtmlCommand.text(StringUtils.trimToNull(record.getContent())));
      SqlUtils where = new SqlUtils().add("draft_content IS NOT NULL AND content_unique_id = ?", record.getUniqueId());
      if (DB.update(connection, TABLE_NAME, updateValues, where)) {
        transaction.commit();
        CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
  }

  public static void removeDraft(Content record) {
    if (record == null || StringUtils.isBlank(record.getUniqueId())) {
      return;
    }
    String set = "draft_content = null, draft_content_format = 0, "
        + "draft_status = null, submitted_by = -1, approved_by = -1, release_reference = null";
    SqlUtils where = new SqlUtils().add("content_unique_id = ?", record.getUniqueId());
    if (DB.update(TABLE_NAME, set, where)) {
      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
    }
  }

  /**
   * Loads a prior version's content into the draft slot (#406) -- never touches the live content, so
   * a subsequent publish is required to make the restored content live again. Also resets the
   * governed-review fields unconditionally: unlike WebPageRepository#restoreDraftFromVersion (its
   * #405 precedent, which leaves them untouched), a restore here always clears any
   * draftStatus/submittedBy/approvedBy left over from whatever draft cycle was in progress before the
   * restore -- otherwise a pending approval on the *old* draft could be inherited by the *restored*
   * content, publishing it without ever actually being reviewed (the #957/#958 bypass shape).
   */
  public static boolean restoreDraftFromVersion(long contentId, String content) {
    SqlUtils updateValues = new SqlUtils()
        .add("draft_content", content)
        .add("draft_content_format", 0)
        .add("draft_status = null")
        .add("submitted_by = -1")
        .add("approved_by = -1")
        .add("release_reference = null");
    SqlUtils where = new SqlUtils().add("content_id = ?", contentId);
    return DB.update(TABLE_NAME, updateValues, where);
  }

  public static boolean remove(Content record) {
    if (record == null || record.getId() == null || record.getId() < 0) {
      return false;
    }
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // No other tables reference content_id, so deleting the record is sufficient
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("content_id = ?", record.getId()));
        // Finish the transaction
        transaction.commit();
        // Keep the cache in sync so the removed content no longer resolves
        CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static Content buildRecord(ResultSet rs) {
    try {
      Content record = new Content();
      record.setId(rs.getLong("content_id"));
      record.setUniqueId(rs.getString("content_unique_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setContent(rs.getString("content"));
      record.setDraftContent(rs.getString("draft_content"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      // Guarded like highlight: a query or a not-yet-migrated database may not carry these columns.
      if (DB.hasColumn(rs, "content_format")) {
        record.setContentFormat(rs.getInt("content_format"));
      }
      if (DB.hasColumn(rs, "draft_content_format")) {
        record.setDraftContentFormat(rs.getInt("draft_content_format"));
      }
      if (DB.hasColumn(rs, "draft_status")) {
        record.setDraftStatus(rs.getString("draft_status"));
        record.setSubmittedBy(rs.getLong("submitted_by"));
        record.setApprovedBy(rs.getLong("approved_by"));
        record.setReleaseReference(rs.getString("release_reference"));
      }
      if (DB.hasColumn(rs, "highlight")) {
        record.setHighlight(rs.getString("highlight"));
      }
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
