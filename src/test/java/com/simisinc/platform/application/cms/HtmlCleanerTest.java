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
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

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
  void checkIframeTitleSurvives() {
    // An embedded frame with no accessible name is announced only as "frame" (WCAG 4.1.2), and a
    // title is the only way content can supply one. cleanRenderedMarkdown() already allowed this;
    // cleanContent() did not, so editors had no route to an accessible embed.
    String html = "<iframe src=\"https://www.youtube.com/embed/LFx-b-njZs0\" width=\"560\" height=\"315\" " +
        "title=\"Hampton Roads local leaders encourage us to pivot to the positive\" allowfullscreen=\"allowfullscreen\"></iframe>";

    String value = HtmlCommand.cleanContent(html);

    assertTrue(value.contains("title=\"Hampton Roads local leaders encourage us to pivot to the positive\""),
        "the iframe title must survive -- it is the frame's accessible name");
    assertTrue(value.contains("src=\"https://www.youtube.com/embed/LFx-b-njZs0\""), "src must survive");
    assertTrue(value.contains("allowfullscreen"), "allowfullscreen must survive");
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

  /** A site allowing one extra host, with Metabase off. */
  private MockedStatic<LoadSitePropertyCommand> siteAllowing(String hosts) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(AllowedIframeHostCommand.SITE_PROPERTY)).thenReturn(hosts);
    m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.enabled")).thenReturn("false");
    return m;
  }

  @Test
  void anIframeFromAnUnallowedHostIsStripped() {
    String html = "<p>before</p><iframe src=\"https://evil.example.com/x\" width=\"560\" height=\"315\"></iframe><p>after</p>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("")) {
      String value = HtmlCommand.cleanContent(html);
      assertFalse(value.contains("evil.example.com"));
      // The surrounding content is untouched -- this removes an embed, not the paragraph around it
      assertTrue(value.contains("before"));
      assertTrue(value.contains("after"));
    }
  }

  @Test
  void anIframeFromAnAllowedHostSurvives() {
    String html = "<iframe src=\"https://www.youtube-nocookie.com/embed/abc123\" width=\"560\" height=\"315\"></iframe>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("")) {
      String value = HtmlCommand.cleanContent(html);
      assertTrue(value.contains("www.youtube-nocookie.com/embed/abc123"));
    }
  }

  @Test
  void aHostTheSiteAddedSurvives() {
    String html = "<iframe src=\"https://app.vendor.example.com/embed/form\"></iframe>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("app.vendor.example.com")) {
      assertTrue(HtmlCommand.cleanContent(html).contains("app.vendor.example.com"));
    }
  }

  @Test
  void everyUnallowedIframeIsRemovedNotJustTheFirst() {
    // Removing from the live Elements list while iterating it skips entries, which would leave
    // every second embed in place.
    String html = "<iframe src=\"https://a.example.com/1\"></iframe>"
        + "<iframe src=\"https://b.example.com/2\"></iframe>"
        + "<iframe src=\"https://c.example.com/3\"></iframe>"
        + "<iframe src=\"https://d.example.com/4\"></iframe>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("")) {
      String value = HtmlCommand.cleanContent(html);
      assertFalse(value.contains("a.example.com"));
      assertFalse(value.contains("b.example.com"));
      assertFalse(value.contains("c.example.com"));
      assertFalse(value.contains("d.example.com"));
    }
  }

  @Test
  void anIframeWithAScriptBearingSrcIsStripped() {
    // cleanContent builds on Safelist.relaxed() and registers iframe/src without a protocol
    // restriction, so jsoup alone does not refuse this one -- unlike the markdown path, which
    // calls addProtocols("iframe", "src", "https"). The host check is what stops it here.
    String html = "<iframe src=\"javascript:alert(1)\"></iframe>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("")) {
      assertFalse(HtmlCommand.cleanContent(html).contains("javascript:"));
    }
  }

  @Test
  void aSurvivingEmbedIsStillWrappedAfterAnotherWasRemoved() {
    // The removal pass leaves detached elements in the snapshot getElementsByTag returned. Reusing
    // it made the wrapping pass dereference a null parent, and cleanContent catches and logs that
    // rather than failing -- so the only visible symptom was every embed after the removed one
    // silently losing its responsive-embed wrapper.
    String html = "<iframe src=\"https://evil.example.com/x\"></iframe>"
        + "<iframe src=\"https://player.vimeo.com/video/12345\"></iframe>";
    try (MockedStatic<LoadSitePropertyCommand> m = siteAllowing("")) {
      String value = HtmlCommand.cleanContent(html);
      assertFalse(value.contains("evil.example.com"));
      assertTrue(value.contains("player.vimeo.com/video/12345"));
      assertTrue(value.contains("responsive-embed widescreen"));
    }
  }
}
