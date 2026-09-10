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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import jakarta.servlet.http.Cookie;

/**
 * Renders a click-to-play embed for a YouTube or Vimeo video whose URL is entered as a widget
 * preference (issue #428). This is a purpose-built widget: unlike {@code ContentWidget}'s raw-HTML
 * youtube.com/vimeo.com substring detection (which only toggled a since-abandoned, commented-out
 * CSP header), it parses the URL into a provider + video id and renders a lightweight placeholder --
 * never an unconditional iframe.
 *
 * <p>
 * Two gates stand between a page visitor and the real embed, both enforced here and in video.jsp --
 * neither is optional:
 * <ol>
 * <li><b>Consent</b>: the real embed markup (the youtube-nocookie.com/player.vimeo.com URL, and for
 * YouTube its static thumbnail image) is only written into the response when consent isn't being
 * required in the first place ({@code analytics.consentRequired ne 'true'}, the shipped default),
 * or the site's {@code analytics-consent} cookie is {@code "accepted"} -- the same cookie and the
 * same property main.jsp's own analytics/GTM loading gate checks (issue #366). Without the
 * consent-not-required branch, a fresh/default install -- where the accept/decline banner never
 * renders because consent isn't required, so the cookie can never become "accepted" -- would leave
 * every video embed on the site permanently stuck behind a consent requirement nothing on the page
 * ever offers the visitor a way to satisfy. Without consent (when it IS required and not yet
 * given), video.jsp renders a plain placeholder with no video-identifying markup at all, so
 * nothing about the page ever calls out to YouTube/Vimeo.</li>
 * <li><b>Click</b>: even with consent, the iframe is never embedded directly. video.jsp renders a
 * click-to-play button; its own inline script only inserts the &lt;iframe&gt; into the DOM after
 * the visitor clicks it.</li>
 * </ol>
 * </p>
 *
 * <p>
 * YouTube embeds use youtube-nocookie.com rather than youtube.com, which defers YouTube's own
 * non-essential cookies until playback actually starts.
 * </p>
 *
 * <p>
 * Vimeo has no static thumbnail URL the way YouTube does (img.youtube.com/vi/{id}/hqdefault.jpg
 * needs no API call) -- getting one requires calling Vimeo's oEmbed endpoint. That lookup is done
 * client-side, by video.jsp's own script calling https://vimeo.com/api/oembed.json directly from
 * the browser, specifically so this widget does not add another server-side outbound fetch to this
 * codebase (see issues #784 and #760, both real SSRF findings fixed by routing/pinning existing
 * server-side outbound fetches).
 * </p>
 *
 * @author SimIS Inc.
 */
public class VideoWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(VideoWidget.class);

  static final long serialVersionUID = -8484048371911908897L;

  static String JSP = "/cms/video.jsp";

  /** Mirrors the cookie name main.jsp's analytics-consent accept/decline banner sets (issue #366).
   * There is no shared constant for it today -- main.jsp reads it directly via the JSTL cookie map
   * (`cookie['analytics-consent']`) rather than through CookieConstants. */
  private static final String ANALYTICS_CONSENT_COOKIE = "analytics-consent";
  private static final String ANALYTICS_CONSENT_ACCEPTED = "accepted";

  /**
   * The URL forms YouTube hands out, which is what an author pastes (issue #1797). {@code live/} is
   * what the Share dialog gives for a stream and for its archive afterwards; {@code v/} is the
   * legacy form still sitting in old content. Host is matched as a substring so m./music. and any
   * other subdomain come along.
   */
  private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile(
      "youtube(?:-nocookie)?\\.com/(?:watch\\?(?:\\S*&)?v=|embed/|shorts/|live/|v/)([A-Za-z0-9_-]{6,})|youtu\\.be/([A-Za-z0-9_-]{6,})");
  /**
   * Vimeo's forms (issue #1797). Group 2 is the privacy hash an <b>unlisted</b> video carries as a
   * second path segment -- without it the player URL below is rejected, which is worse than not
   * matching at all: the widget would report success and the visitor would get a broken player.
   */
  private static final Pattern VIMEO_ID_PATTERN = Pattern.compile(
      "vimeo\\.com/(?:video/|channels/[A-Za-z0-9_-]+/|groups/[A-Za-z0-9_-]+/videos/)?(\\d+)(?:/([A-Za-z0-9]+))?");

  public WidgetContext execute(WidgetContext context) {

    // Neither privacy-sensitive nor dependent on consent -- just sizing/labeling, so these are
    // always set, even for the "hidden until consent" placeholder.
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    // aspectRatio is only ever used by video.jsp's c:choose to pick a known CSS ratio, defaulting
    // to 16:9 for anything unrecognized -- so it never needs validating here (same convention as
    // LogoWidget's "view" preference).
    context.getRequest().setAttribute("aspectRatio", context.getPreferences().get("aspectRatio"));

    boolean consentGiven = hasAnalyticsConsent(context);
    context.getRequest().setAttribute("consentGiven", String.valueOf(consentGiven));
    if (!consentGiven) {
      // Gate 1 (issue #428 / #366): without consent, provider/embedUrl/thumbnailUrl/videoPageUrl
      // are never populated -- not merely hidden by the JSP. video.jsp's placeholder branch is
      // driven off embedUrl being empty, so there is nothing here that identifies the video or
      // could cause a request to YouTube/Vimeo, even if the JSP were changed independently later.
      context.setJsp(JSP);
      return context;
    }

    String videoUrl = StringUtils.trimToNull(context.getPreferences().get("videoUrl"));
    if (videoUrl != null) {
      String youTubeId = extractYouTubeId(videoUrl);
      if (youTubeId != null) {
        context.getRequest().setAttribute("provider", "youtube");
        context.getRequest().setAttribute("embedUrl", "https://www.youtube-nocookie.com/embed/" + youTubeId + "?rel=0");
        context.getRequest().setAttribute("thumbnailUrl", "https://img.youtube.com/vi/" + youTubeId + "/hqdefault.jpg");
      } else {
        Matcher vimeoMatcher = VIMEO_ID_PATTERN.matcher(videoUrl);
        if (vimeoMatcher.find()) {
          String vimeoId = vimeoMatcher.group(1);
          // An unlisted video's privacy hash has to travel with the id on both URLs below, or Vimeo
          // refuses the embed and returns nothing for the thumbnail (issue #1797).
          String vimeoHash = vimeoMatcher.group(2);
          String hashParameter = vimeoHash != null ? "?h=" + vimeoHash : "";
          context.getRequest().setAttribute("provider", "vimeo");
          context.getRequest().setAttribute("embedUrl", "https://player.vimeo.com/video/" + vimeoId + hashParameter);
          // No static thumbnail URL for Vimeo -- video.jsp fetches one client-side via Vimeo's
          // oEmbed endpoint, using this page URL. See the class Javadoc for why that call is made
          // from the browser rather than from here.
          context.getRequest().setAttribute("videoPageUrl",
              "https://vimeo.com/" + vimeoId + (vimeoHash != null ? "/" + vimeoHash : ""));
        } else {
          // Neither pattern matched. This used to be indistinguishable from a widget nobody had
          // configured yet: embedUrl stayed unset, video.jsp rendered the same placeholder, and
          // nothing was said anywhere. An author has no reason to doubt a URL they copied from the
          // site itself, so the one thing that helps is being told (issue #1797). The page still
          // shows the same placeholder to a visitor -- only the log and the builder-only note below
          // change.
          LOG.warn("Video url is not a recognized YouTube or Vimeo address: " + videoUrl);
          context.getRequest().setAttribute("videoUrlRecognized", "false");
        }
      }
    }

    // Whether to show that note. The videoUrl is a page-layout preference, so the tier that can act
    // on it is the layout builder -- a content editor cannot reach it, and a visitor should not be
    // reading about the site's own misconfiguration.
    context.getRequest().setAttribute("canBuildLayout",
        String.valueOf(EditorPermissionCommand.canBuildLayout(context.getUserSession())));

    context.setJsp(JSP);
    return context;
  }

  private static String extractYouTubeId(String url) {
    Matcher matcher = YOUTUBE_ID_PATTERN.matcher(url);
    if (!matcher.find()) {
      return null;
    }
    return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
  }

  /**
   * @return true when consent isn't being required at all (matches main.jsp's own gate on this
   *     same property for the GA4/GTM/SimpliFi/Brand CDN scripts -- issue: the shipped default is
   *     analytics.consentRequired=false, so the accept/decline banner never renders and the
   *     analytics-consent cookie can never become "accepted"; without this check every video
   *     embed on the site was permanently stuck behind an unattainable consent requirement), or
   *     when consent *is* required and the analytics-consent cookie is present with value exactly
   *     "accepted" -- missing, "declined", or any other value all mean no consent
   */
  private static boolean hasAnalyticsConsent(WidgetContext context) {
    if (!"true".equals(LoadSitePropertyCommand.loadByName("analytics.consentRequired"))) {
      return true;
    }
    Cookie[] cookies = context.getRequest().getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (ANALYTICS_CONSENT_COOKIE.equals(cookie.getName())) {
        return ANALYTICS_CONSENT_ACCEPTED.equals(cookie.getValue());
      }
    }
    return false;
  }
}
