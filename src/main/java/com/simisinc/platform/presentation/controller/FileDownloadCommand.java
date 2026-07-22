/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.controller;

import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

/**
 * Sets safe response headers when serving a user-uploaded file, so that uploaded content cannot execute
 * script in the application's own origin (stored XSS). Two things make direct-served uploads dangerous: a
 * browser MIME-sniffing a mislabeled file into HTML, and rendering an active type (HTML, SVG) inline. This
 * command closes both:
 *
 * <ul>
 *   <li><b>{@code X-Content-Type-Options: nosniff}</b> on every response — the browser honours the declared
 *       Content-Type instead of guessing, so an {@code application/octet-stream} can never be re-read as HTML.</li>
 *   <li><b>Inline only for an allow-list</b> of types that are safe to render (images except SVG, PDF, plain
 *       text, audio, video). Everything else — HTML, SVG, XML, and anything unrecognised — is sent as an
 *       {@code attachment} (downloaded, not rendered) and with a neutral content type.</li>
 *   <li><b>A sanitised, quoted {@code filename}</b> in the Content-Disposition header, so a crafted upload
 *       filename cannot inject additional headers or break out of the header value.</li>
 * </ul>
 *
 * @author SimIS Inc.
 */
public class FileDownloadCommand {

  // Content types that are safe to display inline. SVG is deliberately excluded (it is an image type but
  // can carry script), as are HTML/XML and anything not listed -- those are served as downloads instead.
  private static final Set<String> SAFE_INLINE_TYPES = Set.of(
      "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/bmp",
      "image/tiff", "image/x-icon", "image/vnd.microsoft.icon",
      "application/pdf", "text/plain");

  private FileDownloadCommand() {
    // Static utility
  }

  /**
   * Applies the safe download headers (nosniff, Content-Disposition, Content-Type) to the response.
   *
   * @param response      the servlet response
   * @param mimeType      the file's stored content type (may be null/blank)
   * @param filename      the file's stored name (may be null/blank)
   * @param requestInline true if the caller wants to display the file in the browser rather than download it;
   *                      honoured only for types on the safe-inline allow-list
   */
  public static void applyContentHeaders(HttpServletResponse response, String mimeType, String filename,
      boolean requestInline) {
    if (response == null) {
      return;
    }
    // Never let the browser second-guess the declared type.
    response.setHeader("X-Content-Type-Options", "nosniff");

    boolean inline = requestInline && isSafeToDisplayInline(mimeType);
    String disposition = inline ? "inline" : "attachment";
    String safeName = sanitizeFilename(filename);
    if (safeName != null) {
      response.setHeader("Content-Disposition", disposition + "; filename=\"" + safeName + "\"");
    } else {
      response.setHeader("Content-Disposition", disposition);
    }

    // Inline: serve the real (allow-listed, safe) type. Download: a neutral type so a mislabeled file
    // cannot be interpreted as active content even by a client that ignores nosniff + the attachment.
    response.setContentType(inline ? mimeType.trim() : "application/octet-stream");
  }

  /**
   * Headers for a resource that must render inline (e.g. an {@code <img>} source) yet might be an uploaded
   * SVG or HTML file: {@code nosniff} plus a sandbox Content-Security-Policy. The browser still paints the
   * image, but any script or outbound request the file carries is blocked -- so navigating directly to an
   * uploaded SVG/HTML cannot execute in this origin. Unlike {@link #applyContentHeaders} this does not force
   * a download, because forcing an attachment would break legitimate inline image embedding.
   *
   * @param response    the servlet response
   * @param contentType the file's stored content type (set as-is when present)
   */
  public static void applyInlineMediaHeaders(HttpServletResponse response, String contentType) {
    if (response == null) {
      return;
    }
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
    if (StringUtils.isNotBlank(contentType)) {
      response.setContentType(contentType.trim());
    }
  }

  /** True when a content type is on the allow-list of types safe to render inline in a browser. */
  static boolean isSafeToDisplayInline(String mimeType) {
    if (StringUtils.isBlank(mimeType)) {
      return false;
    }
    String type = mimeType.trim().toLowerCase();
    int semicolon = type.indexOf(';');
    if (semicolon > -1) {
      type = type.substring(0, semicolon).trim(); // drop any ";charset=..." parameter
    }
    if (SAFE_INLINE_TYPES.contains(type)) {
      return true;
    }
    // Streamed media plays inline safely; SVG does not qualify (it is handled above, not here).
    return type.startsWith("video/") || type.startsWith("audio/");
  }

  /**
   * Reduces a stored filename to a safe value for a quoted Content-Disposition filename token: strips any
   * path, control characters, CR/LF (header injection), and the quote/backslash that would break the token.
   */
  public static String sanitizeFilename(String filename) {
    if (StringUtils.isBlank(filename)) {
      return null;
    }
    String base = filename.replaceAll(".*[/\\\\]", "");   // keep only the base name (drop any path)
    base = base.replaceAll("[\\x00-\\x1f\\x7f\"\\\\]", ""); // control chars, CR/LF, quote, backslash
    base = base.trim();
    return base.isEmpty() ? null : base;
  }
}
