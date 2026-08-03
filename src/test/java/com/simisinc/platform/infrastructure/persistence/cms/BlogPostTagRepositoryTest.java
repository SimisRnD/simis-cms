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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.BlogPostTag;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link BlogPostTagRepository} against a real PostgreSQL instance (issue #633).
 * Mirrors {@code ItemTagRepositoryTest} (issue #632), which covers the structurally-equivalent
 * Items {@code ItemTagRepository}.
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class BlogPostTagRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping BlogPostTagRepository integration test");

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
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE blog_post_tags, lookup_blog_post_tags, blog_posts, blogs RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void insertBlogPostTagListInsertsARowForEveryTagOnThePost() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");
    long tagC = addTag(blogId, "travel");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(postId);
    blogPost.setTagIdList(new Long[] { tagA, tagB, tagC });

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagList(connection, blogPost);
    }

    List<BlogPostTag> postTagList = BlogPostTagRepository.findAllByPostId(postId);
    assertEquals(3, postTagList.size());
  }

  @Test
  void insertBlogPostTagListDoesNothingWhenTheListIsNull() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(postId);
    blogPost.setTagIdList(null);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagList(connection, blogPost);
    }

    assertEquals(0, BlogPostTagRepository.findAllByPostId(postId).size());
  }

  @Test
  void removeBlogPostTagIdRemovesOnlyThatSpecificTag() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(postId);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagA);
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagB);
      BlogPostTagRepository.removeBlogPostTagId(connection, blogPost, tagA);
    }

    List<BlogPostTag> remaining = BlogPostTagRepository.findAllByPostId(postId);
    assertEquals(1, remaining.size());
    assertEquals(tagB, remaining.get(0).getTagId());
  }

  @Test
  void removeAllByPostRemovesEveryTagOnThatPost() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(postId);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagA);
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagB);
      BlogPostTagRepository.removeAll(connection, blogPost);
    }

    assertTrue(BlogPostTagRepository.findAllByPostId(postId).isEmpty());
  }

  @Test
  void removeAllByTagRemovesThatTagFromEveryPost() throws SQLException {
    long blogId = addBlog();
    long postA = addPost(blogId, "post-a");
    long postB = addPost(blogId, "post-b");
    long tagId = addTag(blogId, "fiction");

    BlogPost blogPostA = new BlogPost();
    blogPostA.setId(postA);
    BlogPost blogPostB = new BlogPost();
    blogPostB.setId(postB);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPostA, tagId);
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPostB, tagId);

      BlogTag tag = new BlogTag();
      tag.setId(tagId);
      BlogPostTagRepository.removeAll(connection, tag);
    }

    assertTrue(BlogPostTagRepository.findAllByPostId(postA).isEmpty());
    assertTrue(BlogPostTagRepository.findAllByPostId(postB).isEmpty());
  }

  @Test
  void removeAllByBlogIdRemovesEveryPostTagInThatBlogButNotOtherBlogs() throws SQLException {
    long blogA = addBlog();
    long blogB = addBlog();
    long postA = addPost(blogA, "post-a");
    long postB = addPost(blogB, "post-b");
    long tagA = addTag(blogA, "fiction");
    long tagB = addTag(blogB, "history");

    BlogPost blogPostA = new BlogPost();
    blogPostA.setId(postA);
    BlogPost blogPostB = new BlogPost();
    blogPostB.setId(postB);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPostA, tagA);
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPostB, tagB);
      BlogPostTagRepository.removeAllByBlogId(connection, blogA);
    }

    assertTrue(BlogPostTagRepository.findAllByPostId(postA).isEmpty());
    assertEquals(1, BlogPostTagRepository.findAllByPostId(postB).size());
  }

  /**
   * Proves the database-level backstop (blog_post_tags_uidx, added by
   * UPGRADE_20260802.1007__blog_post_tags_unique_index.sql) actually exists and rejects a
   * duplicate (post_id, tag_id) pair -- the gap a prior build of issue #633 was flagged for
   * shipping without. The application-level idempotency guarantee (a repeat save with the same
   * tag set inserts nothing new) is proven separately in BlogPostRepositoryTest, since
   * BlogPostRepository's diff-based reconciliation is what normal callers go through; this test
   * exercises the raw repository method to confirm the constraint itself is real.
   */
  @Test
  void insertingTheSamePostTagPairTwiceViolatesTheUniqueConstraint() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagId = addTag(blogId, "fiction");

    BlogPost blogPost = new BlogPost();
    blogPost.setId(postId);

    try (Connection connection = DB.getConnection()) {
      BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagId);
      assertThrows(SQLException.class,
          () -> BlogPostTagRepository.insertBlogPostTagId(connection, blogPost, tagId));
    }
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
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS blog_post_tags CASCADE");
      statement.execute("DROP TABLE IF EXISTS lookup_blog_post_tags CASCADE");
      statement.execute("DROP TABLE IF EXISTS blog_posts CASCADE");
      statement.execute("DROP TABLE IF EXISTS blogs CASCADE");
      statement.execute("CREATE TABLE blogs ("
          + "blog_id BIGSERIAL PRIMARY KEY, "
          + "blog_unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "enabled BOOLEAN DEFAULT true)");
      statement.execute("CREATE TABLE lookup_blog_post_tags ("
          + "tag_id BIGSERIAL PRIMARY KEY, "
          + "blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL, "
          + "tag_unique_id VARCHAR(255) NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE UNIQUE INDEX lookup_bl_po_tag_uidx ON lookup_blog_post_tags (blog_id, tag_unique_id)");
      statement.execute("CREATE TABLE blog_posts ("
          + "post_id BIGSERIAL PRIMARY KEY, "
          + "blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL, "
          + "post_unique_id VARCHAR(255) NOT NULL, "
          + "title VARCHAR(255) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE UNIQUE INDEX blog_posts_unique_idx ON blog_posts(blog_id, post_unique_id)");
      statement.execute("CREATE TABLE blog_post_tags ("
          + "post_tag_id BIGSERIAL PRIMARY KEY, "
          + "post_id BIGINT REFERENCES blog_posts(post_id), "
          + "tag_id BIGINT REFERENCES lookup_blog_post_tags(tag_id))");
      statement.execute("CREATE UNIQUE INDEX blog_post_tags_uidx ON blog_post_tags(post_id, tag_id)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }

  private static long addBlog() {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO blogs (blog_unique_id, name, created_by, modified_by) VALUES (?, ?, 1, 1) RETURNING blog_id")) {
      pst.setString(1, "blog-" + System.nanoTime());
      pst.setString(2, "A Blog");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a blog", se);
    }
  }

  private static long addPost(long blogId, String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO blog_posts (blog_id, post_unique_id, title, created_by, modified_by) "
                + "VALUES (?, ?, ?, 1, 1) RETURNING post_id")) {
      pst.setLong(1, blogId);
      pst.setString(2, uniqueId);
      pst.setString(3, "A Post");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a blog post", se);
    }
  }

  private static long addTag(long blogId, String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO lookup_blog_post_tags (blog_id, tag_unique_id, name, created_by) VALUES (?, ?, ?, 1) RETURNING tag_id")) {
      pst.setLong(1, blogId);
      pst.setString(2, uniqueId);
      pst.setString(3, uniqueId);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a blog tag", se);
    }
  }
}
