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

  // issue #1797: the forms YouTube and Vimeo actually hand out, and telling an author when the one
  // they pasted was not among them.

  @Test
  void executeParsesTheLiveUrlYouTubesShareDialogGivesForAStream() {
    // The form that produced a broken embed on this platform's own content, and the reason this
    // issue exists -- YouTube gives it for a live stream and for its archive afterwards
    Assertions.assertEquals("https://www.youtube-nocookie.com/embed/5_TWZ6rM7oA?rel=0",
        embedUrlFor("https://www.youtube.com/live/5_TWZ6rM7oA?si=gRriCm5qsVYW0jnw"));
  }

  @Test
  void executeParsesTheLegacyYouTubeVUrl() {
    Assertions.assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0",
        embedUrlFor("https://www.youtube.com/v/dQw4w9WgXcQ"));
  }

  @Test
  void executeParsesVimeoChannelAndGroupUrls() {
    Assertions.assertEquals("https://player.vimeo.com/video/76979871",
        embedUrlFor("https://vimeo.com/channels/staffpicks/76979871"));
    Assertions.assertEquals("https://player.vimeo.com/video/76979871",
        embedUrlFor("https://vimeo.com/groups/animation/videos/76979871"));
  }

  @Test
  void executeCarriesAnUnlistedVimeoVideosPrivacyHash() {
    // Dropping the hash was worse than not matching: the widget reported success and the visitor
    // got a player Vimeo refuses to serve
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"video\">\n" +
            "  <videoUrl>https://vimeo.com/76979871/abcdef0123</videoUrl>\n" +
            "</widget>");

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("vimeo", request.getAttribute("provider"));
    Assertions.assertEquals("https://player.vimeo.com/video/76979871?h=abcdef0123", request.getAttribute("embedUrl"));
    // The oEmbed thumbnail lookup video.jsp makes from the browser needs the hash too, or it
    // returns nothing and the play button has no poster frame
    Assertions.assertEquals("https://vimeo.com/76979871/abcdef0123", request.getAttribute("videoPageUrl"));
  }

  @Test
  void executeLeavesAPublicVimeoUrlExactlyAsItWas() {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    addPreferencesFromWidgetXml(widgetContext, VIMEO_XML);

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    // No stray "?h=" on a video that has no hash
    Assertions.assertEquals("https://player.vimeo.com/video/76979871", request.getAttribute("embedUrl"));
    Assertions.assertEquals("https://vimeo.com/76979871", request.getAttribute("videoPageUrl"));
  }

  @Test
  void anUnrecognizedUrlIsReportedAsSuchAndAnEmptyOneIsNot() {
    // The whole point: these two used to be indistinguishable, so an author could not tell a bad
    // paste from a widget they had not filled in yet
    Assertions.assertEquals("false", recognizedFlagFor("https://example.com/not-a-video-site"));
    Assertions.assertNull(recognizedFlagFor(null));
    Assertions.assertNull(recognizedFlagFor("https://www.youtube.com/live/5_TWZ6rM7oA"));
  }

  @Test
  void onlyTheLayoutBuilderTierIsToldTheUrlWasNotRecognized() {
    // videoUrl is a page-layout preference: a content editor cannot reach it, and a visitor should
    // not be reading about the site's own misconfiguration
    Assertions.assertEquals("false", canBuildLayoutFor());
    Assertions.assertEquals("false", canBuildLayoutFor("content-editor"));
    Assertions.assertEquals("true", canBuildLayoutFor("content-manager"));
    Assertions.assertEquals("true", canBuildLayoutFor("admin"));
  }

  @Test
  void consentStillWithholdsEverythingForANewlyRecognizedUrl() {
    // The new forms go through the same gate as the old ones (issues #428/#366)
    when(request.getCookies()).thenReturn(new Cookie[] { DECLINED });
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"video\">\n" +
            "  <videoUrl>https://www.youtube.com/live/5_TWZ6rM7oA</videoUrl>\n" +
            "</widget>");

    VideoWidget widget = new VideoWidget();
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("consentGiven"));
    Assertions.assertNull(request.getAttribute("provider"));
    Assertions.assertNull(request.getAttribute("embedUrl"));
    Assertions.assertNull(request.getAttribute("thumbnailUrl"));
    Assertions.assertNull(request.getAttribute("videoPageUrl"));
    Assertions.assertNull(request.getAttribute("videoUrlRecognized"));
  }

  private String embedUrlFor(String videoUrl) {
    return (String) executeWith(videoUrl).getAttribute("embedUrl");
  }

  private String recognizedFlagFor(String videoUrl) {
    return (String) executeWith(videoUrl).getAttribute("videoUrlRecognized");
  }

  private String canBuildLayoutFor(String... roles) {
    if (roles.length > 0) {
      login(widgetContext);
      setRoles(widgetContext, roles);
    }
    return (String) executeWith("https://example.com/not-a-video-site").getAttribute("canBuildLayout");
  }

  /**
   * Runs the widget with consent given and the given url set, or with no url at all when it is
   * null. Preferences are reset first, so calling this twice in a test stands in for two separate
   * video widgets on one page rather than one widget accumulating settings.
   */
  private jakarta.servlet.http.HttpServletRequest executeWith(String videoUrl) {
    when(request.getCookies()).thenReturn(new Cookie[] { ACCEPTED });
    widgetContext.setPreferences(new java.util.HashMap<>());
    // WebContainerCommand wipes every non-page-level request attribute before each widget runs
    // (isPreservedAcrossWidgetReset), so a widget never sees the previous one's leftovers. This
    // harness has no container, so it stands in for that reset -- without it these assertions would
    // be reading the previous call's values and testing nothing.
    for (String name : new String[] { "provider", "embedUrl", "thumbnailUrl", "videoPageUrl",
        "videoUrlRecognized", "canBuildLayout" }) {
      request.removeAttribute(name);
    }
    if (videoUrl != null) {
      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"video\">\n  <videoUrl>" + videoUrl + "</videoUrl>\n</widget>");
    }
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockConsentRequired()) {
      new VideoWidget().execute(widgetContext);
    }
    return request;
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
