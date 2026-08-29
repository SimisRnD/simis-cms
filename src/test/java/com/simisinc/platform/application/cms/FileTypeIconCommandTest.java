/*
 * Copyright 2022 SimIS Inc.
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

import org.junit.jupiter.api.Test;

/**
 * Tests the file type to icon mapping shared by the public file list and the admin file browser.
 *
 * @author elizabeth houser
 */
class FileTypeIconCommandTest {

  @Test
  void everyTypeValidateFileCommandProducesGetsAnIcon() {
    // These are exactly the names getFileType() can return. Before issue 1582 four of them mapped
    // and the rest fell through, so a spreadsheet, a Word document and a slide deck all showed the
    // same blank page glyph.
    assertEquals("fa-file-pdf-o", FileTypeIconCommand.icon("PDF"));
    assertEquals("fa-file-excel-o", FileTypeIconCommand.icon("Spreadsheet"));
    assertEquals("fa-file-word-o", FileTypeIconCommand.icon("Document"));
    assertEquals("fa-file-powerpoint-o", FileTypeIconCommand.icon("Presentation"));
    assertEquals("fa-file-archive-o", FileTypeIconCommand.icon("Archive"));
    assertEquals("fa-file-code-o", FileTypeIconCommand.icon("Code"));
    assertEquals("fa-file-image-o", FileTypeIconCommand.icon("Image"));
    assertEquals("fa-file-video-o", FileTypeIconCommand.icon("Video"));
    assertEquals("fa-file-audio-o", FileTypeIconCommand.icon("Audio"));
    assertEquals("fa-file-text-o", FileTypeIconCommand.icon("Text"));
    assertEquals("fa-link", FileTypeIconCommand.icon("URL"));
  }

  @Test
  void matchingIgnoresCaseAndSurroundingSpace() {
    // getFileType() capitalises its own returns, but the value round-trips through the database
    // and a JSP, and the old c:choose blocks lower-cased before comparing for the same reason.
    assertEquals("fa-file-pdf-o", FileTypeIconCommand.icon("pdf"));
    assertEquals("fa-file-pdf-o", FileTypeIconCommand.icon("PdF"));
    assertEquals("fa-file-excel-o", FileTypeIconCommand.icon("  Spreadsheet  "));
  }

  @Test
  void aDiagramKeepsTheGenericIconRatherThanBorrowingAMisleadingOne() {
    // Font Awesome has no diagram glyph. The image icon would suggest a preview that no browser
    // will render for a Visio drawing, so the generic file icon is the honest answer.
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon("Diagram"));
  }

  @Test
  void anUnanticipatedTypeGetsTheGenericIcon() {
    // getFileType() falls back to capitalising the mime type's prefix, so the value space is open
    // ended -- an unmapped type is a normal outcome, not a bug.
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon("Application"));
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon("Font"));
  }

  @Test
  void aMissingTypeNeverReturnsNull() {
    // The JSPs render this straight into a class attribute; null would print the literal "null"
    // as a class name rather than failing visibly.
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon(null));
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon(""));
    assertEquals(FileTypeIconCommand.DEFAULT_ICON, FileTypeIconCommand.icon("   "));
    assertNotNull(FileTypeIconCommand.icon("anything at all"));
  }
}
