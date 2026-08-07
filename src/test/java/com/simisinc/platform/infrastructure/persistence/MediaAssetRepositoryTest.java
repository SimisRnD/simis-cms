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

package com.simisinc.platform.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
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

import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies MediaAssetRepository against a real PostgreSQL instance, with a focus on the
 * {@code save()} id-routing bug (issue #773 follow-up): {@link MediaAsset#getId()} used to default to
 * {@code 0}, and {@code save()} routes to {@code update()} whenever {@code id > -1} -- true for the
 * default {@code 0} -- so every brand-new asset was silently misrouted to an {@code UPDATE} against a
 * non-existent row instead of an {@code INSERT}, and the save failed every time. {@link MediaAsset} now
 * defaults {@code id} to {@code -1} (matching every other domain model in this codebase, e.g. App,
 * DatabaseVersion), so a fresh instance correctly routes to {@code add()}.
 *
 * @author SimIS Inc.
 */
class MediaAssetRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping MediaAssetRepository integration test");

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
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE media_assets RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset media_assets table", se);
    }
  }

  @Test
  void saveOnANewAssetActuallyInsertsAndTheAssetIsRetrievableAfterward() {
    // The core regression test: before the #773 follow-up fix, this call silently misrouted to
    // update() against a non-existent row (id=0), update() failed, and save() returned null -- so
    // real uploads/creates never persisted anything at all, always ending in a 500.
    MediaAsset asset = new MediaAsset();
    asset.setAssetId("asset-real-add-1");
    asset.setAssetName("photo.jpg");
    asset.setAssetType("image");
    asset.setMimeType("image/jpeg");
    asset.setFileSizeBytes(1024);
    asset.setStoragePath("media-library/2026/07/31/unique-name.jpg");
    asset.setAltText("photo.jpg");
    asset.setCreatedBy(7);
    asset.setCreatedAt(LocalDateTime.now());

    MediaAsset saved = MediaAssetRepository.save(asset);

    assertNotNull(saved, "save() must succeed via add() for a brand-new asset");
    assertTrue(saved.getId() > 0, "a real INSERT must assign a positive generated id");
    assertEquals(1, DB.selectCountFrom("media_assets"));

    MediaAsset reloadedById = MediaAssetRepository.findById(saved.getId());
    assertNotNull(reloadedById, "the newly added asset must be retrievable by its assigned id");
    assertEquals("photo.jpg", reloadedById.getAssetName());
    assertEquals("media-library/2026/07/31/unique-name.jpg", reloadedById.getStoragePath());

    MediaAsset reloadedByAssetId = MediaAssetRepository.findByAssetId("asset-real-add-1");
    assertNotNull(reloadedByAssetId, "the newly added asset must be retrievable by its assetId");
    assertEquals(saved.getId(), reloadedByAssetId.getId());
  }

  @Test
  void savingASecondNewAssetAddsARowInsteadOfOverwritingTheFirst() {
    // A second brand-new MediaAsset also has id=-1 (each is its own fresh instance) and must also
    // route to add(), not silently collide with or overwrite the first row.
    MediaAsset first = addAsset("asset-a", "a.jpg");
    MediaAsset second = addAsset("asset-b", "b.jpg");

    assertTrue(second.getId() > first.getId());
    assertEquals(2, DB.selectCountFrom("media_assets"));
    assertNotNull(MediaAssetRepository.findById(first.getId()));
    assertNotNull(MediaAssetRepository.findById(second.getId()));
  }

  @Test
  void saveOnAnExistingAssetRoutesToUpdateNotAdd() {
    MediaAsset saved = addAsset("asset-update-me", "original.jpg");
    long originalId = saved.getId();

    saved.setAssetName("renamed.jpg");
    saved.setUpdatedAt(LocalDateTime.now());
    MediaAsset updated = MediaAssetRepository.save(saved);

    assertNotNull(updated);
    assertEquals(originalId, updated.getId(), "update() must not change the row's id");
    assertEquals(1, DB.selectCountFrom("media_assets"), "update() must not insert a second row");
    assertEquals("renamed.jpg", MediaAssetRepository.findById(originalId).getAssetName());
  }

  @Test
  void findByIdReturnsNullForAMissingOrNegativeId() {
    assertNull(MediaAssetRepository.findById(999));
    assertNull(MediaAssetRepository.findById(-1));
  }

  @Test
  void softDeleteExcludesTheAssetFromFindAllButLeavesItFindableById() {
    // Media Library delete feature: softDelete() only sets deleted_at, it never removes the row --
    // findAll() (the query the picker's listing uses) must filter it out anyway, or a "deleted"
    // asset keeps reappearing. findById/findByAssetId deliberately do NOT filter it out --
    // handleServeFile still needs to resolve an asset already embedded into a live page.
    MediaAsset visible = addAsset("asset-visible", "visible.jpg");
    MediaAsset deleted = addAsset("asset-deleted", "deleted.jpg");

    assertTrue(MediaAssetRepository.softDelete(deleted.getId()));

    java.util.List<MediaAsset> all = MediaAssetRepository.findAll(null);
    assertEquals(1, all.size());
    assertEquals(visible.getId(), all.get(0).getId());

    assertNotNull(MediaAssetRepository.findById(deleted.getId()),
        "findById must still resolve a soft-deleted asset");
    assertNotNull(MediaAssetRepository.findByAssetId("asset-deleted"),
        "findByAssetId must still resolve a soft-deleted asset");
  }

  private static MediaAsset addAsset(String assetId, String assetName) {
    MediaAsset asset = new MediaAsset();
    asset.setAssetId(assetId);
    asset.setAssetName(assetName);
    asset.setAssetType("image");
    asset.setMimeType("image/jpeg");
    asset.setFileSizeBytes(512);
    asset.setStoragePath("media-library/2026/07/31/" + assetId + ".jpg");
    asset.setAltText(assetName);
    asset.setCreatedBy(7);
    asset.setCreatedAt(LocalDateTime.now());
    return MediaAssetRepository.save(asset);
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
      statement.execute("DROP TABLE IF EXISTS media_assets CASCADE");
      // Mirrors NEW_10125__new_media_assets.sql / UPGRADE_20260726.1004, minus the FK to users
      // (out of scope for this repository-level test).
      statement.execute("CREATE TABLE media_assets ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "asset_id VARCHAR(128) NOT NULL UNIQUE, "
          + "asset_name VARCHAR(512) NOT NULL, "
          + "asset_type VARCHAR(32) NOT NULL, "
          + "mime_type VARCHAR(64), "
          + "file_size_bytes BIGINT NOT NULL, "
          + "storage_path TEXT NOT NULL, "
          + "alt_text TEXT NOT NULL, "
          + "tags TEXT, "
          + "created_by BIGINT NOT NULL, "
          + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "deleted_at TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the media_assets schema", se);
    }
  }
}
