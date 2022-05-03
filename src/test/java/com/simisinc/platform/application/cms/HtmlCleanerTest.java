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

/**
 * Tests HTML functions
 *
 * @author matt rajkowski
 * @created 3/2/21 10:00 PM
 */
public class HtmlCleanerTest {

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
  void checkVideos() {
    String html = "<video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\" /></video>";
    String expected = "<div class=\"responsive-embed widescreen\"><video controls=\"controls\" width=\"300\" height=\"150\">\n" +
        "<source src=\"/assets/view/20200914083941-104/SimIS-HTT.mp4\" type=\"video/mp4\"></video></div>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }
}
