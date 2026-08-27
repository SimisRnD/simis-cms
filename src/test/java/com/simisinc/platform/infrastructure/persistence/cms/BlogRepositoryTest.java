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
 * Verifies {@link BlogRepository#remove}'s cascading cleanup against a real PostgreSQL instance
 * (issue #633): deleting a blog must remove its posts' blog_post_tags rows, its own
 * lookup_blog_post_tags vocabulary, and the posts, in an order that never violates a foreign key
 * -- none of blog_post_tags.post_id, blog_post_tags.tag_id, or lookup_blog_post_tags.blog_id have
 * ON DELETE CASCADE (confirmed by reading NEW_10010__new_cms.sql).
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class BlogRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping BlogRepository integration test");

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
  void removeDeletesTheBlogItsPostsItsTagVocabularyAndTheirJoinRowsWithoutAForeignKeyViolation() {
    Blog blog = new Blog();
    blog.setUniqueId("news-" + System.nanoTime());
    blog.setName("News");
    blog.setCreatedBy(1);
    blog.setModifiedBy(1);
    blog.setEnabled(true);
    Blog savedBlog = BlogRepository.add(blog);
    assertTrue(savedBlog.getId() > -1);

    long tagId = addTag(savedBlog.getId(), "fiction");

    BlogPost post = new BlogPost();
    post.setBlogId(savedBlog.getId());
    post.setUniqueId("a-post");
    post.setTitle("A Post");
    post.setBody("Some body content");
    post.setCreatedBy(1);
    post.setModifiedBy(1);
    post.setTagIdList(new Long[] { tagId });
    BlogPost savedPost = BlogPostRepository.add(post);
    assertEquals(1, countBlogPostTags());

    assertTrue(BlogRepository.remove(savedBlog));

    assertNull(BlogRepository.findById(savedBlog.getId()));
    assertNull(BlogPostRepository.findById(savedPost.getId()));
    assertNull(BlogTagRepository.findById(tagId));
    assertEquals(0, countBlogPostTags());
  }

  @Test
  void removeDoesNotTouchAnotherBlogsTagsOrPosts() {
    Blog blogA = addBlogViaRepository();
    Blog blogB = addBlogViaRepository();
    long tagOnB = addTag(blogB.getId(), "history");
    BlogPost postOnB = new BlogPost();
    postOnB.setBlogId(blogB.getId());
    postOnB.setUniqueId("post-on-b");
    postOnB.setTitle("Post On B");
    postOnB.setBody("Body");
    postOnB.setCreatedBy(1);
    postOnB.setModifiedBy(1);
    postOnB.setTagIdList(new Long[] { tagOnB });
    BlogPost savedPostOnB = BlogPostRepository.add(postOnB);

    assertTrue(BlogRepository.remove(blogA));

    assertNull(BlogRepository.findById(blogA.getId()));
    assertEquals(blogB.getId(), BlogRepository.findById(blogB.getId()).getId());
    assertEquals(tagOnB, BlogTagRepository.findById(tagOnB).getId());
    assertEquals(savedPostOnB.getId(), BlogPostRepository.findById(savedPostOnB.getId()).getId());
  }

  private static Blog addBlogViaRepository() {
    Blog blog = new Blog();
    blog.setUniqueId("blog-" + System.nanoTime());
    blog.setName("A Blog");
    blog.setCreatedBy(1);
    blog.setModifiedBy(1);
    blog.setEnabled(true);
    return BlogRepository.add(blog);
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
          + "description TEXT, "
          + "feed_title VARCHAR(255), "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "enabled BOOLEAN DEFAULT true, "
          + "mailing_list_id BIGINT)");
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
          // #1420: curated link posts
          + "source_url VARCHAR(512), "
          + "script_embed VARCHAR(512), "
          + "tags_list VARCHAR(255), "
          + "keywords VARCHAR(255), "
          + "body_text TEXT, "
          // Governed publish workflow (issue #407, phase 2) -- BlogPostRepository.add() below
          // writes these columns unconditionally, so this throwaway schema needs them too.
          + "draft_status VARCHAR(20), "
          + "submitted_by BIGINT DEFAULT -1, "
          + "approved_by BIGINT DEFAULT -1, "
          + "release_reference VARCHAR(255), "
          // issue #414/#1237-sibling: mirrors UPGRADE_20260813.1000__locale_content_variants.sql,
          // which made both of these NOT NULL on the real table -- BlogPostRepository.add() now
          // sets translation_group unconditionally, so this schema needs the column too.
          + "locale VARCHAR(35) NOT NULL DEFAULT 'en', "
          + "translation_group VARCHAR(255) NOT NULL, "
          // #1419: per-post syndication opt-out; FeedServlet filters on it
          + "exclude_from_feed BOOLEAN NOT NULL DEFAULT false)");
      statement.execute("CREATE UNIQUE INDEX blog_posts_unique_idx ON blog_posts(blog_id, post_unique_id)");
      statement.execute("CREATE UNIQUE INDEX uq_blog_posts_group_locale ON blog_posts (translation_group, locale)");
      statement.execute("CREATE TABLE blog_post_tags ("
          + "post_tag_id BIGSERIAL PRIMARY KEY, "
          + "post_id BIGINT REFERENCES blog_posts(post_id), "
          + "tag_id BIGINT REFERENCES lookup_blog_post_tags(tag_id))");
      statement.execute("CREATE UNIQUE INDEX blog_post_tags_uidx ON blog_post_tags(post_id, tag_id)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }

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

  private static long countBlogPostTags() {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement("SELECT COUNT(*) FROM blog_post_tags");
        ResultSet rs = pst.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not count blog_post_tags", se);
    }
  }
}
