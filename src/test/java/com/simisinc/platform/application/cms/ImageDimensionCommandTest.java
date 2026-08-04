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

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link ImageDimensionCommand} reads dimensions via the fast {@code javax.imageio}
 * path for formats the JDK can decode, and falls back to ImageMagick's {@code identify} for
 * formats it can't (WebP) -- issue #931. The WebP-specific tests require a real ImageMagick
 * binary on PATH and are skipped otherwise (this repo's ImageMagick-dependent tests all follow
 * this pattern; see {@link GenerateImageVariantsCommandTest}).
 *
 * @author SimIS Inc.
 */
class ImageDimensionCommandTest {

  @Test
  void readDimensionUsesTheFastImageIoPathForPng(@TempDir Path tempDir) throws Exception {
    File pngFile = tempDir.resolve("sample.png").toFile();
    ImageIO.write(new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB), "png", pngFile);

    Dimension dimension = ImageDimensionCommand.readDimension(pngFile);

    assertEquals(320, dimension.width);
    assertEquals(240, dimension.height);
  }

  @Test
  void readDimensionFallsBackToImageMagickForAWebpFile(@TempDir Path tempDir) throws Exception {
    Assumptions.assumeTrue(isImageMagickAvailable(), "ImageMagick is not on PATH - skipping WebP dimension test");

    File webpFile = buildRealWebpFixture(tempDir, 320, 240);

    Dimension dimension = ImageDimensionCommand.readDimension(webpFile);

    assertEquals(320, dimension.width, "the JDK's own ImageIO cannot decode WebP -- this must come from the identify fallback");
    assertEquals(240, dimension.height);
  }

  @Test
  void readDimensionThrowsIoExceptionForAFileThatIsNotAnImage(@TempDir Path tempDir) throws Exception {
    File notAnImage = tempDir.resolve("not-an-image.png").toFile();
    Files.write(notAnImage.toPath(), "this is plain text, not a PNG".getBytes());

    assertThrows(IOException.class, () -> ImageDimensionCommand.readDimension(notAnImage));
  }

  @Test
  void readDimensionThrowsIoExceptionForAFileWithNoRecognizedExtension(@TempDir Path tempDir) throws Exception {
    File noExtension = tempDir.resolve("mystery-file").toFile();
    Files.write(noExtension.toPath(), "not an image at all".getBytes());

    assertThrows(IOException.class, () -> ImageDimensionCommand.readDimension(noExtension));
  }

  /** Builds a real WebP file via a `convert`/`cwebp` subprocess -- the JDK cannot write WebP either. */
  private static File buildRealWebpFixture(Path tempDir, int width, int height) throws Exception {
    File pngSource = tempDir.resolve("source-for-webp.png").toFile();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", pngSource);
    File webpFile = tempDir.resolve("sample.webp").toFile();

    Process process = new ProcessBuilder("convert", pngSource.getAbsolutePath(), webpFile.getAbsolutePath())
        .redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("Could not build the WebP test fixture: " + output);
    }
    return webpFile;
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
