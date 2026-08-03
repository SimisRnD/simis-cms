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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
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
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link BlogPostRepository}'s tag round-trip against a real PostgreSQL instance
 * (issue #633): tags were previously never wired to the database at all (a bare
 * getter/setter-only field on {@code BlogPost}), so this exercises add/update/remove through the
 * real blog_post_tags/lookup_blog_post_tags join, including the idempotency property that a
 * repeat save with the same tag set must not create duplicate rows or fail.
 *
 * <p>The blog_posts table here is deliberately narrower than
 * src/main/resources/database/install/NEW_10010__new_cms.sql -- it matches every column
 * {@link BlogPostRepository#add}/{@code update}/{@code buildRecord} actually read or write
 * (verified by reading both), and omits geom/tsv full-text-search columns and their trigger,
 * which those methods never touch and which would otherwise require PostGIS and a custom text
 * search configuration this lighter, non-PostGIS test image doesn't have.</p>
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class BlogPostRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping BlogPostRepository integration test");

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
  void addInsertsTheAssignedTags() {
    long blogId = addBlog();
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");

    BlogPost post = newPost(blogId, "a-post");
    post.setTagIdList(new Long[] { tagA, tagB });

    BlogPost saved = BlogPostRepository.add(post);
    assertNotNull(saved);
    assertTrue(saved.getId() > -1);
    assertEquals(2, countBlogPostTagsForPost(saved.getId()));

    BlogPost reloaded = BlogPostRepository.findById(saved.getId());
    assertEquals(2, reloaded.getTagIdList().length);
    Arrays.sort(reloaded.getTagsList());
    assertArrayEquals(new String[] { "Fiction", "History" }, reloaded.getTagsList());
  }

  @Test
  void updateAddsAndRemovesTagsToMatchTheNewSet() {
    long blogId = addBlog();
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");
    long tagC = addTag(blogId, "travel");

    BlogPost post = newPost(blogId, "a-post");
    post.setTagIdList(new Long[] { tagA, tagB });
    BlogPost saved = BlogPostRepository.add(post);
    assertEquals(2, countBlogPostTagsForPost(saved.getId()));

    // Drop tagA, keep tagB, add tagC
    saved.setTagIdList(new Long[] { tagB, tagC });
    BlogPost updated = BlogPostRepository.update(saved);
    assertNotNull(updated);

    BlogPost reloaded = BlogPostRepository.findById(saved.getId());
    Arrays.sort(reloaded.getTagsList());
    assertArrayEquals(new String[] { "History", "Travel" }, reloaded.getTagsList());
  }

  @Test
  void callingUpdateTwiceWithTheSameTagSetDoesNotDuplicateOrFail() {
    long blogId = addBlog();
    long tagA = addTag(blogId, "fiction");
    long tagB = addTag(blogId, "history");

    BlogPost post = newPost(blogId, "a-post");
    BlogPost saved = BlogPostRepository.add(post);
    assertNotNull(saved);

    saved.setTagIdList(new Long[] { tagA, tagB });
    BlogPost firstUpdate = BlogPostRepository.update(saved);
    assertNotNull(firstUpdate, "the first update must succeed");
    assertEquals(2, countBlogPostTagsForPost(saved.getId()));

    // Call again with the exact same tag set -- must be a no-op, not a duplicate or a failure
    saved.setTagIdList(new Long[] { tagA, tagB });
    BlogPost secondUpdate = BlogPostRepository.update(saved);
    assertNotNull(secondUpdate, "a repeat update with the same tag set must not fail");
    assertEquals(2, countBlogPostTagsForPost(saved.getId()),
        "a repeat update with the same tag set must not create duplicate join rows");

    // And a third time, for good measure
    saved.setTagIdList(new Long[] { tagA, tagB });
    assertNotNull(BlogPostRepository.update(saved));
    assertEquals(2, countBlogPostTagsForPost(saved.getId()));
  }

  @Test
  void removeDeletesThePostAndItsTagReferencesWithoutAForeignKeyViolation() {
    long blogId = addBlog();
    long tagA = addTag(blogId, "fiction");

    BlogPost post = newPost(blogId, "a-post");
    post.setTagIdList(new Long[] { tagA });
    BlogPost saved = BlogPostRepository.add(post);
    assertEquals(1, countBlogPostTagsForPost(saved.getId()));

    assertTrue(BlogPostRepository.remove(saved));

    assertNull(BlogPostRepository.findById(saved.getId()));
    assertEquals(0, countBlogPostTagsForPost(saved.getId()));
    // The tag itself (the blog's vocabulary) is untouched by deleting one post
    assertNotNull(BlogTagRepository.findById(tagA));
  }

  @Test
  void removeAllByBlogDeletesEveryPostsTagReferencesWithoutAForeignKeyViolation() throws SQLException {
    long blogId = addBlog();
    long tagA = addTag(blogId, "fiction");

    BlogPost postA = newPost(blogId, "post-a");
    postA.setTagIdList(new Long[] { tagA });
    BlogPost savedA = BlogPostRepository.add(postA);

    BlogPost postB = newPost(blogId, "post-b");
    postB.setTagIdList(new Long[] { tagA });
    BlogPost savedB = BlogPostRepository.add(postB);

    Blog blogBean = new Blog();
    blogBean.setId(blogId);
    try (Connection connection = DB.getConnection()) {
      // Exercises the same ordering BlogRepository.remove() relies on: join rows must be gone
      // before the posts themselves are deleted, or the post_id foreign key rejects the delete.
      BlogPostRepository.removeAll(connection, blogBean);
    }

    assertNull(BlogPostRepository.findById(savedA.getId()));
    assertNull(BlogPostRepository.findById(savedB.getId()));
    assertEquals(0, countBlogPostTagsForPost(savedA.getId()));
    assertEquals(0, countBlogPostTagsForPost(savedB.getId()));
  }

  private static BlogPost newPost(long blogId, String uniqueId) {
    BlogPost post = new BlogPost();
    post.setBlogId(blogId);
    post.setUniqueId(uniqueId);
    post.setTitle("A Post");
    post.setBody("Some body content");
    post.setCreatedBy(1);
    post.setModifiedBy(1);
    return post;
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
      // Columns match NEW_10010__new_cms.sql's blog_posts for everything BlogPostRepository's
      // add/update/buildRecord touch; geom/tsv (and the tsv trigger, which needs the 'title_stem'
      // text search configuration created elsewhere and PostGIS) are intentionally omitted since
      // neither is read or written by that code.
      statement.execute("CREATE TABLE blog_posts ("
          + "post_id BIGSERIAL PRIMARY KEY, "
          + "blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL, "
          + "post_unique_id VARCHAR(255) NOT NULL, "
          + "title VARCHAR(255) NOT NULL, "
          + "body TEXT, "
          + "summary TEXT, "
          + "created_by BIGINT NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT NOT NULL, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "published TIMESTAMP(3) DEFAULT NULL, "
          + "archived TIMESTAMP(3) DEFAULT NULL, "
          + "start_date TIMESTAMP(3) DEFAULT NULL, "
          + "end_date TIMESTAMP(3) DEFAULT NULL, "
          + "image_url VARCHAR(255), "
          + "video_url VARCHAR(255), "
          + "video_embed VARCHAR(512), "
          + "script_embed VARCHAR(512), "
          + "tags_list VARCHAR(255), "
          + "keywords VARCHAR(255), "
          + "body_text TEXT)");
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

  /** Name is capitalized deterministically from the uniqueId, e.g. "fiction" -> "Fiction". */
  private static long addTag(long blogId, String uniqueId) {
    String name = Character.toUpperCase(uniqueId.charAt(0)) + uniqueId.substring(1);
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

  private static long countBlogPostTagsForPost(long postId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT COUNT(*) FROM blog_post_tags WHERE post_id = ?")) {
      pst.setLong(1, postId);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not count blog_post_tags", se);
    }
  }
}
