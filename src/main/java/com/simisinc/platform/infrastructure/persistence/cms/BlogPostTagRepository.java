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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.BlogPostTag;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves the blog_post_tags join table (issue #633), which links a blog post to
 * entries in its blog's own tag vocabulary (lookup_blog_post_tags).
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogPostTagRepository {

  private static Log LOG = LogFactory.getLog(BlogPostTagRepository.class);

  private static String TABLE_NAME = "blog_post_tags";
  private static String[] PRIMARY_KEY = new String[] { "post_tag_id" };

  public static void insertBlogPostTagList(Connection connection, BlogPost blogPost) throws SQLException {
    if (blogPost.getTagIdList() == null) {
      return;
    }
    for (Long tagId : blogPost.getTagIdList()) {
      insertBlogPostTagId(connection, blogPost, tagId);
    }
  }

  public static void insertBlogPostTagId(Connection connection, BlogPost blogPost, long tagId) throws SQLException {
    if (blogPost == null) {
      return;
    }
    SqlUtils insertValues = new SqlUtils()
        .add("post_id", blogPost.getId())
        .add("tag_id", tagId);
    DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static void removeBlogPostTagId(Connection connection, BlogPost blogPost, long tagId) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("post_id = ?", blogPost.getId());
    where.add("tag_id = ?", tagId);
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAllByPostId(Connection connection, long postId) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("post_id = ?", postId);
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, BlogPost blogPost) throws SQLException {
    removeAllByPostId(connection, blogPost.getId());
  }

  public static void removeAllByTagId(Connection connection, long tagId) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("tag_id = ?", tagId);
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, BlogTag tag) throws SQLException {
    removeAllByTagId(connection, tag.getId());
  }

  /**
   * Deletes every blog_post_tags row for posts belonging to the given blog. Must run before the
   * blog's posts are deleted (blog_post_tags.post_id references blog_posts(post_id) with no
   * ON DELETE CASCADE), which in turn must run before the blog itself and its tag vocabulary are
   * deleted.
   */
  public static void removeAllByBlogId(Connection connection, long blogId) throws SQLException {
    String SQL_QUERY = "DELETE FROM " + TABLE_NAME
        + " WHERE post_id IN (SELECT post_id FROM blog_posts WHERE blog_id = ?)";
    try (PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      pst.setLong(1, blogId);
      pst.executeUpdate();
    }
  }

  public static List<BlogPostTag> findAllByPostId(long postId) {
    if (postId == -1) {
      return null;
    }
    return (List<BlogPostTag>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("post_id = ?", postId),
        null,
        BlogPostTagRepository::buildRecord).getRecords();
  }

  private static BlogPostTag buildRecord(ResultSet rs) {
    try {
      BlogPostTag record = new BlogPostTag();
      record.setId(rs.getLong("post_tag_id"));
      record.setPostId(rs.getLong("post_id"));
      record.setTagId(rs.getLong("tag_id"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
