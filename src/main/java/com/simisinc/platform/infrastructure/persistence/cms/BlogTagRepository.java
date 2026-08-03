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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves a blog's own tag vocabulary (issue #633). Backed by
 * lookup_blog_post_tags, which is scoped by blog_id -- not to be confused with the Items
 * {@code TagRepository} (issue #632), which is a separate collection-scoped concept.
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogTagRepository {

  private static Log LOG = LogFactory.getLog(BlogTagRepository.class);

  private static String TABLE_NAME = "lookup_blog_post_tags";
  private static String[] PRIMARY_KEY = new String[] { "tag_id" };

  public static BlogTag findById(long id) {
    if (id == -1) {
      return null;
    }
    return (BlogTag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("tag_id = ?", id),
        BlogTagRepository::buildRecord);
  }

  public static BlogTag findByUniqueId(long blogId, String tagUniqueId) {
    if (blogId == -1 || StringUtils.isBlank(tagUniqueId)) {
      return null;
    }
    return (BlogTag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("blog_id = ?", blogId)
            .add("tag_unique_id = ?", tagUniqueId),
        BlogTagRepository::buildRecord);
  }

  public static BlogTag findByNameWithinBlog(String name, long blogId) {
    if (blogId == -1) {
      return null;
    }
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (BlogTag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("blog_id = ?", blogId)
            .add("LOWER(name) = ?", name.trim().toLowerCase()),
        BlogTagRepository::buildRecord);
  }

  public static List<BlogTag> findAllByBlogId(long blogId) {
    if (blogId == -1) {
      return null;
    }
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("blog_id = ?", blogId),
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        BlogTagRepository::buildRecord);
    return (List<BlogTag>) result.getRecords();
  }

  public static List<BlogTag> findAllByPostId(long postId) {
    if (postId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM blog_post_tags WHERE tag_id = lookup_blog_post_tags.tag_id AND post_id = ?)",
            postId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        BlogTagRepository::buildRecord);
    return (List<BlogTag>) result.getRecords();
  }

  public static BlogTag save(BlogTag record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return insert(record);
  }

  public static boolean remove(BlogTag record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        BlogPostTagRepository.removeAllByTagId(connection, record.getId());
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("tag_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAllByBlogId(Connection connection, long blogId) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("blog_id = ?", blogId));
  }

  public static void removeAll(Connection connection, Blog blog) throws SQLException {
    removeAllByBlogId(connection, blog.getId());
  }

  private static BlogTag insert(BlogTag record) {
    SqlUtils insertValues = new SqlUtils()
        .add("blog_id", record.getBlogId())
        .add("tag_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("created_by", record.getCreatedBy());
    try {
      record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
      if (record.getId() == -1) {
        LOG.error("An id was not set!");
        return null;
      }
      return record;
    } catch (Exception e) {
      LOG.error("Exception: " + e.getMessage());
    }
    return null;
  }

  private static BlogTag update(BlogTag record) {
    SqlUtils updateValues = new SqlUtils()
        .add("tag_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()));
    SqlUtils where = new SqlUtils()
        .add("tag_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static BlogTag buildRecord(ResultSet rs) {
    try {
      BlogTag record = new BlogTag();
      record.setId(rs.getLong("tag_id"));
      record.setBlogId(rs.getLong("blog_id"));
      record.setUniqueId(rs.getString("tag_unique_id"));
      record.setName(rs.getString("name"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
