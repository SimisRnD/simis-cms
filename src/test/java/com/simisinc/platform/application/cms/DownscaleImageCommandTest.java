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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Infographics exported at about 3,700px wide reached the library as 2 to 4.5 MB PNGs for a page
 * that shows them at about 1,000px. These pin what {@link DownscaleImageCommand} keeps, converts,
 * and leaves alone.
 *
 * @author SimIS Inc.
 */
class DownscaleImageCommandTest {

  /** Busy enough that the PNG is large and the JPEG has real detail to keep, like an infographic. */
  private static BufferedImage infographic(int width, int height, int type) {
    BufferedImage image = new BufferedImage(width, height, type);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    Random random = new Random(1197);
    for (int i = 0; i < 400; i++) {
      g.setColor(new Color(random.nextInt(0xFFFFFF)));
      g.fillRect(random.nextInt(width), random.nextInt(height), 20 + random.nextInt(400), 20 + random.nextInt(200));
      g.setColor(Color.BLACK);
      g.drawString("Human Type Targets " + i, random.nextInt(width), random.nextInt(height));
    }
    g.dispose();
    return image;
  }

  private static File write(Path dir, String name, BufferedImage image, String format) throws IOException {
    File file = dir.resolve(name).toFile();
    assertTrue(ImageIO.write(image, format, file));
    return file;
  }

  private static byte[] jpegBytes(BufferedImage image, float quality) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.setOutput(out);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  private static BufferedImage decode(byte[] bytes) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    assertNotNull(image, "the result must be a file ImageIO can read back");
    return image;
  }

  @Test
  void aLargeOpaquePngBecomesAJpegAt1600Wide(@TempDir Path dir) throws Exception {
    File png = write(dir, "infographic.png", infographic(3700, 2000, BufferedImage.TYPE_INT_RGB), "png");

    DownscaleImageCommand.Downscaled result = DownscaleImageCommand.downscaleIfTooWide(png);

    assertNotNull(result);
    assertEquals("image/jpeg", result.fileType());
    assertEquals("jpg", result.extension());
    assertEquals(1600, result.width());
    assertEquals(865, result.height(), "the aspect ratio is kept: 2000 * 1600 / 3700");
    assertTrue(result.bytes().length < png.length(), png.length() + " bytes became " + result.bytes().length);
    BufferedImage written = decode(result.bytes());
    assertEquals(1600, written.getWidth());
    assertEquals(865, written.getHeight());
  }

  @Test
  void anOpaquePngWithAnAlphaChannelStillBecomesAJpeg(@TempDir Path dir) throws Exception {
    // Design tools export a fully opaque infographic as RGBA; the channel being there is not
    // transparency
    File png = write(dir, "exported.png", infographic(3700, 2000, BufferedImage.TYPE_INT_ARGB), "png");

    DownscaleImageCommand.Downscaled result = DownscaleImageCommand.downscaleIfTooWide(png);

    assertNotNull(result);
    assertEquals("image/jpeg", result.fileType());
  }

  @Test
  void aTransparentPngStaysAPngAndKeepsItsTransparency(@TempDir Path dir) throws Exception {
    BufferedImage logo = infographic(3200, 1000, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = logo.createGraphics();
    g.setComposite(java.awt.AlphaComposite.Clear);
    g.fillRect(0, 0, 800, 1000);
    g.dispose();
    File png = write(dir, "logo.png", logo, "png");

    DownscaleImageCommand.Downscaled result = DownscaleImageCommand.downscaleIfTooWide(png);

    assertNotNull(result);
    assertEquals("image/png", result.fileType());
    assertEquals(1600, result.width());
    assertEquals(500, result.height());
    BufferedImage written = decode(result.bytes());
    assertEquals(0, written.getRGB(100, 250) >>> 24, "the cleared quarter must still be transparent, not filled in");
    assertEquals(0xFF, written.getRGB(1200, 250) >>> 24);
  }

  @Test
  void aSmallImagePassesThroughUntouched(@TempDir Path dir) throws Exception {
    File png = write(dir, "chart.png", infographic(1600, 900, BufferedImage.TYPE_INT_RGB), "png");
    assertNull(DownscaleImageCommand.downscaleIfTooWide(png), "1600px is already the target; nothing to change");
  }

  @Test
  void aWideJpegIsResizedAndReEncodedAsJpeg(@TempDir Path dir) throws Exception {
    File jpeg = dir.resolve("photo.jpg").toFile();
    Files.write(jpeg.toPath(), jpegBytes(infographic(2400, 1600, BufferedImage.TYPE_INT_RGB), 0.95f));

    DownscaleImageCommand.Downscaled result = DownscaleImageCommand.downscaleIfTooWide(jpeg);

    assertNotNull(result);
    assertEquals("image/jpeg", result.fileType());
    assertEquals(1600, result.width());
    assertEquals(1067, result.height());
  }

  @Test
  void aJpegThatWouldComeOutLargerIsKept(@TempDir Path dir) throws Exception {
    // Noise saved at very low quality is small on disk; at 0.85 the same pixels cost far more
    BufferedImage noise = new BufferedImage(1700, 1700, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(411);
    for (int y = 0; y < noise.getHeight(); y++) {
      for (int x = 0; x < noise.getWidth(); x++) {
        noise.setRGB(x, y, random.nextInt(0xFFFFFF));
      }
    }
    File jpeg = dir.resolve("noise.jpg").toFile();
    Files.write(jpeg.toPath(), jpegBytes(noise, 0.05f));

    assertNull(DownscaleImageCommand.downscaleIfTooWide(jpeg), "a bigger file is not an improvement");
  }

  @Test
  void aFileThatIsNotAnImageIsLeftAlone(@TempDir Path dir) throws Exception {
    File text = dir.resolve("notes.png").toFile();
    Files.write(text.toPath(), "not an image".getBytes(StandardCharsets.UTF_8));
    assertNull(DownscaleImageCommand.downscaleIfTooWide(text));
  }

  @Test
  void anImageTooLargeToDecodeSafelyIsLeftAlone(@TempDir Path dir) throws Exception {
    // A few dozen bytes that declare 20000x20000 pixels: decoding would need 1.6 GB. The header
    // alone has to be enough to refuse it.
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
    png.write(chunk("IHDR", ByteBuffer.allocate(13).putInt(20000).putInt(20000)
        .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array()));
    png.write(chunk("IEND", new byte[0]));
    File file = dir.resolve("bomb.png").toFile();
    Files.write(file.toPath(), png.toByteArray());

    assertNull(DownscaleImageCommand.downscaleIfTooWide(file));
  }

  @Test
  void anAnimatedPngIsLeftAlone(@TempDir Path dir) throws Exception {
    byte[] still = Files.readAllBytes(write(dir, "still.png", infographic(2000, 200, BufferedImage.TYPE_INT_RGB), "png").toPath());
    // acTL goes straight after IHDR (8-byte signature + 25-byte IHDR chunk)
    ByteArrayOutputStream animated = new ByteArrayOutputStream();
    animated.write(still, 0, 33);
    animated.write(chunk("acTL", ByteBuffer.allocate(8).putInt(2).putInt(0).array()));
    animated.write(still, 33, still.length - 33);
    File file = dir.resolve("animated.png").toFile();
    Files.write(file.toPath(), animated.toByteArray());

    assertTrue(DownscaleImageCommand.isAnimatedPng(file));
    assertNull(DownscaleImageCommand.downscaleIfTooWide(file), "ImageIO would keep only the first frame");
  }

  private static byte[] chunk(String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
        .putInt((int) crc.getValue()).array();
  }

  /**
   * A camera JPEG stored sideways with an EXIF orientation tag. Re-encoding drops the tag, so the
   * pixels must come out turned the way a browser would have displayed them. The stored image has
   * a red block at its top-left and a blue block at its top-right; the expected corners are where
   * each one is displayed for that orientation.
   */
  @ParameterizedTest
  @CsvSource({
      "1, TL, TR",
      "2, TR, TL",
      "3, BR, BL",
      "4, BL, BR",
      "5, TL, BL",
      "6, TR, BR",
      "7, BR, TR",
      "8, BL, TL",
  })
  void aJpegIsTurnedTheWayItsExifOrientationSays(int orientation, String redCorner, String blueCorner,
      @TempDir Path dir) throws Exception {
    BufferedImage stored = new BufferedImage(2400, 1800, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = stored.createGraphics();
    g.setColor(Color.GRAY);
    g.fillRect(0, 0, 2400, 1800);
    g.setColor(Color.RED);
    g.fillRect(0, 0, 300, 300);
    g.setColor(Color.BLUE);
    g.fillRect(2100, 0, 300, 300);
    g.dispose();
    File jpeg = dir.resolve("camera.jpg").toFile();
    Files.write(jpeg.toPath(), withExifOrientation(jpegBytes(stored, 0.95f), orientation));
    assertEquals(orientation, DownscaleImageCommand.readExifOrientation(jpeg));

    DownscaleImageCommand.Downscaled result = DownscaleImageCommand.downscaleIfTooWide(jpeg);

    assertNotNull(result, "displayed at least 1800px wide either way, so it is downscaled");
    boolean turned = orientation >= 5;
    assertEquals(1600, result.width());
    assertEquals(turned ? 2133 : 1200, result.height());
    BufferedImage written = decode(result.bytes());
    assertColorAt(written, redCorner, Color.RED, orientation);
    assertColorAt(written, blueCorner, Color.BLUE, orientation);
  }

  private static void assertColorAt(BufferedImage image, String corner, Color expected, int orientation) {
    int inset = 60;
    int x = corner.endsWith("L") ? inset : image.getWidth() - 1 - inset;
    int y = corner.startsWith("T") ? inset : image.getHeight() - 1 - inset;
    Color actual = new Color(image.getRGB(x, y));
    boolean matches = expected.equals(Color.RED)
        ? actual.getRed() > 200 && actual.getBlue() < 60
        : actual.getBlue() > 200 && actual.getRed() < 60;
    assertTrue(matches, "orientation " + orientation + ": expected " + expected + " at " + corner + ", found " + actual);
  }

  /**
   * Splices a minimal big-endian EXIF APP1 segment, holding only the orientation tag, after the
   * JFIF APP0 segment the JDK writer puts first.
   */
  private static byte[] withExifOrientation(byte[] jpeg, int orientation) throws IOException {
    ByteBuffer tiff = ByteBuffer.allocate(26);
    tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
    tiff.putInt(0);
    byte[] exif = new byte[6 + tiff.capacity()];
    System.arraycopy(new byte[] { 'E', 'x', 'i', 'f', 0, 0 }, 0, exif, 0, 6);
    System.arraycopy(tiff.array(), 0, exif, 6, tiff.capacity());

    int insertAt = 2;
    if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
      insertAt += 2 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, insertAt);
    out.write(0xFF);
    out.write(0xE1);
    out.write((exif.length + 2) >> 8);
    out.write((exif.length + 2) & 0xFF);
    out.write(exif);
    out.write(jpeg, insertAt, jpeg.length - insertAt);
    return out.toByteArray();
  }
}
