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

package com.simisinc.platform.presentation.widgets.cms;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

import jakarta.servlet.http.Cookie;

/**
 * Covers issue #428: YouTube and Vimeo url parsing, the youtube-nocookie.com domain, and the
 * consent gate -- VideoWidget#execute must not populate provider/embedUrl/thumbnailUrl (the "real
 * embed markup" video.jsp renders into the click-to-play button) unless consent isn't required in
 * the first place, or the analytics-consent cookie is present and "accepted"; without either,
 * only the placeholder-driving attributes are set. Most tests below stub
 * analytics.consentRequired=true so they exercise the cookie-gated branch; the two
 * "ConsentNotRequired" tests cover the shipped default (issue #366/#428 fix).
 *
 * @author SimIS Inc.
 */
class VideoWidgetTest extends WidgetBase {

  private static final Cookie ACCEPTED = new Cookie("analytics-consent", "accepted");
  private static final Cookie DECLINED = new Cookie("analytics-consent", "declined");

  private static final String YOUTUBE_XML = "<widget name=\"video\">\n" +
      "  <videoUrl>https://www.youtube.com/watch?v=dQw4w9WgXcQ</videoUrl>\n" +
      "  <title>Sample Video</title>\n" +
      "</widget>";

  private static final String VIMEO_XML = "<widget name=\"video\">\n" +
      "  <videoUrl>https://vimeo.com/76979871</videoUrl>\n" +
      "  <title>Sample Vimeo Video</title>\n" +
      "</widget>";

  private static MockedStatic<LoadSitePropertyCommand> mockConsentRequired() {
    MockedStatic<LoadSitePropertyCommand> mocked = mockStatic(LoadSitePropertyCommand.class);
    mocked.when(() -> LoadSitePropertyCommand.loadByName("analytics.consentRequired")).thenReturn("true");
    return mocked;
  }

  @Test
  void executeWithYouTubeUrlAndConsentRendersNoCookieEmbedAndThumbnail() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("consentGiven"));
    Assertions.assertEquals("youtube", request.getAttribute("provider"));
    Assertions.assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0", request.getAttribute("embedUrl"));
    Assertions.assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", request.getAttribute("thumbnailUrl"));
    Assertions.assertEquals(VideoWidget.JSP, widgetContext.getJsp());

    // Never youtube.com itself -- youtube-nocookie.com only
    Assertions.assertFalse(((String) request.getAttribute("embedUrl")).contains("//www.youtube.com"));
  }

  @Test
  void executeParsesYouTubeShortUrlAndEmbedUrlVariants() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });

    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"video\">\n" +
              "  <videoUrl>https://youtu.be/dQw4w9WgXcQ</videoUrl>\n" +
              "</widget>");
      new VideoWidget().execute(widgetContext);
      Assertions.assertEquals("youtube", request.getAttribute("provider"));
      Assertions.assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0", request.getAttribute("embedUrl"));

      preferences.clear();
      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"video\">\n" +
              "  <videoUrl>https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ</videoUrl>\n" +
              "</widget>");
      new VideoWidget().execute(widgetContext);
      Assertions.assertEquals("youtube", request.getAttribute("provider"));
      Assertions.assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0", request.getAttribute("embedUrl"));
    }
  }

  @Test
  void executeWithVimeoUrlAndConsentRendersPlayerEmbedAndPageUrlForClientSideOembed() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    addPreferencesFromWidgetXml(widgetContext, VIMEO_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("consentGiven"));
    Assertions.assertEquals("vimeo", request.getAttribute("provider"));
    Assertions.assertEquals("https://player.vimeo.com/video/76979871", request.getAttribute("embedUrl"));
    // No server-side thumbnail lookup -- video.jsp does the oEmbed call client-side using this url
    Assertions.assertEquals("https://vimeo.com/76979871", request.getAttribute("videoPageUrl"));
    Assertions.assertNull(request.getAttribute("thumbnailUrl"));
    Assertions.assertEquals(VideoWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeWithNoConsentCookieWithholdsRealEmbedMarkupEvenForAValidYouTubeUrl() {
    // No cookies at all on the request (the common case for a first-time visitor)
    when(request.getCookies()).thenReturn(null);
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("consentGiven"));
    // The real embed markup must never be populated without consent -- video.jsp's placeholder
    // branch is driven entirely off these being absent, not a JSP-side re-check of the cookie.
    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
    Assertions.assertNull(request.getAttribute("thumbnailUrl"));
    Assertions.assertEquals(VideoWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeWithNoConsentCookieWithholdsRealEmbedMarkupForAValidVimeoUrl() {
    when(request.getCookies()).thenReturn(null);
    addPreferencesFromWidgetXml(widgetContext, VIMEO_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("consentGiven"));
    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
    Assertions.assertNull(request.getAttribute("videoPageUrl"));
  }

  @Test
  void executeWithDeclinedConsentCookieWithholdsRealEmbedMarkup() {
    when(request.getCookies()).thenReturn(new Cookie[] { DECLINED });
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("consentGiven"));
    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
  }

  @Test
  void executeWithUnrelatedCookiesWithholdsRealEmbedMarkup() {
    when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("some-other-cookie", "accepted") });
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("consentGiven"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
  }

  @Test
  void executeWithNoVideoUrlLeavesEmbedUrlUnset() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("consentGiven"));
    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
    Assertions.assertEquals(VideoWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeWithUnrecognizedUrlLeavesEmbedUrlUnset() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"video\">\n" +
            "  <videoUrl>https://example.com/not-a-video-site</videoUrl>\n" +
            "</widget>");

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
  }

  // issue #366/#428 fix: on the shipped default (analytics.consentRequired=false), the
  // accept/decline banner never renders and the analytics-consent cookie can never become
  // "accepted" -- so consent must be treated as given even with no cookie at all.

  @Test
  void executeWithConsentNotRequiredRendersEmbedRegardlessOfCookie() {
    when(request.getCookies()).thenReturn(null);
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("analytics.consentRequired")).thenReturn("false");
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("consentGiven"));
    Assertions.assertEquals("youtube", request.getAttribute("provider"));
    Assertions.assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0", request.getAttribute("embedUrl"));
  }

  @Test
  void executeWithConsentRequiredPropertyUnsetTreatsItAsNotRequired() {
    when(request.getCookies()).thenReturn(null);
    addPreferencesFromWidgetXml(widgetContext, YOUTUBE_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("analytics.consentRequired")).thenReturn(null);
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("consentGiven"));
    Assertions.assertEquals("youtube", request.getAttribute("provider"));
  }
}
