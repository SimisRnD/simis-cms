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

package com.simisinc.platform.application.cms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests HTML functions
 *
 * @author matt rajkowski
 * @created 3/2/21 10:00 PM
 */
class HtmlCleanerTest {

  @Test
  void cleanHtml() {
    String html = "<h3>Info Area 1</h3>\n" +
        "<p><span></span><span>This is some content.</span></p>\n" +
        "<p><span>This is some content.</span></p>\n" +
        "<p><span>This is some content.</span></p>\n" +
        "<p><span>This is some content too.</span></p>";
    String expected = "<h3>Info Area 1</h3>\n" +
        "<p>This is some content.</p>\n" +
        "<p>This is some content.</p>\n" +
        "<p>This is some content.</p>\n" +
        "<p>This is some content too.</p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);

    // Run it through again
    String newValue = HtmlCommand.cleanContent(value);
    assertEquals(expected, newValue);
  }

  @Test
  void cleanStylesHtml() {
    String html = "<h3><span style=\"background-color: #f1c40f;\">Info Area</span></h3>\n" +
        "<p>This is some content.</p>\n" +
        "<p><a title=\"First tab\" href=\"/first-tab\">This is some content</a>. <span class=\"fas fa-bowling-ball tinymce-noedit\"></span></p>\n" +
        "<p>Another line. <span class=\"fas fa-wind tinymce-noedit\"></span></p>\n" +
        "<p><img class=\"image-right\" src=\"/assets/img/20210219211416-3/Office%20Desk.jpg\" alt=\"Desk\" width=\"129\" height=\"97\" /></p>\n" +
        "<p>This is some content.</p>\n" +
        "<p>This is some content too.</p>";
    String expected = "<h3><span style=\"background-color: #f1c40f\">Info Area</span></h3>\n" +
        "<p>This is some content.</p>\n" +
        "<p><a title=\"First tab\" href=\"/first-tab\">This is some content</a>. <i class=\"fas fa-bowling-ball\"></i></p>\n" +
        "<p>Another line. <i class=\"fas fa-wind\"></i></p>\n" +
        "<p><img class=\"image-right\" src=\"/assets/img/20210219211416-3/Office%20Desk.jpg\" alt=\"Desk\" width=\"129\" height=\"97\"></p>\n" +
        "<p>This is some content.</p>\n" +
        "<p>This is some content too.</p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);

    // Run it through again
    String newValue = HtmlCommand.cleanContent(value);
    assertEquals(expected, newValue);
  }

  @Test
  void cleanIconTagsWithExtraSpan() {
    String html = "<p>This is some content. <span>This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content.&nbsp;<span class=\"fas fa-caravan tinymce-noedit\"></span> <span class=\"fas fa-mitten tinymce-noedit\"></span></span></p>";
    String expected = "<p>This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content.&nbsp;<i class=\"fas fa-caravan\"></i> <i class=\"fas fa-mitten\"></i></p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);

    // Run it through again
    value = HtmlCommand.cleanContent(value);
    assertEquals(expected, value);
  }

  @Test
  void cleanExtraSpan() {
    String html = "<p><span>This is some content. </span><span>This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. <span class=\"fas fa-broadcast-tower tinymce-noedit\"></span> This is some content. This is some content. This is some content. <span class=\"fas fa-closed-captioning tinymce-noedit\"></span> </span></p>";
    String expected = "<p>This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. <i class=\"fas fa-broadcast-tower\"></i> This is some content. This is some content. This is some content. <i class=\"fas fa-closed-captioning\"></i> </p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);

    // Run it through again
    value = HtmlCommand.cleanContent(value);
    assertEquals(expected, value);
  }

  @Test
  void cleanExtraDivSpan() {
    String html = "<div><div><p><span>This is some content. </span><span>This is some content.</span></p></div>" +
        "<div><p><span>This is some content. This is some content. This is some content. This is some content. " +
        "This is some content. This is some content. This is some content. This is some content. This is some content. " +
        "<span class=\"fas fa-broadcast-tower tinymce-noedit\"></span> This is some content. This is some content. This is some content. " +
        "<span class=\"fas fa-closed-captioning tinymce-noedit\"></span> </span></p>" +
        "</div></div>";
    String expected = "<p>This is some content. This is some content.</p><p>This is some content. This is some content. This is some content. " +
        "This is some content. This is some content. This is some content. This is some content. This is some content. This is some content. " +
        "<i class=\"fas fa-broadcast-tower\"></i> This is some content. This is some content. This is some content. " +
        "<i class=\"fas fa-closed-captioning\"></i> </p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);

    // Run it through again
    value = HtmlCommand.cleanContent(value);
    assertEquals(expected, value);
  }

  @Test
  void checkResponsiveVideos() {
    String html = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video></div>";
    String expected = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\"></video></div>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }

  @Test
  void checkDivVideos() {
    String html = "<div><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video></div>";
    String expected = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\"></video></div>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }
  @Test
  void checkVideoCaptionTrackSurvives() {
    // A caption track is what takes a video with audio from failing WCAG 1.2.2 to passing it, so
    // the sanitizer has to let it through intact -- kind and srclang included, or the track is inert.
    String html = "<video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" />\n" +
        "<track kind=\"captions\" src=\"/assets/view/20200914083941-104/SimIS-HTT.vtt\" srclang=\"en\" label=\"English\" default /></video>";
    String expected = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\">\n" +
        "<track kind=\"captions\" src=\"/assets/view/20200914083941-104/SimIS-HTT.vtt\" srclang=\"en\" label=\"English\" default></video></div>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }

  @Test
  void checkVideoCaptionTrackKeepsEveryAttribute() {
    // jsoup drops any attribute not named in the safelist, so a track can survive as an element
    // while quietly losing what makes it usable. Assert each attribute individually rather than
    // trusting the element's presence.
    String html = "<video controls=\"controls\">" +
        "<track kind=\"subtitles\" src=\"/assets/view/1-2/clip.vtt\" srclang=\"es\" label=\"Espanol\" /></video>";

    String value = HtmlCommand.cleanContent(html);

    assertTrue(value.contains("<track"), "the track element itself must survive");
    assertTrue(value.contains("kind=\"subtitles\""), "kind must survive");
    assertTrue(value.contains("src=\"/assets/view/1-2/clip.vtt\""), "src must survive");
    assertTrue(value.contains("srclang=\"es\""), "srclang must survive");
    assertTrue(value.contains("label=\"Espanol\""), "label must survive");
  }

  @Test
  void checkVideos() {
    String html = "<video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video>";
    String expected = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\"></video></div>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }

  /**
   * A style value with no property name (":") used to throw ArrayIndexOutOfBoundsException inside
   * removeUnallowedStyles, which is the FIRST of the mutators that run after the Cleaner. The
   * exception was caught and logged, so every later mutator was skipped and the partially
   * processed document was returned anyway -- with the tests still green. One character of
   * authored content was enough to disable the whole phase.
   *
   * <p>The video wrapper is the assertion because it is produced by handleVideoTags, which runs
   * near the END of the sequence: if it is present, everything between the failure point and it
   * ran too.
   */
  @Test
  void malformedStyleDoesNotSkipTheRestOfTheProcessing() {
    String html = "<p style=\":\">Intro</p>\n" +
        "<video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video>";

    String value = HtmlCommand.cleanContent(html);

    assertTrue(value.contains("<div class=\"responsive-embed widescreen\">"),
        "the video must still be wrapped -- a mutator that runs after the failure point: " + value);
  }

  /**
   * The same trap reached through every other shape of malformed declaration, and through a tag
   * other than the one the first mutator pass visits. "::" and a trailing bare ":" both produce a
   * zero-length array from split(":") the same way a lone ":" does.
   */
  @Test
  void malformedStyleVariantsAreProcessed() {
    String[] styles = {":", "::", "color: red;:", ":;font-size: 2em"};
    for (String style : styles) {
      String html = "<span style=\"" + style + "\">Intro</span>\n" +
          "<video controls=\"controls\" width=\"300\" height=\"150\">\n" +
          "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video>";

      String value = HtmlCommand.cleanContent(html);

      assertTrue(value.contains("<div class=\"responsive-embed widescreen\">"),
          "style=\"" + style + "\" must not stop the processing: " + value);
    }
  }

  /**
   * The denylisted properties are still stripped when a malformed declaration sits alongside them,
   * so recovering from the malformed one does not quietly let a stripped property through.
   */
  @Test
  void denylistedStylesAreStillRemovedAroundAMalformedDeclaration() {
    String value = HtmlCommand.cleanContent("<p style=\"color: red;:;font-size: 2em\">Intro</p>");

    assertTrue(!value.contains("color"), "color must still be stripped: " + value);
    assertTrue(!value.contains("font-size"), "font-size must still be stripped: " + value);
  }
}
