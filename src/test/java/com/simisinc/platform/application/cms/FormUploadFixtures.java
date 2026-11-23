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
