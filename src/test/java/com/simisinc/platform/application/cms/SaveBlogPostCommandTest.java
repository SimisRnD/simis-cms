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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;

/**
 * Verifies {@link SaveBlogPostCommand#saveBlogPost}, in particular that it actually persists the
 * governed publish workflow fields (issue #407 phase 2: draftStatus/submittedBy/approvedBy/
 * releaseReference) from the bean it is given.
 *
 * <p>This command previously loaded a fresh copy of the existing record from the repository and
 * copied over only an explicit allow-list of business fields, silently dropping those four --
 * which made {@code BlogEditorWidget}'s reset-on-unpublish (added for #407 phase 2, to close the
 * unpublish -> edit -> republish review bypass) inert: the widget computed the correct reset
 * values on its bean, but they never reached the persisted record.</p>
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class SaveBlogPostCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SaveBlogPostCommand integration test");

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
      statement.execute("TRUNCATE TABLE blog_posts, blogs RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void updateCopiesTheGovernedWorkflowFieldsFromTheBeanOntoThePersistedRecord() throws Exception {
    long blogId = addBlog();

    // Seed a post that already went through submit -> approve -> publish, as if a prior editing
    // session had legitimately done so
    BlogPost existing = new BlogPost();
    existing.setBlogId(blogId);
    existing.setUniqueId("a-post");
    existing.setTitle("Original Title");
    existing.setBody("Original body");
    existing.setCreatedBy(1);
    existing.setModifiedBy(1);
    existing.setPublished(new Timestamp(System.currentTimeMillis()));
    existing.setDraftStatus("SUBMITTED");
    existing.setSubmittedBy(3L);
    existing.setApprovedBy(7L);
    existing.setReleaseReference("CR-1234");
    BlogPost saved = BlogPostRepository.add(existing);
    assertNotNull(saved);

    // This mirrors what BlogEditorWidget.post() builds after unpublishing an already-approved
    // post (issue #407 phase 2's fix): same id, published cleared, and the four governance
    // fields reset back to their "never submitted" defaults
    BlogPost unpublishBean = new BlogPost();
    unpublishBean.setId(saved.getId());
    unpublishBean.setBlogId(blogId);
    unpublishBean.setUniqueId("a-post");
    unpublishBean.setTitle("Original Title, edited");
    unpublishBean.setBody("New body, never reviewed");
    unpublishBean.setCreatedBy(1);
    unpublishBean.setModifiedBy(1);
    unpublishBean.setPublished(null);
    unpublishBean.setDraftStatus(null);
    unpublishBean.setSubmittedBy(-1);
    unpublishBean.setApprovedBy(-1);
    unpublishBean.setReleaseReference(null);

    BlogPost result = SaveBlogPostCommand.saveBlogPost(unpublishBean);
    assertNotNull(result);

    BlogPost reloaded = BlogPostRepository.findById(saved.getId());
    assertNotNull(reloaded);
    assertNull(reloaded.getPublished(), "the post must actually be unpublished");
    assertNull(reloaded.getDraftStatus(),
        "draftStatus must be reset on the persisted record, not just on the in-memory bean");
    assertEquals(-1L, reloaded.getSubmittedBy());
    assertEquals(-1L, reloaded.getApprovedBy());
    assertNull(reloaded.getReleaseReference());
  }

  @Test
  void newPostPersistsTheSourceUrlAndFeedOptOutFromTheBean() throws Exception {
    // Both fields arrive on the bean from the editor form -- sourceUrl is even validated by this
    // command -- but neither was copied onto the record being saved, so a new post stored the
    // BlogPost defaults instead. On the live site that meant every curated link post was written
    // with source_url NULL, and the feed fell back to linking the post's own page.
    long blogId = addBlog();

    BlogPost bean = new BlogPost();
    bean.setBlogId(blogId);
    bean.setUniqueId("a-curated-post");
    bean.setTitle("A Curated Post");
    bean.setBody("Commentary on someone else's article");
    bean.setCreatedBy(1);
    bean.setModifiedBy(1);
    bean.setSourceUrl("https://example.org/the-original-article");
    bean.setExcludeFromFeed(true);

    BlogPost result = SaveBlogPostCommand.saveBlogPost(bean);
    assertNotNull(result);

    BlogPost reloaded = BlogPostRepository.findById(result.getId());
    assertNotNull(reloaded);
    assertEquals("https://example.org/the-original-article", reloaded.getSourceUrl(),
        "the source article link must reach the database, not just the in-memory bean");
    assertTrue(reloaded.getExcludeFromFeed(),
        "the feed opt-out must reach the database too");
  }

  @Test
  void newPostPersistsTheShareImageFromTheBean() throws Exception {
    // The share card (#1974) reaches the bean the same way sourceUrl does -- BeanUtils.populate
    // maps the editor's shareImageUrl field onto it -- so it fails the same way if the save does
    // not copy it onto the record: the editor keeps the value on screen, the save reports success,
    // and the column stays NULL. That is exactly what #1957 fixed for sourceUrl, and it is why this
    // test reloads from the database rather than asserting on the returned object.
    long blogId = addBlog();

    BlogPost bean = new BlogPost();
    bean.setBlogId(blogId);
    bean.setUniqueId("a-post-with-a-share-card");
    bean.setTitle("A Post With A Share Card");
    bean.setBody("Body copy");
    bean.setCreatedBy(1);
    bean.setModifiedBy(1);
    bean.setImageUrl("/assets/img/1/banner.jpg");
    bean.setShareImageUrl("/assets/img/2/share-card-1200x630.png");

    BlogPost result = SaveBlogPostCommand.saveBlogPost(bean);
    assertNotNull(result);

    BlogPost reloaded = BlogPostRepository.findById(result.getId());
    assertNotNull(reloaded);
    assertEquals("/assets/img/2/share-card-1200x630.png", reloaded.getShareImageUrl(),
        "the share image must reach the database, not just the in-memory bean");
    assertEquals("/assets/img/1/banner.jpg", reloaded.getImageUrl(),
        "setting a share image must not disturb the post's own banner");
    assertEquals("/assets/img/2/share-card-1200x630.png", reloaded.getShareImageUrlOrDefault(),
        "with a share image set, that is what og:image and the list views resolve to");
  }

  @Test
  void aPostWithoutAShareImageFallsBackToItsBanner() throws Exception {
    // The property the whole feature rests on: every consumer calls getShareImageUrlOrDefault, so a
    // post that predates the column -- which is all of them -- keeps rendering exactly as it did.
    long blogId = addBlog();

    BlogPost bean = new BlogPost();
    bean.setBlogId(blogId);
    bean.setUniqueId("a-post-without-a-share-card");
    bean.setTitle("A Post Without A Share Card");
    bean.setBody("Body copy");
    bean.setCreatedBy(1);
    bean.setModifiedBy(1);
    bean.setImageUrl("/assets/img/1/banner.jpg");

    BlogPost reloaded = BlogPostRepository.findById(SaveBlogPostCommand.saveBlogPost(bean).getId());
    assertNotNull(reloaded);
    assertNull(reloaded.getShareImageUrl(), "no share image was set, so the column stays null");
    assertEquals("/assets/img/1/banner.jpg", reloaded.getShareImageUrlOrDefault(),
        "the banner is what og:image and the list views must fall back to");
  }

  @Test
  void editingAPostCanSetAndThenClearItsShareImage() throws Exception {
    // The direction a load-then-copy save gets wrong most quietly: the record is re-read from the
    // database before the copy, so an uncopied field keeps its stored value and CLEARING it appears
    // to do nothing. Blank must round-trip back to the banner.
    long blogId = addBlog();

    BlogPost bean = new BlogPost();
    bean.setBlogId(blogId);
    bean.setUniqueId("a-post-to-edit");
    bean.setTitle("A Post To Edit");
    bean.setBody("Body copy");
    bean.setCreatedBy(1);
    bean.setModifiedBy(1);
    bean.setImageUrl("/assets/img/1/banner.jpg");
    bean.setShareImageUrl("/assets/img/2/share-card-1200x630.png");
    long postId = SaveBlogPostCommand.saveBlogPost(bean).getId();

    BlogPost edit = new BlogPost();
    edit.setId(postId);
    edit.setBlogId(blogId);
    edit.setUniqueId("a-post-to-edit");
    edit.setTitle("A Post To Edit");
    edit.setBody("Body copy");
    edit.setCreatedBy(1);
    edit.setModifiedBy(1);
    edit.setImageUrl("/assets/img/1/banner.jpg");
    edit.setShareImageUrl("");

    SaveBlogPostCommand.saveBlogPost(edit);

    BlogPost reloaded = BlogPostRepository.findById(postId);
    assertNotNull(reloaded);
    assertNull(reloaded.getShareImageUrl(),
        "clearing the share image must reach the database, not be masked by the reloaded record");
    assertEquals("/assets/img/1/banner.jpg", reloaded.getShareImageUrlOrDefault(),
        "and the post falls back to its banner again");
  }

  @Test
  void editingAPostCanPutItBackIntoTheFeedAndChangeItsSourceUrl() throws Exception {
    // The other direction, and the one a load-then-copy save gets wrong most quietly: because the
    // record is re-read from the database before the copy, an uncopied field keeps its stored value
    // however the form was filled in. An excluded post could never be un-excluded, and a wrong
    // source link could never be corrected.
    long blogId = addBlog();

    BlogPost existing = new BlogPost();
    existing.setBlogId(blogId);
    existing.setUniqueId("an-excluded-post");
    existing.setTitle("Excluded Post");
    existing.setBody("Body");
    existing.setCreatedBy(1);
    existing.setModifiedBy(1);
    existing.setSourceUrl("https://example.org/the-wrong-article");
    existing.setExcludeFromFeed(true);
    BlogPost saved = BlogPostRepository.add(existing);
    assertNotNull(saved);

    BlogPost editBean = new BlogPost();
    editBean.setId(saved.getId());
    editBean.setBlogId(blogId);
    editBean.setUniqueId("an-excluded-post");
    editBean.setTitle("Excluded Post");
    editBean.setBody("Body");
    editBean.setCreatedBy(1);
    editBean.setModifiedBy(1);
    editBean.setSourceUrl("https://example.org/the-right-article");
    editBean.setExcludeFromFeed(false);

    assertNotNull(SaveBlogPostCommand.saveBlogPost(editBean));

    BlogPost reloaded = BlogPostRepository.findById(saved.getId());
    assertNotNull(reloaded);
    assertEquals("https://example.org/the-right-article", reloaded.getSourceUrl(),
        "a corrected source link must overwrite the stored one");
    assertFalse(reloaded.getExcludeFromFeed(),
        "unticking the opt-out must put the post back into the feed");
  }

  @Test
  void updatePersistsNewlySubmittedApprovalStateOntoTheRecord() throws Exception {
    long blogId = addBlog();

    BlogPost existing = new BlogPost();
    existing.setBlogId(blogId);
    existing.setUniqueId("a-post");
    existing.setTitle("Title");
    existing.setBody("Body");
    existing.setCreatedBy(1);
    existing.setModifiedBy(1);
    BlogPost saved = BlogPostRepository.add(existing);

    // Mirrors BlogPostReviewWidget's approve action: draftStatus/approvedBy/releaseReference are
    // now meant to be persisted from the bean, not silently dropped
    BlogPost approveBean = new BlogPost();
    approveBean.setId(saved.getId());
    approveBean.setBlogId(blogId);
    approveBean.setUniqueId("a-post");
    approveBean.setTitle("Title");
    approveBean.setBody("Body");
    approveBean.setCreatedBy(1);
    approveBean.setModifiedBy(1);
    approveBean.setDraftStatus("APPROVED");
    approveBean.setSubmittedBy(3L);
    approveBean.setApprovedBy(9L);
    approveBean.setReleaseReference("CR-5678");

    SaveBlogPostCommand.saveBlogPost(approveBean);

    BlogPost reloaded = BlogPostRepository.findById(saved.getId());
    assertEquals("APPROVED", reloaded.getDraftStatus());
    assertEquals(3L, reloaded.getSubmittedBy());
    assertEquals(9L, reloaded.getApprovedBy());
    assertEquals("CR-5678", reloaded.getReleaseReference());
  }

  @Test
  void editingAnExistingPostDoesNotChangeItsOriginalCreatedBy() throws Exception {
    // createdBy must be set once, at creation -- editing an existing post (e.g. fixing a typo)
    // must not reassign the original author to whoever happens to be editing it today.
    long blogId = addBlog();

    BlogPost existing = new BlogPost();
    existing.setBlogId(blogId);
    existing.setUniqueId("a-post");
    existing.setTitle("Original Title");
    existing.setBody("Original body");
    existing.setCreatedBy(7); // the original author
    existing.setModifiedBy(7);
    BlogPost saved = BlogPostRepository.add(existing);
    assertNotNull(saved);

    // Mirrors what BlogEditorWidget.post() builds when a different admin edits the post
    BlogPost editBean = new BlogPost();
    editBean.setId(saved.getId());
    editBean.setBlogId(blogId);
    editBean.setUniqueId("a-post");
    editBean.setTitle("Original Title, edited");
    editBean.setBody("Original body, edited");
    editBean.setCreatedBy(42); // a different user editing it today
    editBean.setModifiedBy(42);

    // Asserted on the command's own return value, not a reload from BlogPostRepository.findById():
    // BlogPostRepository.update()'s SQL never includes created_by in the first place (it is
    // deliberately excluded from that method's SqlUtils, so a DB round-trip can never distinguish
    // the buggy and fixed command). What this test actually guards is SaveBlogPostCommand's own
    // in-memory object: with the bug, the object it hands to BlogPostRepository.save() -- and
    // therefore the object callers/widgets receive back and act on -- carries the editor's id
    // instead of the original author's, even though the update() SQL happens to make that
    // particular mistake harmless against the persisted row today.
    BlogPost result = SaveBlogPostCommand.saveBlogPost(editBean);
    assertNotNull(result);
    assertEquals(7L, result.getCreatedBy(), "createdBy must survive an edit by a different user");
    assertEquals(42L, result.getModifiedBy(), "modifiedBy must still reflect the editor");
  }

  @Test
  void newPostGetsCreatedByFromTheSubmitter() throws Exception {
    long blogId = addBlog();

    BlogPost bean = new BlogPost();
    bean.setBlogId(blogId);
    bean.setUniqueId("a-new-post");
    bean.setTitle("A New Post");
    bean.setBody("Body");
    bean.setCreatedBy(42);
    bean.setModifiedBy(42);

    BlogPost result = SaveBlogPostCommand.saveBlogPost(bean);
    assertNotNull(result);

    BlogPost reloaded = BlogPostRepository.findById(result.getId());
    assertNotNull(reloaded);
    assertEquals(42L, reloaded.getCreatedBy());
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
      // Columns match NEW_10010__new_cms.sql's blog_posts for everything
      // SaveBlogPostCommand/BlogPostRepository's add/update/buildRecord touch; geom/tsv (and the
      // tsv trigger, which needs the 'title_stem' text search configuration created elsewhere and
      // PostGIS) are intentionally omitted since neither is read or written by this test.
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
          + "share_image_url VARCHAR(255), "
          + "video_url VARCHAR(255), "
          + "video_embed VARCHAR(512), "
          // #1420: curated link posts
          + "source_url VARCHAR(512), "
          + "script_embed VARCHAR(512), "
          + "tags_list VARCHAR(255), "
          + "keywords VARCHAR(255), "
          + "body_text TEXT, "
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
      // BlogPostRepository.findById() also loads tags; these are otherwise-unused by this test
      // but avoid it logging "relation does not exist" noise for every reload.
      statement.execute("CREATE TABLE lookup_blog_post_tags ("
          + "tag_id BIGSERIAL PRIMARY KEY, "
          + "blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL, "
          + "tag_unique_id VARCHAR(255) NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE TABLE blog_post_tags ("
          + "post_tag_id BIGSERIAL PRIMARY KEY, "
          + "post_id BIGINT REFERENCES blog_posts(post_id), "
          + "tag_id BIGINT REFERENCES lookup_blog_post_tags(tag_id))");
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
}
