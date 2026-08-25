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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

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
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

/**
 * Verifies {@link GenerateImageVariantsCommand} actually invokes ImageMagick and persists the
 * result correctly (issue #411). Requires both Docker (ImageVariantRepository.save() needs a real
 * database) and a real {@code convert} binary on PATH -- skipped entirely when either is
 * unavailable (e.g. a local sandbox with no ImageMagick installed), but runs for real on GitHub
 * Actions' ubuntu-latest runners, which ship ImageMagick preinstalled, and against the real
 * {@code docker/app/Dockerfile} image the app actually deploys.
 *
 * @author SimIS Inc.
 */
class GenerateImageVariantsCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping GenerateImageVariantsCommand integration test");
    Assumptions.assumeTrue(isImageMagickAvailable(), "ImageMagick is not on PATH - skipping GenerateImageVariantsCommand integration test");

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
    userId = insertUser("variant-generator-owner");
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker/ImageMagick is unavailable
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
  void generateVariantsProducesEveryRungForALargeOriginal(@TempDir Path tempDir) throws Exception {
    Image image = insertImageWithRealFile(tempDir, "large-original.png", 2000, 1500);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    Map<String, ImageVariant> byType = variants.stream()
        .collect(Collectors.toMap(ImageVariant::getVariantType, v -> v));
    assertEquals(4, variants.size(), "a 2000x1500 original is larger than all four variant targets");
    assertTrue(byType.get("thumbnail").getWidth() <= 200 && byType.get("thumbnail").getHeight() <= 200);
    assertTrue(byType.get("small").getWidth() <= 400 && byType.get("small").getHeight() <= 400);
    assertTrue(byType.get("medium").getWidth() <= 800 && byType.get("medium").getHeight() <= 800);
    assertTrue(byType.get("large").getWidth() <= 1600 && byType.get("large").getHeight() <= 1600);
    // 2000x1500 is a 4:3 image -- fitting within an 800x800 box is width-constrained
    assertEquals(800, byType.get("medium").getWidth());
    assertEquals(600, byType.get("medium").getHeight());

    // Persisted, and the file it points at is real
    ImageVariant persisted = ImageVariantRepository.findByImageIdAndVariantType(image.getId(), "medium");
    File variantFile = new File(tempDir.toString() + "/" + persisted.getFileServerPath());
    assertTrue(variantFile.isFile(), "the resized file must actually exist on disk");
  }

  @Test
  void generateVariantsSkipsEveryTargetTheOriginalIsAlreadySmallerThan(@TempDir Path tempDir) throws Exception {
    Image image = insertImageWithRealFile(tempDir, "tiny-original.png", 150, 100);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    assertTrue(variants.isEmpty(), "a 150x100 original is smaller than even the thumbnail target -- nothing to generate");
    assertTrue(ImageVariantRepository.findByImageId(image.getId()).isEmpty());
  }

  @Test
  void generateVariantsFillsTheGapForAnOriginalBetweenTheThumbnailAndMediumTargets(@TempDir Path tempDir)
      throws Exception {
    // Issue #1422: a 752px-wide original is larger than the thumbnail target (200) but smaller than
    // both medium (800) and large (1600), which variant generation correctly skips as upscales. The
    // ladder therefore produced exactly one variant, leaving a srcset of "200w, original" with a 4x
    // hole -- and a 251px slot, unable to use a 200w thumbnail, fell through to the full original.
    // The browser was picking correctly; the candidate list was the problem. The 400px rung fills it.
    Image image = insertImageWithRealFile(tempDir, "card-art.png", 752, 246);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    Map<String, ImageVariant> byType = variants.stream()
        .collect(java.util.stream.Collectors.toMap(ImageVariant::getVariantType, v -> v));
    assertEquals(2, variants.size(), "a 752x246 original clears thumbnail and small, but not medium or large");
    assertTrue(byType.containsKey("thumbnail"));
    assertTrue(byType.containsKey("small"), "the 400px rung is what closes the gap this issue is about");
    assertTrue(!byType.containsKey("medium"), "752 is below the medium target -- generating it would upscale");
    assertTrue(!byType.containsKey("large"));
    // Width-constrained: 752x246 fitted into a 400x400 box lands on 400 wide.
    assertEquals(400, byType.get("small").getWidth());
    assertTrue(byType.get("small").getWidth() > byType.get("thumbnail").getWidth(),
        "the new rung must sit strictly between the thumbnail and the original");
    assertTrue(byType.get("small").getWidth() < image.getWidth());
  }

  @Test
  void generateVariantsOnlySkipsTheTargetsSmallerThanTheOriginalOnBothAxes(@TempDir Path tempDir) throws Exception {
    // A 1000x100 banner exceeds the thumbnail box's width (200) even though its height doesn't --
    // the skip condition must require BOTH dimensions to already fit, not just one.
    Image image = insertImageWithRealFile(tempDir, "banner.png", 1000, 100);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    ImageVariant thumbnail = variants.stream().filter(v -> "thumbnail".equals(v.getVariantType())).findFirst()
        .orElseThrow(() -> new AssertionError("a thumbnail variant must have been generated for a 1000-wide banner"));
    assertEquals(200, thumbnail.getWidth());
    assertEquals(20, thumbnail.getHeight());
  }

  @Test
  void generateVariantsReturnsEmptyForAnUnsupportedFileType(@TempDir Path tempDir) throws Exception {
    Image image = insertImageWithRealFile(tempDir, "vector.svg", 2000, 1500);
    image.setFileType("image/svg+xml");

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    assertTrue(variants.isEmpty(), "SVG is resolution-independent and must not be rasterized into variants");
  }

  @Test
  void generateVariantsReturnsEmptyWhenTheOriginalFileIsMissing(@TempDir Path tempDir) throws Exception {
    Image image = new Image();
    image.setFilename("gone.png");
    image.setFileServerPath("images/2026/08/gone.png");
    image.setCreatedBy(userId);
    image.setFileLength(1000);
    image.setFileType("image/png");
    image.setWidth(2000);
    image.setHeight(1500);
    image.setWebPath("20260803120100");
    Image saved = ImageRepository.save(image);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(saved));

    assertTrue(variants.isEmpty());
  }

  @Test
  void generateVariantsOnANullOrUnsavedImageReturnsEmptyWithoutThrowing() {
    assertTrue(GenerateImageVariantsCommand.generateVariants(null).isEmpty());
    assertTrue(GenerateImageVariantsCommand.generateVariants(new Image()).isEmpty());
  }

  @Test
  void generateVariantsSkipsWritingOnceTheImageRowIsGone(@TempDir Path tempDir) throws Exception {
    // ImageVariantJob runs asynchronously off the upload's critical path, so an admin can delete
    // the image while a variant is still being generated. GenerateImageVariantsCommand re-checks
    // the image row immediately before writing each variant so a deletion mid-run can't leave an
    // orphaned file with no row and therefore no cleanup path.
    Image image = insertImageWithRealFile(tempDir, "large-original.png", 2000, 1500);
    assertTrue(ImageRepository.remove(image), "test setup: could not remove the image row");

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    assertTrue(variants.isEmpty(), "must not generate or persist variants once the image row is gone");
    File expectedMediumFile = tempDir.resolve("images/2026/08/large-original-medium.png").toFile();
    assertTrue(!expectedMediumFile.exists(), "must not have written a variant file for a deleted image");
  }

  @Test
  void generateVariantsContinuesPastOneVariantFailing(@TempDir Path tempDir) throws Exception {
    // The per-variant try/catch's whole purpose is that one variant failing (e.g. a policy.xml
    // restriction, or here, an unwritable destination) must not stop the others. Pre-creating a
    // directory at exactly the "medium" variant's expected output path forces convert to fail to
    // write only that one variant, while thumbnail and large -- unaffected -- must still succeed.
    Image image = insertImageWithRealFile(tempDir, "large-original.png", 2000, 1500);
    File blockedPath = tempDir.resolve("images/2026/08/large-original-medium.png").toFile();
    assertTrue(blockedPath.mkdirs(), "test setup: could not create the blocking directory");

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    Map<String, ImageVariant> byType = variants.stream()
        .collect(Collectors.toMap(ImageVariant::getVariantType, v -> v));
    assertTrue(byType.containsKey("thumbnail"), "thumbnail must still be generated despite medium failing");
    assertTrue(byType.containsKey("small"), "small must still be generated despite medium failing");
    assertTrue(byType.containsKey("large"), "large must still be generated despite medium failing");
    assertTrue(!byType.containsKey("medium"), "medium must have failed rather than crashed the whole method");
    assertEquals(3, variants.size());
  }

  @Test
  void generateVariantsCoalescesAnAnimatedGifSoEveryFrameSurvivesTheResize(@TempDir Path tempDir) throws Exception {
    // A GIF whose second frame is a smaller sub-rectangle at a nonzero offset (not the full
    // logical canvas) is exactly the shape that silently breaks when resized without -coalesce
    // first -- the frame count itself can drop, or per-frame offsets can end up wrong. Asserting
    // the resized variant still reports the same frame count is a concrete, checkable proxy for
    // "the coalesce/layers pipeline actually ran," without needing pixel-level animation
    // comparison.
    String relativePath = "images/2026/08/animated.gif";
    File originalFile = tempDir.resolve(relativePath).toFile();
    originalFile.getParentFile().mkdirs();
    createAnimatedGifWithOffsetFrames(originalFile, 1000);
    int originalFrameCount = countGifFrames(originalFile);
    assertEquals(2, originalFrameCount, "test setup: expected a 2-frame animated GIF fixture");

    Image image = new Image();
    image.setFilename("animated.gif");
    image.setFileServerPath(relativePath);
    image.setCreatedBy(userId);
    image.setFileLength(originalFile.length());
    image.setFileType("image/gif");
    image.setWidth(1000);
    image.setHeight(1000);
    image.setWebPath("20260803120200");
    Image saved = ImageRepository.save(image);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(saved));

    ImageVariant medium = variants.stream().filter(v -> "medium".equals(v.getVariantType())).findFirst()
        .orElseThrow(() -> new AssertionError("a medium variant must have been generated for a 1000x1000 GIF"));
    File variantFile = tempDir.resolve(medium.getFileServerPath()).toFile();
    assertEquals(originalFrameCount, countGifFrames(variantFile),
        "resizing an animated GIF must not drop frames -- coalesce before resize, layers optimize after");
  }

  @Test
  void generateVariantsReadsTheResizedVariantsDimensionsForAWebpOriginal(@TempDir Path tempDir) throws Exception {
    // Issue #931: readDimension() (used to record each generated variant's width/height) had the
    // same JDK-ImageIO-only limitation as ValidateImageCommand's upload-time check -- it could
    // resize a WebP original but then fail to read the resized variant's own dimensions. This
    // exercises that exact second call site via the shared ImageDimensionCommand fix.
    Assumptions.assumeTrue(isImageMagickAvailable(), "ImageMagick is not on PATH - skipping WebP variant test");

    Image image = insertImageWithRealWebpFile(tempDir, "large-original.webp", 2000, 1500);

    List<ImageVariant> variants = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateVariants(image));

    Map<String, ImageVariant> byType = variants.stream()
        .collect(Collectors.toMap(ImageVariant::getVariantType, v -> v));
    assertEquals(4, variants.size(), "a 2000x1500 WebP original is larger than all four variant targets");
    assertEquals(800, byType.get("medium").getWidth(), "the medium variant's own dimensions must be readable");
    assertEquals(600, byType.get("medium").getHeight());
  }

  @Test
  void generateSquareVariantCropsAroundTheStoredFocalPointAndProducesASquareFile(@TempDir Path tempDir) throws Exception {
    // A wide 2000x1000 original with a focal point near the right edge -- the exact crop offset is
    // covered by FocalPointCropCommandTest; this only needs to prove the wiring produces a real,
    // square, persisted variant using the image's own stored focal point.
    Image image = insertImageWithRealFile(tempDir, "wide-original.png", 2000, 1000);
    image.setFocalX(new BigDecimal("90"));
    image.setFocalY(new BigDecimal("50"));
    ImageRepository.save(image);

    ImageVariant variant = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateSquareVariant(image));

    assertEquals("square", variant.getVariantType());
    assertEquals(400, variant.getWidth());
    assertEquals(400, variant.getHeight());
    File variantFile = tempDir.resolve(variant.getFileServerPath()).toFile();
    assertTrue(variantFile.isFile(), "the cropped file must actually exist on disk");
    ImageVariant persisted = ImageVariantRepository.findByImageIdAndVariantType(image.getId(), "square");
    assertEquals(variant.getId(), persisted.getId());
  }

  @Test
  void generateSquareVariantReturnsNullForAnUnsupportedFileType(@TempDir Path tempDir) throws Exception {
    Image image = insertImageWithRealFile(tempDir, "vector.svg", 2000, 1500);
    image.setFileType("image/svg+xml");

    ImageVariant variant = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateSquareVariant(image));

    assertNull(variant, "SVG is resolution-independent and must not be rasterized into a square variant");
  }

  @Test
  void generateSquareVariantReturnsNullWhenTheOriginalFileIsMissing(@TempDir Path tempDir) throws Exception {
    Image image = new Image();
    image.setFilename("gone.png");
    image.setFileServerPath("images/2026/08/gone.png");
    image.setCreatedBy(userId);
    image.setFileLength(1000);
    image.setFileType("image/png");
    image.setWidth(2000);
    image.setHeight(1500);
    image.setWebPath("20260804120100");
    Image saved = ImageRepository.save(image);

    ImageVariant variant = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateSquareVariant(saved));

    assertNull(variant);
  }

  @Test
  void generateSquareVariantOnANullOrUnsavedImageReturnsNullWithoutThrowing() {
    assertNull(GenerateImageVariantsCommand.generateSquareVariant(null));
    assertNull(GenerateImageVariantsCommand.generateSquareVariant(new Image()));
  }

  @Test
  void generateSquareVariantSkipsWritingOnceTheImageRowIsGone(@TempDir Path tempDir) throws Exception {
    Image image = insertImageWithRealFile(tempDir, "wide-original.png", 2000, 1000);
    assertTrue(ImageRepository.remove(image), "test setup: could not remove the image row");

    ImageVariant variant = withStubbedRoot(tempDir, () -> GenerateImageVariantsCommand.generateSquareVariant(image));

    assertNull(variant, "must not generate or persist a variant once the image row is gone");
    File expectedSquareFile = tempDir.resolve("images/2026/08/wide-original-square.png").toFile();
    assertTrue(!expectedSquareFile.exists(), "must not have written a variant file for a deleted image");
  }

  private static Image insertImageWithRealWebpFile(Path tempDir, String filename, int width, int height)
      throws Exception {
    String relativePath = "images/2026/08/" + filename;
    File file = tempDir.resolve(relativePath).toFile();
    file.getParentFile().mkdirs();
    File pngSource = File.createTempFile("webp-source", ".png");
    try {
      ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", pngSource);
      Process process = new ProcessBuilder("convert", pngSource.getAbsolutePath(), file.getAbsolutePath())
          .redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException("Could not build the WebP test fixture: " + output);
      }
    } finally {
      pngSource.delete();
    }

    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath(relativePath);
    image.setCreatedBy(userId);
    image.setFileLength(file.length());
    image.setFileType("image/webp");
    image.setWidth(width);
    image.setHeight(height);
    image.setWebPath("20260803120300");
    return ImageRepository.save(image);
  }

  /** Builds a 2-frame animated GIF whose second frame is a sub-rectangle at a nonzero offset. */
  private static void createAnimatedGifWithOffsetFrames(File outputGif, int canvasSize) throws Exception {
    File frame1 = File.createTempFile("frame1", ".png");
    File frame2 = File.createTempFile("frame2", ".png");
    try {
      ImageIO.write(new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB), "png", frame1);
      ImageIO.write(new BufferedImage(canvasSize / 2, canvasSize / 2, BufferedImage.TYPE_INT_RGB), "png", frame2);
      Process process = new ProcessBuilder(
          "convert", "-delay", "20", "-loop", "0",
          "-page", canvasSize + "x" + canvasSize + "+0+0", frame1.getAbsolutePath(),
          "-dispose", "none",
          "-page", (canvasSize / 2) + "x" + (canvasSize / 2) + "+" + (canvasSize / 4) + "+" + (canvasSize / 4),
          frame2.getAbsolutePath(),
          outputGif.getAbsolutePath())
          .redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException("Could not build the animated GIF test fixture: " + output);
      }
    } finally {
      frame1.delete();
      frame2.delete();
    }
  }

  private static int countGifFrames(File gifFile) throws Exception {
    Process process = new ProcessBuilder("identify", "-format", "%n\\n", gifFile.getAbsolutePath())
        .redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes()).trim();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("Could not identify the GIF fixture: " + output);
    }
    // `identify -format %n` prints the frame count once per frame (identically) -- the first
    // line is enough.
    return Integer.parseInt(output.split("\\r?\\n")[0].trim());
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

  private static Image insertImageWithRealFile(Path tempDir, String filename, int width, int height)
      throws IOException {
    String relativePath = "images/2026/08/" + filename;
    File file = tempDir.resolve(relativePath).toFile();
    file.getParentFile().mkdirs();
    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(bufferedImage, "png", file);

    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath(relativePath);
    image.setCreatedBy(userId);
    image.setFileLength(file.length());
    image.setFileType("image/png");
    image.setWidth(width);
    image.setHeight(height);
    image.setWebPath("20260803120000");
    Image saved = ImageRepository.save(image);
    assertNull(saved.getProcessed());
    return saved;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  private static boolean isImageMagickAvailable() {
    try {
      Process process = new ProcessBuilder("convert", "-version").redirectErrorStream(true).start();
      return process.waitFor() == 0;
    } catch (Exception e) {
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
          + "focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "file_hash VARCHAR(1024), "
          + "alt_text VARCHAR(255))");
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
