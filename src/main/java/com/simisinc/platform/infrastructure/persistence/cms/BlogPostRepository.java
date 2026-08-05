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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.BlogPostTag;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Persists and retrieves blog post objects
 *
 * @author matt rajkowski
 * @created 8/7/18 9:15 AM
 */
public class BlogPostRepository {

  private static Log LOG = LogFactory.getLog(BlogPostRepository.class);

  private static String TABLE_NAME = "blog_posts";
  private static String[] PRIMARY_KEY = new String[] { "post_id" };

  // Package-private (was private) so BlogPostRepositoryWhereClauseTest can inspect the built
  // SqlUtils directly -- mirrors CalendarEventRepository.createWhereStatement (issue #882/PR #911).
  static SqlUtils createWhereStatement(BlogPostSpecification specification) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("post_id = ?", specification.getId(), -1)
          .addIfExists("blog_id = ?", specification.getBlogId(), -1)
          .addIfExists("post_unique_id = ?", specification.getUniqueId());
      if (specification.getPublishedOnly() != DataConstants.UNDEFINED) {
        if (specification.getPublishedOnly() == DataConstants.TRUE) {
          where.add("published IS NOT NULL");
        } else {
          where.add("published IS NULL");
        }
      }
      // issue #427: mirrors CalendarEventRepository's archived filtering shape. UNDEFINED (the
      // default for every pre-#427 caller) includes archived rows, so this is purely additive.
      if (specification.getArchivedOnly() == DataConstants.TRUE) {
        where.add("archived IS NOT NULL");
      } else if (specification.getArchivedOnly() == DataConstants.FALSE) {
        where.add("archived IS NULL");
      }
      if (specification.getStartDateIsBeforeNow() != DataConstants.UNDEFINED) {
        if (specification.getStartDateIsBeforeNow() == DataConstants.TRUE) {
          // Show the ones which are active
          where.add("start_date <= NOW()");
        }
      }
      if (specification.getIsWithinEndDate() != DataConstants.UNDEFINED) {
        if (specification.getIsWithinEndDate() == DataConstants.TRUE) {
          // Show the non-expiring and unexpired
          where.add("(end_date IS NULL OR end_date >= NOW())");
        }
      }
      // issue #996 (editorial calendar "Drafts with no dates" feed): a post with neither
      // scheduling field set has no anchor date, so this replaces the date-range filter below
      // entirely rather than combining with it -- EditorialCalendarAjax never sets both on the
      // same specification.
      if (specification.isUndatedOnly()) {
        where.add("start_date IS NULL AND end_date IS NULL");
      } else if (specification.getStartingDateRange() != null && specification.getEndingDateRange() != null) {
        // issue #426 (editorial calendar): mirrors CalendarEventRepository's identical
        // startingDateRange/endingDateRange OR-across-two-columns shape, applied to start_date/
        // end_date -- BlogPost has no publishAt/expiresAt columns, so start_date/end_date are this
        // entity's real scheduling-equivalent fields (see BlogPostSpecification's field comment).
        where.add("((start_date >= ? AND start_date < ?) OR (end_date >= ? AND end_date < ?))",
            new Timestamp[]{specification.getStartingDateRange(), specification.getEndingDateRange(),
                specification.getStartingDateRange(), specification.getEndingDateRange()});
      } else if (specification.getStartingDateRange() != null) {
        where.add("(start_date >= ? OR end_date >= ?)",
            new Timestamp[]{specification.getStartingDateRange(), specification.getStartingDateRange()});
      } else if (specification.getEndingDateRange() != null) {
        where.add("(start_date < ? OR end_date < ?)",
            new Timestamp[]{specification.getEndingDateRange(), specification.getEndingDateRange()});
      }
      where.addIfExists("created_by = ?", specification.getCreatedBy(), -1);
      if (StringUtils.isNotBlank(specification.getSearchTerm())) {
        where.add("tsv @@ PLAINTO_TSQUERY('content_stem', ?)", specification.getSearchTerm().trim());
      }
    }
    return where;
  }

  private static DataResult query(BlogPostSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = createWhereStatement(specification);
    SqlUtils orderBy = null;
    if (specification != null && StringUtils.isNotBlank(specification.getSearchTerm())) {
      select.add(
          "ts_headline('english', body_text, PLAINTO_TSQUERY('content_stem', ?), 'StartSel=${b}, StopSel=${/b}, MaxWords=30, MinWords=15, ShortWord=3, HighlightAll=FALSE, MaxFragments=2, FragmentDelimiter=\" ... \"') AS highlight",
          specification.getSearchTerm().trim());
      select.add("TS_RANK_CD(tsv, PLAINTO_TSQUERY('content_stem', ?)) AS rank", specification.getSearchTerm().trim());
      // Override the order by for rank first
      orderBy = new SqlUtils();
      orderBy.add("rank DESC, post_id desc");
    }
    return DB.selectAllFrom(TABLE_NAME, select, where, orderBy, constraints, BlogPostRepository::buildRecord);
  }

  public static BlogPost findByUniqueId(Long blogId, String postUniqueId) {
    if (StringUtils.isBlank(postUniqueId)) {
      return null;
    }
    return (BlogPost) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("blog_id = ?", blogId)
            .add("post_unique_id = ?", postUniqueId),
        BlogPostRepository::buildRecord);
  }

  public static BlogPost findById(Long blogPostId) {
    if (blogPostId == -1) {
      return null;
    }
    return (BlogPost) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("post_id = ?", blogPostId),
        BlogPostRepository::buildRecord);
  }

  public static List<BlogPost> findAll() {
    return findAll(null, null);
  }

  public static List<BlogPost> findAll(BlogPostSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("post_id");
    DataResult result = query(specification, constraints);
    return (List<BlogPost>) result.getRecords();
  }

  public static long findCount(BlogPostSpecification specification) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static BlogPost save(BlogPost record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static BlogPost add(BlogPost record) {
    SqlUtils insertValues = new SqlUtils()
        .add("blog_id", record.getBlogId())
        .add("post_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("body_text", HtmlCommand.text(StringUtils.trimToNull(record.getBody())))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("published", record.getPublished())
        .add("archived", record.getArchived())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("draft_status", StringUtils.trimToNull(record.getDraftStatus()))
        .add("submitted_by", record.getSubmittedBy())
        .add("approved_by", record.getApprovedBy())
        .add("release_reference", StringUtils.trimToNull(record.getReleaseReference()));
    // Use a transaction so the post row and its tag assignments (issue #633) commit together
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        if (record.getId() == -1) {
          LOG.error("An id was not set!");
          return null;
        }
        // Manage the tags (issue #633)
        BlogPostTagRepository.insertBlogPostTagList(connection, record);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  public static BlogPost update(BlogPost record) {
    SqlUtils updateValues = new SqlUtils()
        .add("post_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("body_text", HtmlCommand.text(StringUtils.trimToNull(record.getBody())))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("published", record.getPublished())
        .add("archived", record.getArchived())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("draft_status", StringUtils.trimToNull(record.getDraftStatus()))
        .add("submitted_by", record.getSubmittedBy())
        .add("approved_by", record.getApprovedBy())
        .add("release_reference", StringUtils.trimToNull(record.getReleaseReference()));
    SqlUtils where = new SqlUtils()
        .add("post_id = ?", record.getId());

    // Use the previous tag assignments for reconciliation (issue #633), mirroring
    // ItemRepository's diff-based approach so a repeat call with the same tag set is a no-op --
    // it recomputes existingTagList == newTagList and neither inserts nor deletes anything.
    List<BlogPostTag> existingTagList = BlogPostTagRepository.findAllByPostId(record.getId());
    List<Long> newTagList = record.getTagIdList() != null ? Arrays.asList(record.getTagIdList()) : Collections.emptyList();

    // Use a transaction so the post row and its tag assignments commit together
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        if (!DB.update(connection, TABLE_NAME, updateValues, where)) {
          LOG.error("The update failed!");
          return null;
        }

        // Remove tags that are no longer assigned
        if (existingTagList != null) {
          for (BlogPostTag existingTag : existingTagList) {
            if (!newTagList.contains(existingTag.getTagId())) {
              BlogPostTagRepository.removeBlogPostTagId(connection, record, existingTag.getTagId());
            }
          }
        }

        // Add newly-assigned tags
        for (Long newTagId : newTagList) {
          boolean hasTag = false;
          if (existingTagList != null) {
            for (BlogPostTag existingTag : existingTagList) {
              if (existingTag.getTagId() == newTagId) {
                hasTag = true;
                break;
              }
            }
          }
          if (!hasTag) {
            BlogPostTagRepository.insertBlogPostTagId(connection, record, newTagId);
          }
        }

        // Finish the transaction
        transaction.commit();
        //      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static boolean remove(BlogPost record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references (issue #633) -- blog_post_tags.post_id has no ON DELETE CASCADE,
        // so its rows must be removed before the post itself
        BlogPostTagRepository.removeAll(connection, record);
        //        ItemCategoryRepository.removeAll(connection, record);
        //        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
        //        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("post_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Blog blog) throws SQLException {
    // Delete the references (issue #633) -- blog_post_tags.post_id has no ON DELETE CASCADE, so
    // its rows for every post in this blog must be removed before the posts themselves
    BlogPostTagRepository.removeAllByBlogId(connection, blog.getId());
    SqlUtils where = new SqlUtils();
    where.add("blog_id = ?", blog.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  private static BlogPost buildRecord(ResultSet rs) {
    try {
      BlogPost record = new BlogPost();
      record.setId(rs.getLong("post_id"));
      record.setBlogId(rs.getLong("blog_id"));
      record.setUniqueId(rs.getString("post_unique_id"));
      record.setTitle(rs.getString("title"));
      record.setBody(rs.getString("body"));
      record.setSummary(rs.getString("summary"));
      record.setImageUrl(rs.getString("image_url"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setPublished(rs.getTimestamp("published"));
      record.setArchived(rs.getTimestamp("archived"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      record.setKeywords(rs.getString("keywords"));
      // Governed publish workflow (issue #407, phase 2) -- guarded so a throwaway/minimal test
      // schema without these columns still builds a valid record, mirroring
      // WebPageRepository.buildRecord()'s identical guard.
      if (DB.hasColumn(rs, "draft_status")) {
        record.setDraftStatus(rs.getString("draft_status"));
        record.setSubmittedBy(rs.getLong("submitted_by"));
        record.setApprovedBy(rs.getLong("approved_by"));
        record.setReleaseReference(rs.getString("release_reference"));
      }
      // Additional fields
      if (DB.hasColumn(rs, "highlight")) {
        record.setHighlight(rs.getString("highlight"));
      }
      // Populate the assigned tags (issue #633) -- both the display-name array that
      // blog-post-list.jsp/blog-post-details.jsp already render, and the id array used to
      // pre-check the assignment checkboxes in the post editor
      List<BlogTag> tagList = BlogTagRepository.findAllByPostId(record.getId());
      if (tagList != null && !tagList.isEmpty()) {
        List<String> tagNameList = new ArrayList<>();
        List<Long> tagIdList = new ArrayList<>();
        for (BlogTag tag : tagList) {
          tagNameList.add(tag.getName());
          tagIdList.add(tag.getId());
        }
        record.setTagsList(tagNameList.toArray(new String[0]));
        record.setTagIdList(tagIdList.toArray(new Long[0]));
      }
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
