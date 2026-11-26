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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simisinc.platform.application.cms.SanitizeFormUploadCommand.Result;
import com.simisinc.platform.application.cms.SanitizeFormUploadCommand.Sanitized;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * Verifies {@link SanitizeFormUploadCommand}, which decodes an accepted form upload and encodes it
 * again so only its pixels are stored (issue #2130, step 3).
 */
class SanitizeFormUploadCommandTest {

  @TempDir
  Path quarantine;

  private static FormField screenshotField() {
    FormField field = new FormField();
    field.setLabel("Screenshot");
    field.setType("file");
    return field;
  }

  private File upload(String name, byte[] bytes) throws IOException {
    Path file = quarantine.resolve(name);
    Files.write(file, bytes);
    return file.toFile();
  }

  private static Sanitized accepted(Result result) {
    assertTrue(result.accepted(), "expected the image to be accepted, got: " + result.reason());
    assertNull(result.reason());
    assertNotNull(result.image());
    return result.image();
  }

  private static BufferedImage decode(byte[] bytes) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    assertNotNull(image, "the output must itself be a decodable image");
    return image;
  }

  private static boolean contains(byte[] haystack, String needle) {
    return contains(haystack, needle.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static boolean contains(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  // -- PNG ---------------------------------------------------------------------------------------

  @Test
  void aPngNamedJpgComesBackAsAPngWithItsTransparencyKept() throws IOException {
    // The #2130 acceptance case: the type check passed it as PNG, and re-encoding must not turn it
    // into a JPEG, which would make every transparent pixel solid
    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(),
        upload("screenshot.jpg", FormUploadFixtures.pngWithTransparency())));

    assertEquals("image/png", image.contentType());
    assertEquals("png", image.extension());
    BufferedImage pixels = decode(image.bytes());
    assertEquals(0, pixels.getRGB(0, 0) >>> 24, "the transparent half stays transparent");
    assertEquals(0xFFFF0000, pixels.getRGB(3, 0), "the opaque red half stays opaque red");
    assertEquals("image/png", image.thumbnailContentType(), "a transparent image gets a PNG thumbnail");
  }

  @Test
  void aPngLosesItsTextChunkAndAnythingAppendedAfterItsImageData() throws IOException {
    byte[] original = FormUploadFixtures.pngWithTextChunkAndTrailer();
    assertTrue(contains(original, "tEXt") && contains(original, "<script>"), "the fixture carries both");

    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(), upload("shot.png", original)));

    assertFalse(contains(image.bytes(), "tEXt"));
    assertFalse(contains(image.bytes(), "<script>"));
    assertFalse(contains(image.bytes(), "<!DOCTYPE"));
    byte[] end = Arrays.copyOfRange(image.bytes(), image.bytes().length - 8, image.bytes().length);
    assertArrayEquals(new byte[] { 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82 }, end,
        "the file ends with its IEND chunk");
  }

  @Test
  void anOpaquePngStaysAPngAndGetsAJpegThumbnail() throws IOException {
    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(),
        upload("diagram.png", FormUploadFixtures.opaque(50, 30, "png"))));

    assertEquals("image/png", image.contentType());
    assertEquals("image/jpeg", image.thumbnailContentType());
    assertEquals(50, image.width());
    assertEquals(30, image.height());
  }

  // -- JPEG --------------------------------------------------------------------------------------

  @Test
  void aJpegLosesItsExifAndCommentAndIsTurnedTheWayItsOrientationTagSaid() throws IOException {
    byte[] original = FormUploadFixtures.jpegWithExifAndComment();
    assertTrue(contains(original, "Exif") && contains(original, "<script>"), "the fixture carries both");

    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(), upload("photo.jpg", original)));

    assertEquals("image/jpeg", image.contentType());
    assertFalse(contains(image.bytes(), "Exif"), "EXIF, and any GPS position in it, is gone");
    assertFalse(contains(image.bytes(), "<script>"), "the comment is gone");
    // Stored 40 wide and 20 tall with "turn a quarter clockwise": the tag is gone, so the pixels
    // must be turned for real or the photo shows sideways
    assertEquals(20, image.width());
    assertEquals(40, image.height());
    BufferedImage pixels = decode(image.bytes());
    assertEquals(20, pixels.getWidth());
    assertEquals(40, pixels.getHeight());
  }

  @Test
  void aJpegLosesBytesAppendedAfterItsEndMarker() throws IOException {
    byte[] jpeg = FormUploadFixtures.opaque(16, 16, "jpg");
    byte[] polyglot = Arrays.copyOf(jpeg, jpeg.length + FormUploadFixtures.html().length);
    System.arraycopy(FormUploadFixtures.html(), 0, polyglot, jpeg.length, FormUploadFixtures.html().length);

    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(), upload("photo.jpg", polyglot)));

    assertFalse(contains(image.bytes(), "<script>"));
    int last = image.bytes().length;
    assertEquals((byte) 0xFF, image.bytes()[last - 2]);
    assertEquals((byte) 0xD9, image.bytes()[last - 1], "the file ends with its end-of-image marker");
  }

  // -- Thumbnail ---------------------------------------------------------------------------------

  @Test
  void theThumbnailIsAtMost320WideAndKeepsTheShape() throws IOException {
    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(),
        upload("wide.jpg", FormUploadFixtures.opaque(1000, 500, "jpg"))));

    BufferedImage thumbnail = decode(image.thumbnail());
    assertEquals(SanitizeFormUploadCommand.THUMBNAIL_WIDTH, thumbnail.getWidth());
    assertEquals(160, thumbnail.getHeight());
    assertEquals(1000, image.width(), "the stored image keeps its full size");
  }

  @Test
  void aNarrowImagesThumbnailIsNotEnlarged() throws IOException {
    Sanitized image = accepted(SanitizeFormUploadCommand.sanitize(screenshotField(),
        upload("small.jpg", FormUploadFixtures.opaque(100, 50, "jpg"))));

    BufferedImage thumbnail = decode(image.thumbnail());
    assertEquals(100, thumbnail.getWidth());
    assertEquals(50, thumbnail.getHeight());
  }

  // -- Rejections --------------------------------------------------------------------------------

  @Test
  void aTruncatedImageIsRejectedNotStoredAsItCame() throws IOException {
    Result result = SanitizeFormUploadCommand.sanitize(screenshotField(), upload("broken.png", FormUploadFixtures.truncatedPng()));

    assertFalse(result.accepted());
    assertEquals(SanitizeFormUploadCommand.REASON_UNDECODABLE, result.reason());
    assertEquals("Screenshot: the image couldn't be opened. Try saving it again, or choose another image.", result.message());
    assertNull(result.image());
  }

  @Test
  void anImageClaimingTooManyPixelsIsRejectedFromItsHeaderAlone() throws IOException {
    // 20,000 x 20,000 is 400 megapixels: about 1.6 GB decoded, from a file of a few dozen bytes
    Result result = SanitizeFormUploadCommand.sanitize(screenshotField(), upload("bomb.png", FormUploadFixtures.pngDeclaring(20_000, 20_000)));

    assertFalse(result.accepted());
    assertEquals(SanitizeFormUploadCommand.REASON_TOO_MANY_PIXELS, result.reason());
    assertEquals("Screenshot: the image has too many pixels. Choose a smaller image.", result.message());
  }

  @Test
  void contentThatIsNotAJpegOrPngIsRejectedWhateverTheCallerBelieved() throws IOException {
    // The type is detected again from the file on disk, not taken from step 2's answer
    assertEquals(SanitizeFormUploadCommand.REASON_UNDECODABLE,
        SanitizeFormUploadCommand.sanitize(screenshotField(), upload("logo.png", FormUploadFixtures.svg())).reason());
    assertEquals(SanitizeFormUploadCommand.REASON_UNDECODABLE,
        SanitizeFormUploadCommand.sanitize(screenshotField(), upload("animation.png", FormUploadFixtures.gif())).reason());
    assertEquals(SanitizeFormUploadCommand.REASON_UNDECODABLE,
        SanitizeFormUploadCommand.sanitize(screenshotField(), new File(quarantine.toFile(), "missing.png")).reason());
  }

  @Test
  void anUploadWaitingTooLongForAnotherDecodeIsTurnedAwayRatherThanQueuedForever() throws Exception {
    long wait = SanitizeFormUploadCommand.decodeWaitMillis;
    DownscaleImageCommand.DECODE_PERMIT.acquire();
    try {
      SanitizeFormUploadCommand.decodeWaitMillis = 10;

      Result result = SanitizeFormUploadCommand.sanitize(screenshotField(), upload("shot.png", FormUploadFixtures.pngWithTransparency()));

      assertEquals(SanitizeFormUploadCommand.REASON_BUSY, result.reason());
      assertEquals("Screenshot: the image couldn't be processed right now. Try again in a minute.", result.message());
    } finally {
      SanitizeFormUploadCommand.decodeWaitMillis = wait;
      DownscaleImageCommand.DECODE_PERMIT.release();
    }
    assertEquals(1, DownscaleImageCommand.DECODE_PERMIT.availablePermits(), "the permit is not leaked");
  }

  @Test
  void theDecodePermitIsReturnedAfterARejectionToo() throws IOException {
    SanitizeFormUploadCommand.sanitize(screenshotField(), upload("broken.png", FormUploadFixtures.truncatedPng()));

    assertEquals(1, DownscaleImageCommand.DECODE_PERMIT.availablePermits());
  }

  @Test
  void aWebpIsRefusedHereEvenIfTheTypeCheckLetItThrough() throws IOException {
    // WebP is off the accepted list so no upload reaches ImageMagick and libwebp; this is the second
    // gate, in case it is ever put back on the list without its re-encode
    assertEquals(SanitizeFormUploadCommand.REASON_UNDECODABLE,
        SanitizeFormUploadCommand.sanitize(screenshotField(), upload("phone.webp", FormUploadFixtures.webpHeader())).reason());
  }
}
