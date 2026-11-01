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

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Verifies {@link ImageUsageCommand} against a real PostgreSQL instance (issue #498): an image
 * referenced through a plain varchar "image URL" column (e.g. {@code web_pages.page_image_url})
 * must be detected, an image referenced only through a raw {@code <img src="...">} inside an HTML
 * body ({@code content.content}/{@code content.draft_content}) must ALSO be detected -- not just
 * orphaned by omission -- an image referenced only through markdown/HTML pasted into a wiki page
 * body ({@code wiki_pages.body}) must ALSO be detected, an image referenced only via an
 * image-typed {@code site_properties} row (e.g. {@code site.logo.white}) must ALSO be detected
 * (issue #1312), and a genuinely unreferenced image must come back orphaned.
 *
 * @author SimIS Inc.
 */
class ImageUsageCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping ImageUsageCommand integration test");

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
    userId = insertUser("image-owner");
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
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
      statement.execute("TRUNCATE TABLE images RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE web_pages RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE content RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE wiki_pages RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE site_properties RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE stylesheets RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void imageReferencedByAVarcharImageUrlColumnIsDetectedAsUsed() {
    Image image = insertImage("hero-banner.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    insertWebPage("/solutions", fullUrl);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertFalse(usages.isEmpty(), "an image referenced via WebPage.imageUrl must be detected as used");
    assertTrue(usages.stream().anyMatch(u -> "Web Page".equals(u.getSourceType()) && "/solutions".equals(u.getLabel())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyViaARawImgSrcInsideAContentHtmlBodyIsDetectedAsUsed() {
    Image image = insertImage("inline-diagram.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String bodyHtml = "<p>See the diagram below</p><img src=\"" + fullUrl + "\" alt=\"diagram\"><p>more text</p>";
    insertContentBlock("homepage-hero", bodyHtml, null);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertFalse(usages.isEmpty(),
        "an image referenced only via a raw <img src> inside a Content HTML body must be detected as used, "
            + "not orphaned by omission");
    assertTrue(usages.stream().anyMatch(u -> u.getSourceType().startsWith("Content Block") && "homepage-hero".equals(u.getLabel())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyInDraftContentIsAlsoDetectedAsUsed() {
    Image image = insertImage("draft-only.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String draftHtml = "<div><img src=\"" + fullUrl + "\"></div>";
    insertContentBlock("about-page", "<p>published, no image here</p>", draftHtml);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertTrue(usages.stream().anyMatch(u -> "Content Block (draft)".equals(u.getSourceType())));
  }

  @Test
  void imageReferencedOnlyInlineInAPagesXmlIsDetectedAsUsed() {
    // A content/contentSlider widget can carry its markup inline as an <html><![CDATA[...]]></html>
    // preference instead of pointing at a content record, so the only reference lives in page_xml.
    // This is how the pilot's home page hero and Featured Work cards reference their images.
    Image image = insertImage("hero-slide.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String pageXml = "<page><section><column><widget name=\"contentSlider\">"
        + "<html><![CDATA[<img class=\"orbit-image\" src=\"" + fullUrl + "\" alt=\"Hero\">]]></html>"
        + "</widget></column></section></page>";
    insertWebPageXml("/", pageXml, null);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertFalse(usages.isEmpty(),
        "an image referenced only inline in a page's XML must be detected as used -- findUsages also "
            + "gates deletion, so reporting it orphaned allows deleting an image on a live page");
    assertTrue(usages.stream().anyMatch(u -> "Web Page".equals(u.getSourceType()) && "/".equals(u.getLabel())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyInAPagesSectionBackgroundStyleIsDetectedAsUsed() {
    // Not every page_xml reference is an <img>: a section can name an image in a background-image
    // style, which is how the pilot's home page renders its section backdrop.
    Image image = insertImage("section-backdrop.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String pageXml = "<page><section style=\"background-image:url(" + fullUrl + ");background-size:cover;\">"
        + "<column class=\"small-12 cell\"/></section></page>";
    insertWebPageXml("/solutions", pageXml, null);

    assertFalse(ImageUsageCommand.findUsages(image).isEmpty(),
        "an image used only as a section background must be detected as used");
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyInADraftPageXmlIsAlsoDetectedAsUsed() {
    // Mirrors content/draft_content: unpublished work still counts as a reference, otherwise
    // deleting the image silently breaks the draft before anyone publishes it.
    Image image = insertImage("draft-hero.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String draftXml = "<page><section><column><widget name=\"content\">"
        + "<html><![CDATA[<img src=\"" + fullUrl + "\">]]></html></widget></column></section></page>";
    insertWebPageXml("/about-us", "<page><section><column/></section></page>", draftXml);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertTrue(usages.stream().anyMatch(u -> "Web Page (draft)".equals(u.getSourceType())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void anImageInNoPageXmlIsStillReportedOrphaned() {
    // The fix must not make everything look used -- a genuinely unreferenced image stays orphaned.
    Image image = insertImage("genuinely-unused.png");
    String otherUrl = "/assets/img/2026/07/999-other.png";
    insertWebPageXml("/contact-us",
        "<page><section><column><widget name=\"content\">"
            + "<html><![CDATA[<img src=\"" + otherUrl + "\">]]></html></widget></column></section></page>", null);

    assertTrue(ImageUsageCommand.findUsages(image).isEmpty());
    assertTrue(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyViaMarkdownPastedIntoAWikiPageBodyIsDetectedAsUsed() {
    Image image = insertImage("wiki-diagram.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    String markdownBody = "See the diagram below\n\n![architecture](" + fullUrl + ")\n\nmore text";
    insertWikiPage("Architecture Overview", markdownBody);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertFalse(usages.isEmpty(),
        "an image referenced only via markdown pasted into a wiki page body must be detected as used, "
            + "not orphaned by omission");
    assertTrue(usages.stream().anyMatch(u -> "Wiki Page".equals(u.getSourceType()) && "Architecture Overview".equals(u.getLabel())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void imageReferencedOnlyViaAnImageTypedSitePropertyIsDetectedAsUsed() {
    Image image = insertImage("footer-logo.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    insertSiteProperty("site.logo.mixed", "Mixed color logo", fullUrl, "image");

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertFalse(usages.isEmpty(),
        "an image referenced only via an image-typed site_properties row (e.g. the header/footer "
            + "logo) must be detected as used, not orphaned by omission");
    assertTrue(usages.stream().anyMatch(u -> "Site Setting".equals(u.getSourceType()) && "Mixed color logo".equals(u.getLabel())));
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void aSitePropertyUsageWithNoLabelFallsBackToItsPropertyName() {
    Image image = insertImage("og-image.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    insertSiteProperty("site.image", "", fullUrl, "image");

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertTrue(usages.stream().anyMatch(u -> "Site Setting".equals(u.getSourceType()) && "site.image".equals(u.getLabel())),
        "a blank property_label must fall back to the property_name so the usage never renders empty");
  }

  @Test
  void aNonImageTypedSitePropertyContainingTheUrlSubstringIsNotCountedAsUsage() {
    // A text/url-typed property whose value happens to contain the image's URL (e.g. pasted into a
    // free-text field) must not be mistaken for the image actually being wired up as a site image.
    Image image = insertImage("coincidental-match.png");
    String fullUrl = "/assets/img/" + image.getUrl();
    insertSiteProperty("theme.custom.note", "Custom Note", "see " + fullUrl + " for reference", "text");

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertTrue(usages.isEmpty(), "a non-image-typed property must never count as a usage, even on a substring match");
    assertTrue(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void aGenuinelyUnusedImageIsCorrectlyFlaggedOrphaned() {
    Image image = insertImage("never-used.png");
    // A different image and unrelated page/content rows exist, but none reference this one.
    Image otherImage = insertImage("other.png");
    insertWebPage("/about", "/assets/img/" + otherImage.getUrl());
    insertContentBlock("footer", "<p>no images here</p>", null);

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);

    assertTrue(usages.isEmpty(), "an image nothing references must come back with no usages");
    assertTrue(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void aSimilarFilenameOnADifferentImageDoesNotFalselyMatch() {
    // Two images can share a filename (e.g. re-uploaded); getUrl() embeds the id, so a scan for one
    // must not match a reference to the other even though the filename substring is identical.
    Image imageA = insertImage("logo.png");
    Image imageB = insertImage("logo.png");
    insertWebPage("/only-b", "/assets/img/" + imageB.getUrl());

    assertTrue(ImageUsageCommand.findUsages(imageA).isEmpty(), "imageA must not match imageB's reference");
    assertFalse(ImageUsageCommand.findUsages(imageB).isEmpty(), "imageB's own reference must still be found");
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
      statement.execute("DROP TABLE IF EXISTS site_properties CASCADE");
      statement.execute("DROP TABLE IF EXISTS wiki_pages CASCADE");
      statement.execute("DROP TABLE IF EXISTS content CASCADE");
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("DROP TABLE IF EXISTS images CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE images ("
          + "image_id BIGSERIAL PRIMARY KEY, "
          + "filename VARCHAR(255) NOT NULL, "
          + "path VARCHAR(255) NOT NULL, "
          + "created_by BIGINT REFERENCES users(user_id) NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "processed TIMESTAMP(3), "
          + "file_length BIGINT DEFAULT 0, "
          + "file_type VARCHAR(20), "
          + "width INTEGER NOT NULL, "
          + "height INTEGER NOT NULL, "
          + "web_path VARCHAR(50) NOT NULL, "
          + "focal_x NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "file_hash VARCHAR(1024), "
          + "alt_text VARCHAR(255))");
      // A focused subset of web_pages -- link, page_image_url, and both page XML bodies, the only
      // columns this scan reads.
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL, "
          + "page_image_url VARCHAR(255), "
          + "page_xml TEXT, "
          + "draft_page_xml TEXT)");
      // A focused subset of content -- just the unique id + both HTML bodies.
      statement.execute("CREATE TABLE content ("
          + "content_id BIGSERIAL PRIMARY KEY, "
          + "content_unique_id VARCHAR(255) UNIQUE, "
          + "content TEXT, "
          + "draft_content TEXT)");
      // A focused subset of wiki_pages -- just title + body, the only columns this scan reads.
      statement.execute("CREATE TABLE wiki_pages ("
          + "wiki_page_id BIGSERIAL PRIMARY KEY, "
          + "title VARCHAR(255) NOT NULL, "
          + "body TEXT)");
      // A focused subset of site_properties -- just the columns this scan reads/filters on.
      // A stylesheet is either site-wide (web_page_id NULL) or attached to one page.
      statement.execute("CREATE TABLE stylesheets ("
          + "stylesheet_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT, "
          + "css TEXT)");
      statement.execute("CREATE TABLE site_properties ("
          + "property_id BIGSERIAL PRIMARY KEY, "
          + "property_label VARCHAR(50), "
          + "property_name VARCHAR(50) UNIQUE NOT NULL, "
          + "property_value TEXT NOT NULL, "
          + "property_type VARCHAR(100))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }

  private static long insertUser(String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password) VALUES (?, ?, ?) RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test user", se);
    }
  }

  private static Image insertImage(String filename) {
    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath("2026/07/" + filename);
    image.setCreatedBy(userId);
    image.setFileLength(1024);
    image.setFileType("image/png");
    image.setWidth(100);
    image.setHeight(100);
    image.setWebPath("2026/07");
    return ImageRepository.save(image);
  }

  private static void insertWebPage(String link, String pageImageUrl) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO web_pages (link, page_image_url) VALUES (?, ?)")) {
      pst.setString(1, link);
      pst.setString(2, pageImageUrl);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test web page", se);
    }
  }

  private static void insertWebPageXml(String link, String pageXml, String draftPageXml) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO web_pages (link, page_xml, draft_page_xml) VALUES (?, ?, ?)")) {
      pst.setString(1, link);
      pst.setString(2, pageXml);
      pst.setString(3, draftPageXml);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test web page xml", se);
    }
  }

  private static void insertWikiPage(String title, String body) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO wiki_pages (title, body) VALUES (?, ?)")) {
      pst.setString(1, title);
      pst.setString(2, body);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test wiki page", se);
    }
  }

  private static void insertSiteProperty(String name, String label, String value, String type) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO site_properties (property_name, property_label, property_value, property_type) VALUES (?, ?, ?, ?)")) {
      pst.setString(1, name);
      pst.setString(2, label);
      pst.setString(3, value);
      pst.setString(4, type);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test site property", se);
    }
  }

  private static void insertContentBlock(String uniqueId, String content, String draftContent) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO content (content_unique_id, content, draft_content) VALUES (?, ?, ?)")) {
      pst.setString(1, uniqueId);
      pst.setString(2, content);
      pst.setString(3, draftContent);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test content block", se);
    }
  }

  @Test
  void imageReferencedOnlyFromTheSiteStylesheetIsDetectedAsUsed() {
    // A page background set in /admin/css-editor exists nowhere else: not in page_xml, not in a
    // content body, not in a site property. Four such backgrounds on a live site were all badged
    // "Orphaned" -- and isOrphaned() gates deletion, so they were cleared for deletion too.
    Image image = insertImage("careersbg2.jpg");
    insertStylesheet(null, ".careers-hero { background-image: url(/assets/img/" + image.getUrl() + "); }");

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);
    assertFalse(usages.isEmpty(), "an image used only as a stylesheet background must be detected as used");
    assertEquals("Stylesheet", usages.get(0).getSourceType());
    assertEquals("(site-wide)", usages.get(0).getLabel(),
        "the global stylesheet has no page to name, so it must say so rather than show a null");
    assertFalse(ImageUsageCommand.isOrphaned(image));
  }

  @Test
  void aPageScopedStylesheetUsageNamesThePage() {
    Image image = insertImage("aubg.jpg");
    insertWebPage("/about-us", null);
    insertStylesheet(1L, "body { background-image: url(/assets/img/" + image.getUrl() + "); }");

    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);
    assertFalse(usages.isEmpty());
    assertEquals("/about-us", usages.get(0).getLabel());
  }

  @Test
  void aStylesheetReferenceToAFilenameWithSpacesIsDetected() {
    // getUrl() runs the filename through UrlCommand.encodeUri, which is the same spelling the
    // picker writes into the stylesheet -- so a plain substring match is enough, and deliberately
    // no re-encoding happens on the way in (that would double-encode and match nothing).
    Image image = insertImage("Modeling & Simulation Background.png");
    String url = "/assets/img/" + image.getUrl();
    assertTrue(url.contains("%20") && url.contains("%26"), url);
    insertStylesheet(null, ".hero { background-image: url(\"" + url + "\"); }");

    assertFalse(ImageUsageCommand.findUsages(image).isEmpty(),
        "an encoded filename must be detected, since that is how both sides spell it");
  }

  @Test
  void aStylesheetThatDoesNotMentionTheImageIsNotCountedAsUsage() {
    Image image = insertImage("unused-backdrop.png");
    insertStylesheet(null, ".hero { background-image: url(/assets/img/9-9/something-else.png); }");

    assertTrue(ImageUsageCommand.isOrphaned(image),
        "an unrelated stylesheet must not make an image look used");
  }

  private static void insertStylesheet(Long webPageId, String css) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO stylesheets (web_page_id, css) VALUES (?, ?)")) {
      if (webPageId == null) {
        pst.setNull(1, java.sql.Types.BIGINT);
      } else {
        pst.setLong(1, webPageId);
      }
      pst.setString(2, css);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test stylesheet", se);
    }
  }
}
