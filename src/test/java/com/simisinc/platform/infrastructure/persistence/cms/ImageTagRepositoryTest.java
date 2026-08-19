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
import java.util.Map;
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
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ImageTagRepository} and {@link ImageTagMapRepository} against a real PostgreSQL
 * instance: save/find/remove, the case-insensitive unique name constraint, the live
 * {@code countAllByImageTagId()} count (no maintained counter column -- see the class docs on
 * {@link ImageTagRepository}), and the batch {@code findByImageIds}.
 *
 * @author SimIS Inc.
 */
class ImageTagRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping ImageTagRepository test");

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
      statement.execute("TRUNCATE TABLE image_tag_map, image_tags, images RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void saveThenFindByIdRoundTripsAnImageTag() {
    ImageTag tag = new ImageTag();
    tag.setName("Homepage");
    tag.setCreatedBy(userId);

    ImageTag saved = ImageTagRepository.save(tag);
    ImageTag reloaded = ImageTagRepository.findById(saved.getId());

    assertEquals("Homepage", reloaded.getName());
  }

  @Test
  void findByNameIsCaseInsensitive() {
    ImageTag tag = new ImageTag();
    tag.setName("Homepage");
    tag.setCreatedBy(userId);
    ImageTagRepository.save(tag);

    ImageTag found = ImageTagRepository.findByName("HOMEPAGE");

    assertEquals("Homepage", found.getName());
  }

  @Test
  void aSecondTagWithTheSameNameDifferentCaseViolatesTheUniqueIndex() {
    ImageTag first = new ImageTag();
    first.setName("Homepage");
    first.setCreatedBy(userId);
    ImageTagRepository.save(first);

    ImageTag second = new ImageTag();
    second.setName("HOMEPAGE");
    second.setCreatedBy(userId);
    ImageTag saved = ImageTagRepository.save(second);

    assertNull(saved, "the case-insensitive unique index must reject a second tag with the same name");
  }

  @Test
  void removeDeletesTheTagAndItsImageAssignments() throws SQLException {
    ImageTag tag = new ImageTag();
    tag.setName("Homepage");
    tag.setCreatedBy(userId);
    ImageTag saved = ImageTagRepository.save(tag);
    Image image = insertImage("hero.png");
    linkImageToTag(image.getId(), saved.getId());

    boolean removed = ImageTagRepository.remove(saved);

    assertTrue(removed);
    assertNull(ImageTagRepository.findById(saved.getId()));
    assertEquals(0, countImageTagMapRows());
  }

  @Test
  void countAllByImageTagIdCountsOnlyAssignedImages() {
    ImageTag tagWithImages = new ImageTag();
    tagWithImages.setName("Homepage");
    tagWithImages.setCreatedBy(userId);
    ImageTag savedTagWithImages = ImageTagRepository.save(tagWithImages);

    ImageTag tagWithNoImages = new ImageTag();
    tagWithNoImages.setName("Unused");
    tagWithNoImages.setCreatedBy(userId);
    ImageTag savedTagWithNoImages = ImageTagRepository.save(tagWithNoImages);

    Image imageA = insertImage("a.png");
    Image imageB = insertImage("b.png");
    linkImageToTag(imageA.getId(), savedTagWithImages.getId());
    linkImageToTag(imageB.getId(), savedTagWithImages.getId());

    Map<Long, Long> counts = ImageTagRepository.countAllByImageTagId();

    assertEquals(2L, counts.get(savedTagWithImages.getId()));
    assertTrue(!counts.containsKey(savedTagWithNoImages.getId()),
        "a tag with zero assigned images must have no entry, not a zero entry");
  }

  @Test
  void findByImageIdsBatchLoadsEachImagesOwnTagsOnly() {
    ImageTag tagA = ImageTagRepository.save(newTag("Homepage"));
    ImageTag tagB = ImageTagRepository.save(newTag("Banner"));
    Image imageA = insertImage("a.png");
    Image imageB = insertImage("b.png");
    linkImageToTag(imageA.getId(), tagA.getId());
    linkImageToTag(imageB.getId(), tagB.getId());

    Map<Long, List<ImageTag>> result = ImageTagRepository.findByImageIds(List.of(imageA.getId(), imageB.getId()));

    assertEquals(1, result.get(imageA.getId()).size());
    assertEquals("Homepage", result.get(imageA.getId()).get(0).getName());
    assertEquals(1, result.get(imageB.getId()).size());
    assertEquals("Banner", result.get(imageB.getId()).get(0).getName());
  }

  private static ImageTag newTag(String name) {
    ImageTag tag = new ImageTag();
    tag.setName(name);
    tag.setCreatedBy(userId);
    return tag;
  }

  private static int countImageTagMapRows() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM image_tag_map")) {
      rs.next();
      return rs.getInt(1);
    } catch (SQLException se) {
      throw new IllegalStateException("Could not count image_tag_map rows", se);
    }
  }

  private static void linkImageToTag(long imageId, long imageTagId) {
    try (Connection connection = DB.getConnection();
        AutoStartTransaction a = new AutoStartTransaction(connection);
        AutoRollback transaction = new AutoRollback(connection)) {
      Image image = new Image();
      image.setId(imageId);
      ImageTagMapRepository.insertImageTagId(connection, image, imageTagId);
      transaction.commit();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link test image to tag", se);
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
      statement.execute("DROP TABLE IF EXISTS image_tag_map CASCADE");
      statement.execute("DROP TABLE IF EXISTS image_tags CASCADE");
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
          + "alt_text VARCHAR(255))");
      statement.execute("CREATE TABLE image_tags ("
          + "image_tag_id BIGSERIAL PRIMARY KEY, "
          + "name VARCHAR(255) NOT NULL, "
          + "created_by BIGINT REFERENCES users(user_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE UNIQUE INDEX image_tags_name_uidx ON image_tags(LOWER(name))");
      statement.execute("CREATE TABLE image_tag_map ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "image_id BIGINT REFERENCES images(image_id) NOT NULL, "
          + "image_tag_id BIGINT REFERENCES image_tags(image_tag_id) NOT NULL)");
      statement.execute("CREATE UNIQUE INDEX image_tag_map_uidx ON image_tag_map(image_id, image_tag_id)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the image tags schema", se);
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
}
