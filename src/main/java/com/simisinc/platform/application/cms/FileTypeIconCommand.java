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

import org.apache.commons.lang3.StringUtils;

/**
 * The Font Awesome class for a stored file's type.
 *
 * <p>
 * {@link ValidateFileCommand#getFileType(String, String)} classifies an upload into one of eleven
 * or so names -- PDF, Archive, Code, Document, Spreadsheet, Diagram, Presentation, plus the Image,
 * Video, Audio and Text forms taken from the mime type's prefix, and URL for a link entry. Both
 * places that showed an icon recognised four of them, so a spreadsheet, a Word document and a
 * slide deck were indistinguishable from each other and from an unknown file (issue 1582).
 * </p>
 *
 * <p>
 * The mapping lives here rather than in either JSP because it was written twice and drifted: the
 * public file list and the admin file browser carried near-identical {@code c:choose} blocks, and
 * a type added to one would not appear in the other. One source, two callers.
 * </p>
 *
 * <p>
 * The value space is open-ended by design -- {@code getFileType} falls back to capitalising the
 * mime type's prefix for anything it does not recognise, so a type nobody anticipated is a normal
 * outcome rather than a bug, and reaches the generic file icon.
 * </p>
 */
public class FileTypeIconCommand {

  /** The icon for a type that does not match, and for a null or blank one */
  public static final String DEFAULT_ICON = "fa-file-o";

  private FileTypeIconCommand() {
    // Static utility, not instantiated
  }

  /**
   * The Font Awesome class name for a file type.
   *
   * <p>
   * The 4-style names are kept deliberately. The bundled Font Awesome 6 still carries them through
   * its compatibility shim, every one used here was confirmed present, and switching styles would
   * mean changing icons across both listings for no gain the reader can see.
   * </p>
   *
   * @param fileType a {@code FileItem} type, matched without regard to case
   * @return a Font Awesome class name, never null
   */
  public static String icon(String fileType) {
    if (StringUtils.isBlank(fileType)) {
      return DEFAULT_ICON;
    }
    switch (fileType.trim().toLowerCase()) {
      case "pdf":
        return "fa-file-pdf-o";
      case "spreadsheet":
        return "fa-file-excel-o";
      case "document":
        return "fa-file-word-o";
      case "presentation":
        return "fa-file-powerpoint-o";
      case "archive":
        return "fa-file-archive-o";
      case "code":
        return "fa-file-code-o";
      case "image":
        return "fa-file-image-o";
      case "video":
        return "fa-file-video-o";
      case "audio":
        return "fa-file-audio-o";
      case "text":
        return "fa-file-text-o";
      // A Visio drawing. Font Awesome has no diagram-specific file glyph, and the generic image
      // one would say the wrong thing about a file no browser will preview, so it keeps the
      // default rather than borrowing a glyph that misleads.
      case "diagram":
        return DEFAULT_ICON;
      case "url":
        return "fa-link";
      default:
        return DEFAULT_ICON;
    }
  }
}
