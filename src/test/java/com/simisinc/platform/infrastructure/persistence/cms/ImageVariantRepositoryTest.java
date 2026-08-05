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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ImageVariantRepository} against a real PostgreSQL instance (issue #411),
 * including the FK-cascade relationship to {@code images} and the save()-as-upsert behavior a
 * regenerated variant relies on.
 *
 * @author SimIS Inc.
 */
class ImageVariantRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping ImageVariantRepository integration test");

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
    userId = insertUser("variant-owner");
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
      statement.execute("TRUNCATE TABLE image_variants, images RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset images/image_variants tables", se);
    }
  }

  @Test
  void saveInsertsANewVariant() {
    Image image = insertImage();

    ImageVariant variant = newVariant(image.getId(), "thumbnail", "images/thumb.png", 12345, 200, 150);
    ImageVariant saved = ImageVariantRepository.save(variant);

    assertNotNull(saved);
    assertNotNull(saved.getId());
    assertEquals("thumbnail", saved.getVariantType());
    assertEquals(200, saved.getWidth());
  }

  @Test
  void saveOnAnExistingImageIdAndVariantTypeReplacesTheRowRatherThanDuplicatingIt() {
    Image image = insertImage();
    ImageVariantRepository.save(newVariant(image.getId(), "thumbnail", "images/thumb-v1.png", 100, 200, 150));

    ImageVariant regenerated = ImageVariantRepository
        .save(newVariant(image.getId(), "thumbnail", "images/thumb-v2.png", 200, 195, 150));

    assertEquals(1, DB.selectCountFrom("image_variants"), "a regenerated variant must replace, not duplicate");
    ImageVariant found = ImageVariantRepository.findByImageIdAndVariantType(image.getId(), "thumbnail");
    assertEquals("images/thumb-v2.png", found.getFileServerPath());
    assertEquals(195, found.getWidth());
    assertEquals(regenerated.getId(), found.getId(), "the row identity must be preserved across a regeneration");
  }

  @Test
  void findByImageIdReturnsEveryVariantForThatImageOnly() {
    Image image1 = insertImage();
    Image image2 = insertImage();
    ImageVariantRepository.save(newVariant(image1.getId(), "thumbnail", "images/1-thumb.png", 100, 200, 150));
    ImageVariantRepository.save(newVariant(image1.getId(), "medium", "images/1-medium.png", 400, 800, 600));
    ImageVariantRepository.save(newVariant(image2.getId(), "thumbnail", "images/2-thumb.png", 100, 200, 150));

    List<ImageVariant> image1Variants = ImageVariantRepository.findByImageId(image1.getId());

    assertEquals(2, image1Variants.size());
    assertTrue(image1Variants.stream().allMatch(v -> v.getImageId() == image1.getId()));
  }

  @Test
  void findByImageIdsGroupsVariantsByImageIdAcrossMultipleImages() {
    Image image1 = insertImage();
    Image image2 = insertImage();
    Image image3 = insertImage(); // no variants -- must not appear in the returned map at all
    ImageVariantRepository.save(newVariant(image1.getId(), "thumbnail", "images/1-thumb.png", 100, 200, 150));
    ImageVariantRepository.save(newVariant(image1.getId(), "medium", "images/1-medium.png", 400, 800, 600));
    ImageVariantRepository.save(newVariant(image2.getId(), "thumbnail", "images/2-thumb.png", 100, 200, 150));

    Map<Long, List<ImageVariant>> byImageId = ImageVariantRepository
        .findByImageIds(List.of(image1.getId(), image2.getId(), image3.getId()));

    assertEquals(2, byImageId.get(image1.getId()).size());
    assertEquals(1, byImageId.get(image2.getId()).size());
    assertNull(byImageId.get(image3.getId()), "an image with zero variants must not get an empty-list entry");
  }

  @Test
  void findByImageIdsReturnsAnEmptyMapForNullOrEmptyInputWithoutQuerying() {
    assertTrue(ImageVariantRepository.findByImageIds(null).isEmpty());
    assertTrue(ImageVariantRepository.findByImageIds(List.of()).isEmpty());
  }

  @Test
  void findByImageIdAndVariantTypeReturnsNullWhenNoSuchVariantExists() {
    Image image = insertImage();
    assertNull(ImageVariantRepository.findByImageIdAndVariantType(image.getId(), "large"));
  }

  @Test
  void regeneratingAVariantRefreshesModifiedButNotCreated() throws InterruptedException {
    // save() returns the same in-memory ImageVariant it was given -- add()/update() never
    // back-fill created/modified onto it, since those are DB-assigned defaults/updates. Reload
    // through findByImageIdAndVariantType() to see what was actually persisted.
    Image image = insertImage();
    ImageVariantRepository.save(newVariant(image.getId(), "thumbnail", "images/thumb-v1.png", 100, 200, 150));
    ImageVariant firstRow = findRow(image.getId());
    assertNotNull(firstRow.getModified(), "modified must be set on insert, not just created");

    // TIMESTAMP(3) is millisecond-precision -- without a gap, a fast regeneration could land in
    // the same millisecond and the assertion below couldn't distinguish "refreshed" from "never
    // touched."
    Thread.sleep(5);

    ImageVariantRepository.save(newVariant(image.getId(), "thumbnail", "images/thumb-v2.png", 200, 195, 150));
    ImageVariant reloaded = findRow(image.getId());

    assertEquals(firstRow.getCreated(), reloaded.getCreated(), "created must survive a regeneration unchanged");
    assertTrue(reloaded.getModified().after(firstRow.getModified()),
        "modified must advance on regeneration so StreamImageWidget's Last-Modified reflects the new bytes");
  }

  @Test
  void addSurfacesAUniqueConstraintViolationAsALoggedFailureRatherThanThrowing() throws Exception {
    // save() is check-then-act (find, then add()/update()), so the private add() INSERT path is
    // normally only reached when no row exists yet. Reflection reaches it directly to simulate
    // the race two concurrent save() calls for the same (imageId, variantType) could hit -- both
    // passing the "no existing row" check before either has inserted.
    Image image = insertImage();
    ImageVariant firstInsert = newVariant(image.getId(), "thumbnail", "images/thumb-a.png", 100, 200, 150);
    ImageVariant conflictingInsert = newVariant(image.getId(), "thumbnail", "images/thumb-b.png", 200, 195, 150);

    java.lang.reflect.Method add = ImageVariantRepository.class.getDeclaredMethod("add", ImageVariant.class);
    add.setAccessible(true);

    ImageVariant succeeded = (ImageVariant) add.invoke(null, firstInsert);
    assertNotNull(succeeded, "the first insert for a given (imageId, variantType) must succeed");

    ImageVariant conflicted = (ImageVariant) add.invoke(null, conflictingInsert);
    assertNull(conflicted, "a second concurrent insert for the same (imageId, variantType) must be "
        + "rejected by the unique index and surfaced as null, not thrown");
    assertEquals(1, DB.selectCountFrom("image_variants"), "the rejected insert must not leave a partial row");
  }

  private static ImageVariant findRow(long imageId) {
    ImageVariant row = ImageVariantRepository.findByImageIdAndVariantType(imageId, "thumbnail");
    assertNotNull(row, "test setup: expected a thumbnail row to exist");
    return row;
  }

  @Test
  void deletingTheParentImageCascadeDeletesItsVariantRows() throws SQLException {
    Image image = insertImage();
    ImageVariantRepository.save(newVariant(image.getId(), "thumbnail", "images/thumb.png", 100, 200, 150));

    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement("DELETE FROM images WHERE image_id = ?")) {
      pst.setLong(1, image.getId());
      pst.executeUpdate();
    }

    assertTrue(ImageVariantRepository.findByImageId(image.getId()).isEmpty(),
        "image_variants.image_id's ON DELETE CASCADE must remove orphaned variant rows");
  }

  private static ImageVariant newVariant(long imageId, String variantType, String path, long fileLength, int width,
      int height) {
    ImageVariant variant = new ImageVariant();
    variant.setImageId(imageId);
    variant.setVariantType(variantType);
    variant.setFileServerPath(path);
    variant.setFileLength(fileLength);
    variant.setFileType("image/png");
    variant.setWidth(width);
    variant.setHeight(height);
    return variant;
  }

  private static Image insertImage() {
    Image image = new Image();
    image.setFilename("photo.png");
    image.setFileServerPath("images/2026/08/photo.png");
    image.setCreatedBy(userId);
    image.setFileLength(1000);
    image.setFileType("image/png");
    image.setWidth(2000);
    image.setHeight(1500);
    image.setWebPath("20260803120000");
    Image saved = ImageRepository.save(image);
    assertNotNull(saved, "test setup: image must save");
    return saved;
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
    // Mirrors NEW_10010__new_cms.sql's `images` table + NEW_10160__new_image_variants.sql's
    // `image_variants` table.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS image_variants CASCADE");
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
          + "processed_path VARCHAR(255), "
          + "processed_file_length BIGINT DEFAULT 0, "
          + "processed_file_type VARCHAR(20), "
          + "processed_width INTEGER NOT NULL DEFAULT 0, "
          + "processed_height INTEGER NOT NULL DEFAULT 0, "
          + "web_path VARCHAR(50) NOT NULL, "
          + "focal_x NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00)");
      statement.execute("CREATE TABLE image_variants ("
          + "image_variant_id BIGSERIAL PRIMARY KEY, "
          + "image_id BIGINT NOT NULL REFERENCES images(image_id) ON DELETE CASCADE, "
          + "variant_type VARCHAR(20) NOT NULL, "
          + "path VARCHAR(255) NOT NULL, "
          + "file_length BIGINT DEFAULT 0, "
          + "file_type VARCHAR(20), "
          + "width INTEGER NOT NULL, "
          + "height INTEGER NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute(
          "CREATE UNIQUE INDEX image_variants_image_id_variant_type_idx ON image_variants(image_id, variant_type)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the images/image_variants/users schema", se);
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
}
