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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.Image;

/**
 * The string rules the library backfill rewrites stored content with. Page XML holds the same image
 * as both {@code My%20Image.png} and {@code My Image.png}; every spelling has to be switched, and
 * nothing that merely resembles the image may be.
 *
 * @author SimIS Inc.
 */
class DownscaleLibraryImagesCommandTest {

  private static Image image(long id, String webPath, String filename) {
    Image image = new Image();
    image.setId(id);
    image.setWebPath(webPath);
    image.setFilename(filename);
    return image;
  }

  private static final Image ORIGINAL = image(12L, "20250314093000", "HTT Infographic.png");
  private static final Image COPY = image(480L, "20261101120000", "HTT Infographic.jpg");
  private static final String NEW_URL = "/assets/img/20261101120000-480/HTT%20Infographic.jpg";

  @Test
  void anEncodedReferenceIsSwitchedToTheCopy() {
    String html = "<p><img src=\"/assets/img/20250314093000-12/HTT%20Infographic.png\" alt=\"\"></p>";
    assertEquals("<p><img src=\"" + NEW_URL + "\" alt=\"\"></p>", DownscaleLibraryImagesCommand.rewrite(html, ORIGINAL, COPY));
  }

  @Test
  void aReferenceWithLiteralSpacesIsSwitchedToo() {
    String xml = "<widget name=\"image\"><image>/assets/img/20250314093000-12/HTT Infographic.png</image></widget>";
    assertEquals("<widget name=\"image\"><image>" + NEW_URL + "</image></widget>",
        DownscaleLibraryImagesCommand.rewrite(xml, ORIGINAL, COPY));
  }

  @Test
  void bothSpellingsInOnePageAreSwitched() {
    String xml = "<section style=\"background-image: url(/assets/img/20250314093000-12/HTT Infographic.png)\">"
        + "<widget name=\"content\"><html><![CDATA[<img src=\"/assets/img/20250314093000-12/HTT%20Infographic.png\">]]></html></widget>"
        + "</section>";
    String rewritten = DownscaleLibraryImagesCommand.rewrite(xml, ORIGINAL, COPY);
    assertEquals("<section style=\"background-image: url(" + NEW_URL + ")\">"
        + "<widget name=\"content\"><html><![CDATA[<img src=\"" + NEW_URL + "\">]]></html></widget>"
        + "</section>", rewritten);
    assertFalse(rewritten.contains("-12/"));
  }

  @Test
  void anEscapedAmpersandInALiteralFilenameIsRecognized() {
    Image original = image(7L, "20250101000000", "R&D Lab.png");
    String xml = "<image>/assets/img/20250101000000-7/R&amp;D Lab.png</image>";
    assertEquals("<image>" + NEW_URL + "</image>", DownscaleLibraryImagesCommand.rewrite(xml, original, COPY));
  }

  @Test
  void aVariantSuffixIsKept() {
    String html = "<img src=\"/assets/img/20250314093000-12/HTT%20Infographic.png?variant=large\">";
    assertEquals("<img src=\"" + NEW_URL + "?variant=large\">", DownscaleLibraryImagesCommand.rewrite(html, ORIGINAL, COPY));
  }

  @Test
  void anUnrecognizedFilenameKeepsItsNameButGetsTheNewPrefix() {
    // The image servlet reads only the id, so this still serves the copy
    String html = "<img src=\"/assets/img/20250314093000-12/renamed-later.png\">";
    assertEquals("<img src=\"/assets/img/20261101120000-480/renamed-later.png\">",
        DownscaleLibraryImagesCommand.rewrite(html, ORIGINAL, COPY));
  }

  @Test
  void aLongerFilenameStartingTheSameIsNotTakenForThisOne() {
    Image original = image(12L, "20250314093000", "photo.png");
    String html = "<a href=\"/assets/img/20250314093000-12/photo.png.bak\">";
    assertEquals("<a href=\"/assets/img/20261101120000-480/photo.png.bak\">",
        DownscaleLibraryImagesCommand.rewrite(html, original, COPY));
  }

  @Test
  void otherImagesAndFilesAreLeftAlone() {
    String html = "<img src=\"/assets/img/20250314093000-123/HTT%20Infographic.png\">"
        + "<img src=\"/assets/img/20250314093001-12/HTT%20Infographic.png\">"
        + "<a href=\"/assets/file/20250314093000-12/HTT%20Infographic.png\">";
    assertSame(html, DownscaleLibraryImagesCommand.rewrite(html, ORIGINAL, COPY),
        "a different id, a different upload time, and a file-library link are all different things");
  }

  @Test
  void textWithNoReferenceComesBackUnchanged() {
    String html = "<p>No images here</p>";
    assertSame(html, DownscaleLibraryImagesCommand.rewrite(html, ORIGINAL, COPY));
    assertSame(null, DownscaleLibraryImagesCommand.rewrite(null, ORIGINAL, COPY));
  }

  @Test
  void spellingsAreCountedForTheReport() {
    String xml = "<image>/assets/img/20250314093000-12/HTT Infographic.png</image>"
        + "<img src=\"/assets/img/20250314093000-12/HTT%20Infographic.png\">"
        + "<img src=\"/assets/img/20250314093000-12/HTT%20Infographic.png?variant=medium\">"
        + "<img src=\"/assets/img/20250314093000-12/old-name.png\">";
    DownscaleLibraryImagesCommand.Spellings spellings = DownscaleLibraryImagesCommand.countSpellings(xml, ORIGINAL);
    assertEquals(2, spellings.encoded());
    assertEquals(1, spellings.literal());
    assertEquals(1, spellings.other());
    assertEquals(4, spellings.getTotal());
  }

  @Test
  void onlyLargeWidePngsAndJpegsAreCandidates() {
    assertTrue(DownscaleLibraryImagesCommand.isOversized(sized(3700, 2_400_000, "image/png")));
    assertTrue(DownscaleLibraryImagesCommand.isOversized(sized(4032, 3_100_000, "image/jpeg")));
    assertFalse(DownscaleLibraryImagesCommand.isOversized(sized(1600, 2_400_000, "image/png")), "already at the limit");
    assertFalse(DownscaleLibraryImagesCommand.isOversized(sized(3700, 300 * 1024, "image/png")), "not over 300 KB");
    assertFalse(DownscaleLibraryImagesCommand.isOversized(sized(3700, 2_400_000, "image/gif")));
    assertFalse(DownscaleLibraryImagesCommand.isOversized(sized(3700, 2_400_000, "image/webp")));
    assertFalse(DownscaleLibraryImagesCommand.isOversized(null));
  }

  private static Image sized(int width, long fileLength, String fileType) {
    Image image = image(1L, "20250101000000", "x");
    image.setWidth(width);
    image.setFileLength(fileLength);
    image.setFileType(fileType);
    return image;
  }
}
