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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/**
 * The files a visitor might attach to a form (issue #2130), built in memory so no binary fixtures are
 * checked in. The PNG, JPEG and GIF are real, decodable images, so the later re-encode step can reuse
 * them. The WebP is only a valid header: the JDK has no WebP encoder, and the type check reads no
 * further than the header.
 */
final class FormUploadFixtures {

  private FormUploadFixtures() {
  }

  /** A real 4x4 PNG whose left half is fully transparent */
  static byte[] pngWithTransparency() {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    for (int x = 2; x < 4; x++) {
      for (int y = 0; y < 4; y++) {
        image.setRGB(x, y, Color.RED.getRGB());
      }
    }
    return encode(image, "png");
  }

  static byte[] jpeg() {
    return encode(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpg");
  }

  static byte[] gif() {
    return encode(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "gif");
  }

  /** "RIFF", a chunk size, "WEBP", then the start of a VP8L chunk */
  static byte[] webpHeader() {
    return new byte[] { 'R', 'I', 'F', 'F', 0x1A, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'L', 0x0D, 0, 0, 0 };
  }

  static byte[] svg() {
    return text("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>");
  }

  static byte[] html() {
    return text("<!DOCTYPE html><html><body><script>alert(1)</script></body></html>");
  }

  static byte[] pdf() {
    return text("%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n");
  }

  /** The start of a Windows executable: "MZ", then padding */
  static byte[] exe() {
    byte[] bytes = new byte[64];
    bytes[0] = 'M';
    bytes[1] = 'Z';
    return bytes;
  }

  // -- Re-encode fixtures (step 3): real images carrying things only the bytes, not the pixels, hold

  /** A real opaque image of the given size and format ("png" or "jpg"), filled with one color */
  static byte[] opaque(int width, int height, String format) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        image.setRGB(x, y, Color.BLUE.getRGB());
      }
    }
    return encode(image, format);
  }

  /**
   * A real JPEG, 40 wide and 20 tall, carrying an EXIF segment whose orientation tag says to turn it
   * a quarter clockwise (6), and a comment segment holding script
   */
  static byte[] jpegWithExifAndComment() {
    byte[] jpeg = opaque(40, 20, "jpg");
    // EXIF: "Exif\0\0", a big-endian TIFF header, then one IFD entry: tag 0x0112, SHORT, count 1, 6
    byte[] exif = { 'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8,
        0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0 };
    byte[] comment = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2); // SOI
    writeSegment(out, 0xE1, exif);
    writeSegment(out, 0xFE, comment);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  /** A real PNG with transparency, a tEXt chunk holding script, and HTML appended after IEND */
  static byte[] pngWithTextChunkAndTrailer() {
    byte[] png = pngWithTransparency();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    // The 8-byte signature and the 25-byte IHDR chunk come first
    out.write(png, 0, 33);
    writeChunk(out, "tEXt", "Comment\0<script>alert(1)</script>".getBytes(StandardCharsets.ISO_8859_1));
    out.write(png, 33, png.length - 33);
    byte[] trailer = html();
    out.write(trailer, 0, trailer.length);
    return out.toByteArray();
  }

  /** A real 1x1 PNG whose header is rewritten to claim the given dimensions */
  static byte[] pngDeclaring(int width, int height) {
    byte[] png = opaque(1, 1, "png");
    java.nio.ByteBuffer.wrap(png).putInt(16, width).putInt(20, height);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(png, 12, 17);
    java.nio.ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
    return png;
  }

  /** The first half of a real PNG: a valid header, then nothing to decode */
  static byte[] truncatedPng() {
    byte[] png = opaque(64, 64, "png");
    return java.util.Arrays.copyOf(png, png.length / 2);
  }

  private static void writeSegment(ByteArrayOutputStream out, int marker, byte[] data) {
    int length = data.length + 2;
    out.write(0xFF);
    out.write(marker);
    out.write(length >> 8);
    out.write(length & 0xFF);
    out.write(data, 0, data.length);
  }

  private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(typeBytes);
    crc.update(data);
    byte[] header = java.nio.ByteBuffer.allocate(8).putInt(data.length).put(typeBytes).array();
    out.write(header, 0, header.length);
    out.write(data, 0, data.length);
    byte[] crcBytes = java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array();
    out.write(crcBytes, 0, crcBytes.length);
  }

  private static byte[] text(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] encode(BufferedImage image, String format) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("No ImageIO writer for " + format);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
