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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Editor-authored video embeds get the same privacy choices VideoWidget makes.
 *
 * <p>
 * VideoWidget embeds YouTube from youtube-nocookie.com because that host withholds YouTube's
 * non-essential cookies until playback begins. An embed code pasted from YouTube's share dialog
 * uses youtube.com and never passes through the widget, so the same site set tracking cookies on
 * page view or not depending only on how the page was authored (issue 1469).
 * </p>
 *
 * @author SimIS Inc.
 */
class ContentVideoEmbedCommandTest {

  @Test
  void aPastedYoutubeEmbedIsPointedAtTheNoCookieHost() {
    String html = "<p>Watch:</p><iframe width=\"560\" height=\"315\" "
        + "src=\"https://www.youtube.com/embed/8elFL8KThY0\" allowfullscreen></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("https://www.youtube-nocookie.com/embed/8elFL8KThY0"));
    assertFalse(result.contains("//www.youtube.com/embed/"), "the tracking host must not survive");
  }

  @Test
  void theBareHostFormIsRewrittenToo() {
    // Share codes appear both ways; matching only the www form would leave half of them tracking.
    String html = "<iframe src=\"https://youtube.com/embed/qYIRapHuDvU\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("https://www.youtube-nocookie.com/embed/qYIRapHuDvU"));
  }

  @Test
  void everythingElseAboutTheEmbedIsLeftAlone() {
    // The share dialog appends ?si=..., and width/height/allow/title are the author's business.
    String html = "<iframe width=\"560\" height=\"315\" "
        + "src=\"https://www.youtube.com/embed/8elFL8KThY0?si=LXaWqsMRSNAkFMF&amp;start=30\" "
        + "title=\"A video\" allow=\"encrypted-media\" allowfullscreen></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("?si=LXaWqsMRSNAkFMF&amp;start=30"), "query string must survive");
    assertTrue(result.contains("width=\"560\""));
    assertTrue(result.contains("title=\"A video\""));
    assertTrue(result.contains("allowfullscreen"));
  }

  @Test
  void anEmbedIsDeferredUnlessTheAuthorAlreadySaidOtherwise() {
    String html = "<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("loading=\"lazy\""));
  }

  @Test
  void anAuthorsOwnLoadingAttributeIsNotOverridden() {
    String html = "<iframe loading=\"eager\" src=\"https://www.youtube.com/embed/abc\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("loading=\"eager\""));
    assertFalse(result.contains("loading=\"lazy\""), "the author asked for eager; respect it");
  }

  @Test
  void anEmbedAlreadyOnTheNoCookieHostIsStillDeferred() {
    String html = "<iframe src=\"https://www.youtube-nocookie.com/embed/abc\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("loading=\"lazy\""));
    assertTrue(result.contains("youtube-nocookie.com/embed/abc"));
  }

  @Test
  void aVimeoEmbedIsDeferredButItsHostIsNotRewritten() {
    // Vimeo has no nocookie equivalent, so there is no host to move it to.
    String html = "<iframe src=\"https://player.vimeo.com/video/123456\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("player.vimeo.com/video/123456"));
    assertTrue(result.contains("loading=\"lazy\""));
  }

  @Test
  void anIframeThatIsNotAVideoIsUntouched() {
    // The careers page embeds a JazzHR board this way. Deferring an application form the visitor
    // came to the page for would be a regression, not a privacy win.
    String html = "<iframe src=\"https://simis.applytojob.com/apply/jobs\" height=\"800\"></iframe>";

    assertSame(html, ContentVideoEmbedCommand.privacyEnhanceEmbeds(html),
        "a non-video embed must come back as the very same string");
  }

  @Test
  void everyEmbedInADocumentIsHandled() {
    String html = "<iframe src=\"https://www.youtube.com/embed/one\"></iframe>"
        + "<p>between</p>"
        + "<iframe src=\"https://youtube.com/embed/two\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("youtube-nocookie.com/embed/one"));
    assertTrue(result.contains("youtube-nocookie.com/embed/two"));
    assertFalse(result.contains("//www.youtube.com/"));
    assertFalse(result.contains("//youtube.com/"));
    assertTrue(result.contains("<p>between</p>"), "surrounding content must be preserved");
  }

  @Test
  void aGreaterThanInsideAnAttributeDoesNotCutTheTagInHalf() {
    // Scanning for the first '>' would end the tag inside the title and corrupt the document.
    String html = "<iframe title=\"Before > After\" src=\"https://www.youtube.com/embed/abc\"></iframe>";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertTrue(result.contains("title=\"Before > After\""));
    assertTrue(result.contains("youtube-nocookie.com/embed/abc"));
    assertTrue(result.endsWith("</iframe>"));
  }

  @Test
  void contentWithNoEmbedComesBackUnchanged() {
    String html = "<p>Just words, and an <img src=\"/a.png\"> for company.</p>";
    assertSame(html, ContentVideoEmbedCommand.privacyEnhanceEmbeds(html));
  }

  @Test
  void nothingIsNotAnError() {
    assertNull(ContentVideoEmbedCommand.privacyEnhanceEmbeds(null));
    assertEquals("", ContentVideoEmbedCommand.privacyEnhanceEmbeds(""));
  }

  @Test
  void aTruncatedTagIsLeftAsItIsRatherThanGuessedAt() {
    // Better to render the author's broken markup than to invent a closing bracket.
    String html = "<p>ok</p><iframe src=\"https://www.youtube.com/embed/abc\"";

    String result = ContentVideoEmbedCommand.privacyEnhanceEmbeds(html);

    assertSame(html, result);
  }
}
