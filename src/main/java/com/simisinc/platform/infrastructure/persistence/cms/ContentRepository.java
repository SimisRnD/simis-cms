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

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
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
        select.add("ts_headline('english', content_text, PLAINTO_TSQUERY('content_stem', ?), 'StartSel=${b}, StopSel=${/b}, MaxWords=30, MinWords=15, ShortWord=3, HighlightAll=FALSE, MaxFragments=2, FragmentDelimiter=\" ... \"') AS highlight", specification.getSearchTerm().trim());
        select.add("TS_RANK_CD(tsv, PLAINTO_TSQUERY('content_stem', ?)) AS rank", specification.getSearchTerm().trim());
        where.add("tsv @@ PLAINTO_TSQUERY('content_stem', ?)", specification.getSearchTerm().trim());
        // Override the order by for rank first
        orderBy = new SqlUtils();
        orderBy.add("rank DESC, content_id");
      }
    }
    return DB.selectAllFrom(TABLE_NAME, select, where, orderBy, constraints, ContentRepository::buildRecord);
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

  public static void publish(Content record) {
    if (StringUtils.isBlank(record.getUniqueId())) {
      return;
    }
    // Handle publishing and making sure there is content to publish
    SqlUtils updateValues = new SqlUtils();
    updateValues.add("content = draft_content");
    updateValues.add("draft_content = null");
    // Promote the draft's format stamp with its content, then clear it alongside the emptied draft.
    updateValues.add("content_format = draft_content_format");
    updateValues.add("draft_content_format = 0");
    // The draft is consumed, so clear its review workflow. The durable record of who submitted and
    // approved, and under what release authority, lives in the append-only audit trail, not here.
    updateValues.add("draft_status = null");
    updateValues.add("submitted_by = -1");
    updateValues.add("approved_by = -1");
    updateValues.add("release_reference = null");
    updateValues.add("content_text", HtmlCommand.text(StringUtils.trimToNull(record.getContent())));
    SqlUtils where = new SqlUtils().add("draft_content IS NOT NULL AND content_unique_id = ?", record.getUniqueId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
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
