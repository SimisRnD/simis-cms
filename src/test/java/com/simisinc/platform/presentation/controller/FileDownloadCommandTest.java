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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

/**
 * Tests that the file-download headers keep uploaded content from executing in the application's origin:
 * nosniff is always set, only allow-listed types are served inline, everything else is a neutral-typed
 * download, and the Content-Disposition filename is sanitised.
 *
 * @author SimIS Inc.
 */
class FileDownloadCommandTest {

  @Test
  void safeTypesAreInlineable() {
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("image/png"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("image/jpeg"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("application/pdf"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("text/plain"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("video/mp4"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("audio/mpeg"));
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("IMAGE/PNG"), "case-insensitive");
    assertTrue(FileDownloadCommand.isSafeToDisplayInline("text/plain; charset=utf-8"), "charset param ignored");
  }

  @Test
  void activeAndUnknownTypesAreNotInlineable() {
    assertFalse(FileDownloadCommand.isSafeToDisplayInline("text/html"));
    assertFalse(FileDownloadCommand.isSafeToDisplayInline("image/svg+xml"), "SVG can carry script");
    assertFalse(FileDownloadCommand.isSafeToDisplayInline("application/xhtml+xml"));
    assertFalse(FileDownloadCommand.isSafeToDisplayInline("application/xml"));
    assertFalse(FileDownloadCommand.isSafeToDisplayInline("application/octet-stream"));
    assertFalse(FileDownloadCommand.isSafeToDisplayInline(""));
    assertFalse(FileDownloadCommand.isSafeToDisplayInline(null));
  }

  @Test
  void sanitizeFilenameStripsPathControlCharsAndQuotes() {
    assertNull(FileDownloadCommand.sanitizeFilename(null));
    assertNull(FileDownloadCommand.sanitizeFilename("   "));
    // Path is dropped, base name kept
    assertEquals("report.pdf", FileDownloadCommand.sanitizeFilename("/var/uploads/../report.pdf"));
    assertEquals("report.pdf", FileDownloadCommand.sanitizeFilename("C:\\files\\report.pdf"));
    // CR/LF (header injection), quote and backslash are removed
    String cleaned = FileDownloadCommand.sanitizeFilename("evil\r\nSet-Cookie: x=1\\\".html");
    assertFalse(cleaned.contains("\r"), "CR removed");
    assertFalse(cleaned.contains("\n"), "LF removed");
    assertFalse(cleaned.contains("\""), "quote removed");
    assertFalse(cleaned.contains("\\"), "backslash removed");
  }

  @Test
  void inlineForSafeTypeSetsNosniffAndInlineDisposition() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FileDownloadCommand.applyContentHeaders(response, "image/png", "photo.png", true);
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    verify(response).setHeader("Content-Disposition", "inline; filename=\"photo.png\"");
    verify(response).setContentType("image/png");
  }

  @Test
  void dangerousTypeIsForcedToNeutralDownloadEvenWhenViewRequested() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FileDownloadCommand.applyContentHeaders(response, "text/html", "page.html", true);
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"page.html\"");
    verify(response).setContentType("application/octet-stream");
  }

  @Test
  void svgIsForcedToDownloadInTheDownloadPath() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FileDownloadCommand.applyContentHeaders(response, "image/svg+xml", "logo.svg", true);
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"logo.svg\"");
    verify(response).setContentType("application/octet-stream");
  }

  @Test
  void notViewingAlwaysDownloads() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FileDownloadCommand.applyContentHeaders(response, "image/png", "photo.png", false);
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"photo.png\"");
    verify(response).setContentType("application/octet-stream");
  }

  @Test
  void inlineMediaHeadersSetNosniffAndSandboxCsp() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    FileDownloadCommand.applyInlineMediaHeaders(response, "image/svg+xml");
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    verify(response).setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
    verify(response).setContentType("image/svg+xml");
  }

  @Test
  void nullResponseIsIgnored() {
    FileDownloadCommand.applyContentHeaders(null, "image/png", "photo.png", true);
    FileDownloadCommand.applyInlineMediaHeaders(null, "image/png");
  }
}
