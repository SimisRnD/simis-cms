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

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link DetectContentTypeCommand} reads a file's real format from its header bytes and
 * ignores the filename entirely (issue #1445).
 *
 * @author SimIS Inc.
 */
class DetectContentTypeCommandTest {

  @Test
  void detectReadsPngDataEvenWhenTheFilenameClaimsJpeg(@TempDir Path tempDir) throws Exception {
    // The exact shape reported in #1445: an editor's file named .jpg that is really PNG. Trusting
    // the extension here is what made the platform re-encode it as JPEG and lose its alpha.
    File misnamed = writeImage(tempDir, "gsa-alt.jpg", "png");

    assertEquals("image/png", DetectContentTypeCommand.detect(misnamed));
  }

  @Test
  void detectReadsJpegDataEvenWhenTheFilenameClaimsPng(@TempDir Path tempDir) throws Exception {
    File misnamed = writeImage(tempDir, "photo.png", "jpg");

    assertEquals("image/jpeg", DetectContentTypeCommand.detect(misnamed));
  }

  @Test
  void detectRecognizesEachAcceptedRasterFormat(@TempDir Path tempDir) throws Exception {
    assertEquals("image/png", DetectContentTypeCommand.detect(writeImage(tempDir, "a.png", "png")));
    assertEquals("image/jpeg", DetectContentTypeCommand.detect(writeImage(tempDir, "b.jpg", "jpg")));
    assertEquals("image/gif", DetectContentTypeCommand.detect(writeImage(tempDir, "c.gif", "gif")));
  }

  @Test
  void detectReturnsNullForAFileDisguisedAsAnImage(@TempDir Path tempDir) throws Exception {
    File disguised = tempDir.resolve("malware.png").toFile();
    Files.write(disguised.toPath(), "<?php echo 'not an image'; ?>".getBytes(StandardCharsets.UTF_8));

    assertNull(DetectContentTypeCommand.detect(disguised));
  }

  @Test
  void detectReturnsNullForAFileTooShortToCarryASignature(@TempDir Path tempDir) throws Exception {
    File truncated = tempDir.resolve("truncated.png").toFile();
    Files.write(truncated.toPath(), new byte[] { (byte) 0x89, 'P' });

    assertNull(DetectContentTypeCommand.detect(truncated));
  }

  @Test
  void detectReturnsNullRatherThanThrowingForAMissingOrNullFile(@TempDir Path tempDir) {
    assertNull(DetectContentTypeCommand.detect(null));
    assertNull(DetectContentTypeCommand.detect(tempDir.resolve("never-written.png").toFile()));
    assertNull(DetectContentTypeCommand.detect(tempDir.toFile()));
  }

  @Test
  void imageExtensionForMapsEveryReEncodableTypeAndNothingElse() {
    assertEquals("png", DetectContentTypeCommand.imageExtensionFor("image/png"));
    assertEquals("jpg", DetectContentTypeCommand.imageExtensionFor("image/jpeg"));
    assertEquals("gif", DetectContentTypeCommand.imageExtensionFor("image/gif"));
    assertEquals("webp", DetectContentTypeCommand.imageExtensionFor("image/webp"));
    // SVG is accepted as an image but never re-encoded, so it deliberately has no mapping.
    assertNull(DetectContentTypeCommand.imageExtensionFor("image/svg+xml"));
    assertNull(DetectContentTypeCommand.imageExtensionFor("application/pdf"));
    assertNull(DetectContentTypeCommand.imageExtensionFor(null));
  }

  private static File writeImage(Path tempDir, String filename, String format) throws Exception {
    File file = tempDir.resolve(filename).toFile();
    file.getParentFile().mkdirs();
    ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, file);
    return file;
  }
}
