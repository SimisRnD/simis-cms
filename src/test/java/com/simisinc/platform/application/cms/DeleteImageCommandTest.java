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
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Verifies {@link DeleteImageCommand} against a real PostgreSQL instance and a real file system
 * (issue #498): the database row must be removed before the physical file is touched, a database
 * delete that removes nothing must leave the file alone, and a file that is already missing from
 * disk must not turn a real delete into a failure.
 *
 * <p>
 * {@link FileSystemCommand#getFileServerRootPath()} normally resolves from the {@code CMS_PATH}
 * environment variable or a {@code system.filepath} site property (a DB row this test's minimal
 * schema does not have); it is stubbed here to point at a JUnit {@code @TempDir} instead, while
 * {@link FileSystemCommand#resolveWithinRoot} -- the actual path-safety logic under test -- still
 * runs for real via {@code CALLS_REAL_METHODS}.
 * </p>
 *
 * @author SimIS Inc.
 */
class DeleteImageCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping DeleteImageCommand integration test");

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
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE images RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset images table", se);
    }
  }

  @Test
  void deleteImageRemovesTheDatabaseRowAndThePhysicalFile(@TempDir Path tempDir) throws Exception {
    Path relativeFile = Path.of("2026", "07", "test-photo.png");
    writeRealFile(tempDir, relativeFile, "fake png bytes");

    Image image = insertImage("test-photo.png", relativeFile.toString());

    boolean removed = withStubbedRoot(tempDir, () -> DeleteImageCommand.deleteImage(image));

    assertTrue(removed, "deleteImage() must report success for a real row with a real file");
    assertNull(ImageRepository.findById(image.getId()), "the database row must be gone");
    assertFalse(Files.exists(tempDir.resolve(relativeFile)), "the physical file must be gone");
  }

  @Test
  void deleteImageNeverTouchesTheFileWhenTheDatabaseDeleteRemovesNothing(@TempDir Path tempDir) throws Exception {
    // A row that was never inserted (or already deleted by another request) -- ImageRepository.remove()
    // will delete 0 rows, and DeleteImageCommand must treat that as a failure and leave the file alone.
    Path relativeFile = Path.of("2026", "07", "orphan-row.png");
    writeRealFile(tempDir, relativeFile, "fake png bytes");

    Image image = new Image();
    image.setId(999999L);
    image.setFilename("orphan-row.png");
    image.setFileServerPath(relativeFile.toString());

    boolean removed = withStubbedRoot(tempDir, () -> DeleteImageCommand.deleteImage(image));

    assertFalse(removed, "deleteImage() must report failure when no database row was actually deleted");
    assertTrue(Files.exists(tempDir.resolve(relativeFile)),
        "the physical file must be untouched -- the database delete must happen first, and gate the file delete");
  }

  @Test
  void deleteImageOnAFileAlreadyMissingFromDiskStillSucceedsWithoutThrowing(@TempDir Path tempDir) throws Exception {
    // The file was never written (or was removed by hand) -- real-world state can already be
    // inconsistent, so this must still delete the database row and must not throw.
    Image image = insertImage("already-gone.png", "2026/07/already-gone.png");

    boolean removed = withStubbedRoot(tempDir, () -> DeleteImageCommand.deleteImage(image));

    assertTrue(removed, "a missing physical file must not fail the delete");
    assertNull(ImageRepository.findById(image.getId()), "the database row must still be removed");
  }

  @Test
  void deleteImageOnAnInvalidImageReturnsFalseWithoutTouchingAnything() {
    assertFalse(DeleteImageCommand.deleteImage(null));

    Image neverSaved = new Image();
    // default id is -1
    assertFalse(DeleteImageCommand.deleteImage(neverSaved));
  }

  @Test
  void bulkDeleteLoopRemovesExactlyTheSelectedImagesAndLeavesOthersUntouched(@TempDir Path tempDir) throws Exception {
    Path file1 = Path.of("2026", "07", "one.png");
    Path file2 = Path.of("2026", "07", "two.png");
    Path file3 = Path.of("2026", "07", "three.png");
    writeRealFile(tempDir, file1, "one");
    writeRealFile(tempDir, file2, "two");
    writeRealFile(tempDir, file3, "three");

    Image image1 = insertImage("one.png", file1.toString());
    Image image2 = insertImage("two.png", file2.toString());
    Image image3 = insertImage("three.png", file3.toString());

    // Mirrors AdminImageBrowserWidget's bulk-delete loop: DeleteImageCommand.deleteImage() called
    // once per selected id -- here, every id except image2's.
    withStubbedRoot(tempDir, () -> {
      assertTrue(DeleteImageCommand.deleteImage(image1));
      assertTrue(DeleteImageCommand.deleteImage(image3));
      return null;
    });

    assertNull(ImageRepository.findById(image1.getId()), "image1 must be deleted");
    assertNull(ImageRepository.findById(image3.getId()), "image3 must be deleted");
    assertNotNull(ImageRepository.findById(image2.getId()), "image2 was never selected and must be untouched");

    assertFalse(Files.exists(tempDir.resolve(file1)));
    assertFalse(Files.exists(tempDir.resolve(file3)));
    assertTrue(Files.exists(tempDir.resolve(file2)), "image2's file must be untouched");

    assertEquals(1, DB.selectCountFrom("images"), "exactly one image (image2) must remain");
  }

  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private static <T> T withStubbedRoot(Path tempDir, ThrowingSupplier<T> body) throws Exception {
    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, Answers.CALLS_REAL_METHODS)) {
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      return body.get();
    }
  }

  private static void writeRealFile(Path tempDir, Path relativeFile, String content) throws IOException {
    Path absolute = tempDir.resolve(relativeFile);
    Files.createDirectories(absolute.getParent());
    Files.writeString(absolute, content);
  }

  private static Image insertImage(String filename, String fileServerPath) {
    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath(fileServerPath);
    image.setCreatedBy(userId);
    image.setFileLength(filename.length());
    image.setFileType("image/png");
    image.setWidth(100);
    image.setHeight(100);
    image.setWebPath("2026/07");
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
    // Mirrors NEW_10010__new_cms.sql's `images` table exactly (minus the users FK's real geom-heavy
    // shape, which this test has no need for -- same pattern as MediaAssetRepositoryTest).
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
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
          + "web_path VARCHAR(50) NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the images/users schema", se);
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
