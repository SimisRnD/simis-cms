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

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link BlogTagRepository} against a real PostgreSQL instance (issue #633), since the
 * unique-per-blog lookup and blog_post_tags cascade-on-remove behavior aren't meaningful to
 * exercise with a mock. Mirrors {@code TagRepositoryTest} (issue #632), which covers the
 * structurally-equivalent Items {@code TagRepository}.
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class BlogTagRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping BlogTagRepository integration test");

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
  void saveInsertsANewTagAndFindByIdReturnsIt() {
    long blogId = addBlog();
    BlogTag tagBean = new BlogTag();
    tagBean.setBlogId(blogId);
    tagBean.setUniqueId("fiction");
    tagBean.setName("Fiction");
    tagBean.setCreatedBy(1);

    BlogTag saved = BlogTagRepository.save(tagBean);
    assertTrue(saved.getId() > -1);

    BlogTag found = BlogTagRepository.findById(saved.getId());
    assertEquals("Fiction", found.getName());
    assertEquals("fiction", found.getUniqueId());
    assertEquals(blogId, found.getBlogId());
  }

  @Test
  void findByUniqueIdIsScopedToTheBlog() {
    long blogA = addBlog();
    long blogB = addBlog();
    addTag(blogA, "fiction", "Fiction");

    assertEquals("Fiction", BlogTagRepository.findByUniqueId(blogA, "fiction").getName());
    assertNull(BlogTagRepository.findByUniqueId(blogB, "fiction"));
  }

  @Test
  void findByNameWithinBlogIsCaseInsensitiveAndScopedToTheBlog() {
    long blogA = addBlog();
    long blogB = addBlog();
    addTag(blogA, "fiction", "Fiction");

    assertEquals("Fiction", BlogTagRepository.findByNameWithinBlog("fiction", blogA).getName());
    assertEquals("Fiction", BlogTagRepository.findByNameWithinBlog("FICTION", blogA).getName());
    assertNull(BlogTagRepository.findByNameWithinBlog("Fiction", blogB));
    assertNull(BlogTagRepository.findByNameWithinBlog("Nonfiction", blogA));
  }

  @Test
  void findAllByBlogIdReturnsOnlyThatBlogsTagsSortedByName() {
    long blogA = addBlog();
    long blogB = addBlog();
    addTag(blogA, "zebra", "Zebra");
    addTag(blogA, "apple", "Apple");
    addTag(blogB, "other", "Other");

    List<BlogTag> tagList = BlogTagRepository.findAllByBlogId(blogA);
    assertEquals(2, tagList.size());
    assertEquals("Apple", tagList.get(0).getName());
    assertEquals("Zebra", tagList.get(1).getName());
  }

  @Test
  void findAllByPostIdReturnsOnlyTheAssignedTags() {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagA = addTag(blogId, "fiction", "Fiction");
    long tagB = addTag(blogId, "history", "History");
    addTag(blogId, "unused", "Unused");
    linkPostTag(postId, tagA);
    linkPostTag(postId, tagB);

    List<BlogTag> tagList = BlogTagRepository.findAllByPostId(postId);
    assertEquals(2, tagList.size());
    assertEquals("Fiction", tagList.get(0).getName());
    assertEquals("History", tagList.get(1).getName());
  }

  @Test
  void saveUpdatesAnExistingTagsNameAndUniqueId() {
    long blogId = addBlog();
    long tagId = addTag(blogId, "fiction", "Fiction");

    BlogTag tagBean = BlogTagRepository.findById(tagId);
    tagBean.setName("Non-Fiction");
    tagBean.setUniqueId("non-fiction");
    BlogTag updated = BlogTagRepository.save(tagBean);
    assertEquals("Non-Fiction", updated.getName());

    BlogTag reloaded = BlogTagRepository.findById(tagId);
    assertEquals("Non-Fiction", reloaded.getName());
    assertEquals("non-fiction", reloaded.getUniqueId());
  }

  @Test
  void removeDeletesTheTagAndItsBlogPostTagReferences() throws SQLException {
    long blogId = addBlog();
    long postId = addPost(blogId, "a-post");
    long tagId = addTag(blogId, "fiction", "Fiction");
    linkPostTag(postId, tagId);

    BlogTag tagBean = BlogTagRepository.findById(tagId);
    assertTrue(BlogTagRepository.remove(tagBean));

    assertNull(BlogTagRepository.findById(tagId));
    assertEquals(0, countBlogPostTagsForTag(tagId));
  }

  @Test
  void removeAllByBlogIdDeletesAllOfThatBlogsTags() throws SQLException {
    long blogId = addBlog();
    addTag(blogId, "fiction", "Fiction");
    addTag(blogId, "history", "History");

    Blog blogBean = new Blog();
    blogBean.setId(blogId);
    try (Connection connection = DB.getConnection()) {
      BlogTagRepository.removeAll(connection, blogBean);
    }

    assertEquals(0, BlogTagRepository.findAllByBlogId(blogId).size());
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

  /**
   * Column names/types for blogs, lookup_blog_post_tags, blog_posts and blog_post_tags mirror
   * src/main/resources/database/install/NEW_10010__new_cms.sql exactly for every column these
   * repositories read or write, including the blog_post_tags_uidx unique index added by
   * UPGRADE_20260802.1010__blog_post_tags_unique_index.sql (issue #633). Columns unrelated to tag
   * persistence (geom/tsv full-text search, location fields, etc.) are omitted, matching the
   * simplification TagRepositoryTest/ItemTagRepositoryTest already apply to collections/items.
   */
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
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          // Governed publish workflow (issue #407, phase 2) -- added for parity with the other
          // throwaway blog_posts schemas, even though this test doesn't exercise
          // BlogPostRepository's add/update directly.
          + "draft_status VARCHAR(20), "
          + "submitted_by BIGINT DEFAULT -1, "
          + "approved_by BIGINT DEFAULT -1, "
          + "release_reference VARCHAR(255), "
          // #1419: per-post syndication opt-out; FeedServlet filters on it
          + "exclude_from_feed BOOLEAN NOT NULL DEFAULT false)");
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

  private static long addTag(long blogId, String uniqueId, String name) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO lookup_blog_post_tags (blog_id, tag_unique_id, name, created_by) VALUES (?, ?, ?, 1) RETURNING tag_id")) {
      pst.setLong(1, blogId);
      pst.setString(2, uniqueId);
      pst.setString(3, name);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a blog tag", se);
    }
  }

  private static void linkPostTag(long postId, long tagId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO blog_post_tags (post_id, tag_id) VALUES (?, ?)")) {
      pst.setLong(1, postId);
      pst.setLong(2, tagId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link a blog post tag", se);
    }
  }

  private static long countBlogPostTagsForTag(long tagId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT COUNT(*) FROM blog_post_tags WHERE tag_id = ?")) {
      pst.setLong(1, tagId);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }
}
