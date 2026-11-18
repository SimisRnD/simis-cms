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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jobrunr.scheduling.BackgroundJobRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Runs {@link DownscaleLibraryImagesCommand#apply} against a real PostgreSQL instance and a real
 * file, so the SQL that finds and rewrites references is exercised, not just the string rules.
 * Skipped when Docker is not available, like the other database tests.
 *
 * @author SimIS Inc.
 */
class DownscaleLibraryImagesApplyTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping DownscaleLibraryImages integration test");
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
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", "jdbc:postgresql://" + postgres.getHost() + ":"
        + postgres.getMappedPort(POSTGRES_PORT) + "/" + DB_NAME);
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
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void resetTables() throws SQLException {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE images, web_pages, content, blog_posts RESTART IDENTITY");
    }
  }

  @Test
  void applyAddsAResizedCopyAndSwitchesEveryRewritableReference(@TempDir Path root) throws Exception {
    Image original = insertImageWithRealFile(root, "HTT Infographic.png", 3700, 2000);
    byte[] originalBytes = Files.readAllBytes(root.resolve(original.getFileServerPath()));
    String prefix = "/assets/img/" + original.getWebPath() + "-" + original.getId() + "/";
    String encodedUrl = prefix + "HTT%20Infographic.png";
    String literalUrl = prefix + "HTT Infographic.png";

    long pageId = 1L;
    insert("INSERT INTO web_pages (link, page_image_url, page_xml, draft_page_xml) VALUES (?, ?, ?, ?)",
        "/htt-human-type-targets", encodedUrl,
        "<page><widget name=\"image\"><image>" + literalUrl + "</image></widget>"
            + "<widget name=\"content\"><html><![CDATA[<img src=\"" + encodedUrl + "\">]]></html></widget></page>",
        "<page><widget name=\"image\"><image>" + literalUrl + "</image></widget></page>");
    insert("INSERT INTO content (content_unique_id, content, draft_content) VALUES (?, ?, ?)",
        "htt-intro", "<p><img src=\"" + encodedUrl + "?variant=large\"></p>", null);
    insert("INSERT INTO blog_posts (title, body, image_url) VALUES (?, ?, ?)",
        "Human Type Targets ship", "<img src=\"" + encodedUrl + "\">", encodedUrl);

    String outcome;
    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, Answers.CALLS_REAL_METHODS);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<BackgroundJobRequest> jobs = mockStatic(BackgroundJobRequest.class);
        MockedStatic<CacheManager> cache = mockStatic(CacheManager.class);
        MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class)) {
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      DownscaleLibraryImagesCommand.Candidate candidate = DownscaleLibraryImagesCommand.describe(original);
      assertEquals(4, candidate.rewritable().size(), "page XML, its draft, the content block and the post body");
      assertTrue(candidate.elsewhere().stream().anyMatch(u -> "page_image_url".equals(u.getColumn())));
      assertTrue(candidate.elsewhere().stream().anyMatch(u -> "image_url".equals(u.getColumn())));

      outcome = DownscaleLibraryImagesCommand.apply(original.getId(), userId);

      pages.verify(() -> WebPageRepository.invalidateCachedPage("/htt-human-type-targets"), org.mockito.Mockito.atLeastOnce());
    }

    Image copy = ImageRepository.findById(2L);
    assertNotNull(copy, "the copy is a new library entry: " + outcome);
    assertEquals("image/jpeg", copy.getFileType());
    assertEquals(1600, copy.getWidth());
    assertEquals("HTT Infographic.jpg", copy.getFilename());
    assertEquals("Human Type Targets infographic", copy.getAltText(), "pages switched to the copy keep their alt text");
    String newUrl = "/assets/img/" + copy.getWebPath() + "-" + copy.getId() + "/HTT%20Infographic.jpg";

    String pageXml = select("SELECT page_xml FROM web_pages WHERE web_page_id = ?", pageId);
    assertFalse(pageXml.contains(prefix), "both the %20 and the literal-space reference are switched: " + pageXml);
    assertTrue(pageXml.contains("<image>" + newUrl + "</image>"));
    assertTrue(pageXml.contains("<img src=\"" + newUrl + "\">"));
    assertFalse(select("SELECT draft_page_xml FROM web_pages WHERE web_page_id = ?", pageId).contains(prefix));
    assertEquals("<p><img src=\"" + newUrl + "?variant=large\"></p>", select("SELECT content FROM content WHERE content_id = ?", 1L));
    assertEquals("<img src=\"" + newUrl + "\">", select("SELECT body FROM blog_posts WHERE post_id = ?", 1L));

    // Everything outside the rewritten columns, and the original itself, is untouched
    assertEquals(encodedUrl, select("SELECT page_image_url FROM web_pages WHERE web_page_id = ?", pageId));
    assertEquals(encodedUrl, select("SELECT image_url FROM blog_posts WHERE post_id = ?", 1L));
    Image originalAfter = ImageRepository.findById(original.getId());
    assertEquals(original.getFileServerPath(), originalAfter.getFileServerPath());
    assertEquals(original.getFileLength(), originalAfter.getFileLength());
    assertArrayEquals(originalBytes, Files.readAllBytes(root.resolve(original.getFileServerPath())));

    // A second run finds nothing left to switch and adds no second copy
    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, Answers.CALLS_REAL_METHODS)) {
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
      DownscaleLibraryImagesCommand.apply(original.getId(), userId);
    }
    assertEquals(null, ImageRepository.findById(3L));
  }

  private static Image insertImageWithRealFile(Path root, String filename, int width, int height) throws Exception {
    BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = pixels.createGraphics();
    Random random = new Random(1197);
    for (int i = 0; i < 300; i++) {
      g.setColor(new Color(random.nextInt(0xFFFFFF)));
      g.fillRect(random.nextInt(width), random.nextInt(height), 300, 150);
    }
    g.dispose();
    // A band of photographic noise, which PNG cannot compress, puts the file over the 300 KB floor
    for (int y = 0; y < 100; y++) {
      for (int x = 0; x < width; x++) {
        pixels.setRGB(x, y, random.nextInt(0xFFFFFF));
      }
    }
    String relativePath = "images/2025/03/14/original.png";
    File file = root.resolve(relativePath).toFile();
    file.getParentFile().mkdirs();
    ImageIO.write(pixels, "png", file);
    // Make sure it is over the 300 KB floor, as the oversized library images are
    assertTrue(file.length() > DownscaleLibraryImagesCommand.MIN_FILE_LENGTH, "test image is " + file.length() + " bytes");

    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath(relativePath);
    image.setCreatedBy(userId);
    image.setFileLength(file.length());
    image.setFileType("image/png");
    image.setWidth(width);
    image.setHeight(height);
    image.setWebPath("20250314093000");
    image.setAltText("Human Type Targets infographic");
    return ImageRepository.save(image);
  }

  /** Each table starts empty with its identity reset, so the first row inserted is id 1 */
  private static void insert(String sql, String... values) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) {
        pst.setString(i + 1, values[i]);
      }
      pst.executeUpdate();
    }
  }

  private static String select(String sql, long id) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setLong(1, id);
      try (ResultSet rs = pst.executeQuery()) {
        assertTrue(rs.next());
        return rs.getString(1);
      }
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY, unique_id VARCHAR(255) UNIQUE NOT NULL)");
      statement.execute("INSERT INTO users (unique_id) VALUES ('admin')");
      userId = 1L;
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
      // Focused subsets: just the columns the reference scan and rewrite read and write
      statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, link VARCHAR(255) UNIQUE NOT NULL, "
          + "page_image_url VARCHAR(255), page_xml TEXT, draft_page_xml TEXT)");
      statement.execute("CREATE TABLE content (content_id BIGSERIAL PRIMARY KEY, content_unique_id VARCHAR(255) UNIQUE, "
          + "content TEXT, draft_content TEXT)");
      statement.execute("CREATE TABLE blog_posts (post_id BIGSERIAL PRIMARY KEY, title VARCHAR(255) NOT NULL, "
          + "body TEXT, image_url VARCHAR(255))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
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
}
