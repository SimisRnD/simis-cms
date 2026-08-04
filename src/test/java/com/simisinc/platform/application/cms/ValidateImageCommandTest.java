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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;

/**
 * Verifies {@link ValidateImageCommand#checkFile} correctly accepts a WebP upload (issue #931) --
 * previously rejected with "Image could not be read" because the dimension read only tried the
 * JDK's own {@code javax.imageio}, which cannot decode WebP. Also locks in the pre-existing
 * behavior for a missing file and a non-image file, both unchanged by that fix.
 *
 * <p>
 * The WebP test requires a real ImageMagick binary on PATH (the fallback {@link
 * ImageDimensionCommand} uses) and is skipped otherwise, matching this repo's other
 * ImageMagick-dependent tests (see {@link GenerateImageVariantsCommandTest}).
 * </p>
 *
 * @author SimIS Inc.
 */
class ValidateImageCommandTest {

  @Test
  void checkFileAcceptsAJpegAndRecordsItsDimensions(@TempDir Path tempDir) throws Exception {
    Image image = imageForRealFile(tempDir, "photo.jpg", 400, 300, "jpg");

    withStubbedRoot(tempDir, () -> ValidateImageCommand.checkFile(image));

    assertEquals(400, image.getWidth());
    assertEquals(300, image.getHeight());
    assertEquals("image/jpeg", image.getFileType());
  }

  @Test
  void checkFileAcceptsAWebpAndRecordsItsDimensions(@TempDir Path tempDir) throws Exception {
    Assumptions.assumeTrue(isImageMagickAvailable(), "ImageMagick is not on PATH - skipping WebP upload test");

    String relativePath = "images/2026/08/photo.webp";
    File webpFile = tempDir.resolve(relativePath).toFile();
    webpFile.getParentFile().mkdirs();
    buildRealWebpFixture(tempDir, webpFile, 400, 300);

    Image image = new Image();
    image.setFilename("photo.webp");
    image.setFileServerPath(relativePath);

    withStubbedRoot(tempDir, () -> ValidateImageCommand.checkFile(image));

    assertEquals(400, image.getWidth(), "a WebP upload must not be rejected -- this is the bug being fixed");
    assertEquals(300, image.getHeight());
  }

  @Test
  void checkFileReturnsSilentlyWhenTheFileDoesNotExist(@TempDir Path tempDir) throws Exception {
    Image image = new Image();
    image.setFilename("gone.png");
    image.setFileServerPath("images/2026/08/gone.png");

    // Must not throw -- checkFile's contract for a missing file is a silent no-op, unchanged by
    // the WebP fix.
    withStubbedRoot(tempDir, () -> ValidateImageCommand.checkFile(image));
  }

  @Test
  void checkFileThrowsDataExceptionForATextFileMasqueradingAsAnImage(@TempDir Path tempDir) throws Exception {
    String relativePath = "images/2026/08/fake.png";
    File fakeFile = tempDir.resolve(relativePath).toFile();
    fakeFile.getParentFile().mkdirs();
    Files.write(fakeFile.toPath(), "this is plain text, not a real PNG".getBytes());

    Image image = new Image();
    image.setFilename("fake.png");
    image.setFileServerPath(relativePath);

    assertThrows(DataException.class,
        () -> withStubbedRoot(tempDir, () -> ValidateImageCommand.checkFile(image)));
  }

  private static Image imageForRealFile(Path tempDir, String filename, int width, int height, String format)
      throws Exception {
    String relativePath = "images/2026/08/" + filename;
    File file = tempDir.resolve(relativePath).toFile();
    file.getParentFile().mkdirs();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file);

    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath(relativePath);
    return image;
  }

  /** Builds a real WebP file via a `convert` subprocess -- the JDK cannot write WebP either. */
  private static void buildRealWebpFixture(Path tempDir, File webpFile, int width, int height) throws Exception {
    File pngSource = tempDir.resolve("source-for-webp.png").toFile();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", pngSource);

    Process process = new ProcessBuilder("convert", pngSource.getAbsolutePath(), webpFile.getAbsolutePath())
        .redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("Could not build the WebP test fixture: " + output);
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static void withStubbedRoot(Path tempDir, ThrowingRunnable body) throws Exception {
    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, Answers.CALLS_REAL_METHODS)) {
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      body.run();
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
}
