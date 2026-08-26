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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Determines a file's real content type by inspecting its header bytes ("magic bytes") rather than
 * trusting its name.
 *
 * <p>
 * {@code Files.probeContentType()} is not a substitute. On a minimal JDK or container with no
 * mime-magic database it silently falls back to guessing from the filename extension, so PNG data
 * named {@code photo.jpg} is reported as {@code image/jpeg}. That is wrong in two different ways
 * that both matter here: it lets a disguised file be accepted and served back under a type it is
 * not, and it makes the platform re-encode an image into a format its bytes never were -- which is
 * how a transparent PNG named {@code .jpg} ended up composited onto solid black in every generated
 * variant (issue #1445).
 * </p>
 *
 * <p>
 * Only the formats the platform actually accepts are recognized. Anything else returns {@code null}
 * so the caller decides whether to reject outright or fall back to a weaker check -- this command
 * never guesses.
 * </p>
 *
 * @author SimIS Inc.
 */
public class DetectContentTypeCommand {

  private static final Log LOG = LogFactory.getLog(DetectContentTypeCommand.class);

  private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
  private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
  private static final byte[] GIF87A_SIGNATURE = { 'G', 'I', 'F', '8', '7', 'a' };
  private static final byte[] GIF89A_SIGNATURE = { 'G', 'I', 'F', '8', '9', 'a' };
  private static final byte[] PDF_SIGNATURE = { '%', 'P', 'D', 'F', '-' };
  // WebP is a RIFF container: bytes 0-3 are "RIFF", bytes 4-7 are a little-endian chunk size
  // specific to each file (not checked here), bytes 8-11 are "WEBP".
  private static final byte[] RIFF_SIGNATURE = { 'R', 'I', 'F', 'F' };
  private static final byte[] WEBP_SIGNATURE = { 'W', 'E', 'B', 'P' };
  private static final int SNIFF_HEADER_BYTES = 12;

  private DetectContentTypeCommand() {
    // Static utility, not instantiated
  }

  /**
   * Detects a file's real content type from its header bytes on disk -- never the filename, its
   * extension, or a client-declared content type.
   *
   * @param file the file to inspect
   * @return one of {@code image/png}, {@code image/jpeg}, {@code image/gif}, {@code image/webp},
   *         {@code application/pdf}, or {@code null} when the header matches none of them
   */
  public static String detect(File file) {
    if (file == null || !file.isFile()) {
      return null;
    }
    byte[] header = new byte[SNIFF_HEADER_BYTES];
    int headerLength;
    try (InputStream in = Files.newInputStream(file.toPath())) {
      headerLength = in.readNBytes(header, 0, header.length);
    } catch (Exception e) {
      LOG.warn("Could not read the file's header bytes: " + e.getMessage());
      return null;
    }
    if (headerStartsWith(header, headerLength, PNG_SIGNATURE)) {
      return "image/png";
    }
    if (headerStartsWith(header, headerLength, JPEG_SIGNATURE)) {
      return "image/jpeg";
    }
    if (headerStartsWith(header, headerLength, GIF87A_SIGNATURE)
        || headerStartsWith(header, headerLength, GIF89A_SIGNATURE)) {
      return "image/gif";
    }
    if (headerStartsWith(header, headerLength, PDF_SIGNATURE)) {
      return "application/pdf";
    }
    if (headerLength >= SNIFF_HEADER_BYTES && headerStartsWith(header, headerLength, RIFF_SIGNATURE)
        && header[8] == WEBP_SIGNATURE[0] && header[9] == WEBP_SIGNATURE[1]
        && header[10] == WEBP_SIGNATURE[2] && header[11] == WEBP_SIGNATURE[3]) {
      return "image/webp";
    }
    return null;
  }

  /**
   * Maps a detected image content type to the filename extension that encodes it, so a generated
   * file is named for what it actually contains. Returns {@code null} for anything that is not one
   * of the raster image types this platform re-encodes, leaving the caller to decide.
   *
   * @param contentType a content type, typically from {@link #detect(File)}
   * @return the extension without a leading dot, or {@code null} when unrecognized
   */
  public static String imageExtensionFor(String contentType) {
    if (contentType == null) {
      return null;
    }
    switch (contentType) {
      case "image/png":
        return "png";
      case "image/jpeg":
        return "jpg";
      case "image/gif":
        return "gif";
      case "image/webp":
        return "webp";
      default:
        return null;
    }
  }

  private static boolean headerStartsWith(byte[] header, int headerLength, byte[] signature) {
    if (headerLength < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (header[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
