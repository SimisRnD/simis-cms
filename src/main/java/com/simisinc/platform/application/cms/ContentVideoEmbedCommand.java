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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Applies the platform's own video-privacy choices to embeds an editor wrote by hand.
 *
 * <p>
 * {@code VideoWidget} does not embed YouTube from youtube.com. It uses youtube-nocookie.com, and
 * says why: that host defers YouTube's non-essential cookies until playback actually starts. A
 * visitor who never presses play is never given a tracking cookie.
 * </p>
 *
 * <p>
 * An editor pasting an embed code from YouTube's own share dialog gets youtube.com, and that markup
 * goes straight into a blog post or content block without passing through the widget. So the same
 * site both refuses to set YouTube's cookies without a click and sets them on page view, decided
 * by nothing more than how the page happened to be authored (issue 1469).
 * </p>
 *
 * <p>
 * This rewrites the host at render, so it applies to content already stored rather than only to
 * what an editor saves next. It also adds {@code loading="lazy"} where the author did not, which
 * keeps an embed below the fold from being fetched until it is nearly needed.
 * </p>
 *
 * <p>
 * What this deliberately does NOT do is reproduce the widget's second gate. {@code VideoWidget}
 * withholds the iframe entirely until the visitor clicks a placeholder, so no request reaches
 * YouTube at all. Matching that from content HTML means generating the placeholder markup and
 * wiring it to the widget's script, which is a larger change and is left to issue 1469 rather than
 * half-done here. The host rewrite is the part that changes what a visitor is given without asking.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ContentVideoEmbedCommand {

  private static Log LOG = LogFactory.getLog(ContentVideoEmbedCommand.class);

  private static final String IFRAME_TAG_START = "<iframe";

  /** The hosts a pasted YouTube embed code uses, longest first so the www form is matched before the bare one */
  private static final String[] YOUTUBE_HOSTS = { "//www.youtube.com/embed/", "//youtube.com/embed/" };
  private static final String YOUTUBE_NOCOOKIE = "//www.youtube-nocookie.com/embed/";

  private ContentVideoEmbedCommand() {
    // Static utility, not instantiated
  }

  /**
   * Rewrites hand-authored video embeds to the platform's privacy-preserving form.
   *
   * @param html editor-authored HTML, as stored
   * @return the same HTML with youtube.com embeds pointed at youtube-nocookie.com and deferred
   */
  public static String privacyEnhanceEmbeds(String html) {
    if (StringUtils.isBlank(html) || !StringUtils.containsIgnoreCase(html, IFRAME_TAG_START)) {
      return html;
    }
    try {
      return processTags(html);
    } catch (Exception e) {
      // Never let a markup surprise turn into a broken or blank page -- worst case this render
      // keeps the embed the author wrote, exactly as it behaved before this existed.
      LOG.warn("Video embed enhancement failed; leaving content unchanged", e);
      return html;
    }
  }

  private static String processTags(String html) {
    StringBuilder output = null;
    int copiedUpTo = 0;
    int searchFrom = 0;
    while (true) {
      int tagStart = StringUtils.indexOfIgnoreCase(html, IFRAME_TAG_START, searchFrom);
      if (tagStart == -1) {
        break;
      }
      int tagEnd = findUnquotedGreaterThan(html, tagStart);
      if (tagEnd == -1) {
        // A truncated or malformed tag near the end of the string -- stop rather than guess.
        break;
      }
      String originalTag = html.substring(tagStart, tagEnd + 1);
      String newTag = tryBuildReplacement(originalTag);
      if (newTag != null) {
        if (output == null) {
          output = new StringBuilder();
        }
        output.append(html, copiedUpTo, tagStart);
        output.append(newTag);
        copiedUpTo = tagEnd + 1;
      }
      searchFrom = tagEnd + 1;
    }
    if (output == null) {
      return html;
    }
    output.append(html, copiedUpTo, html.length());
    return output.toString();
  }

  /** The rewritten tag, or null when there is nothing to change. */
  private static String tryBuildReplacement(String tag) {
    String updated = tag;
    for (String host : YOUTUBE_HOSTS) {
      if (StringUtils.containsIgnoreCase(updated, host)) {
        updated = StringUtils.replaceIgnoreCase(updated, host, YOUTUBE_NOCOOKIE);
        break;
      }
    }
    boolean isVideoEmbed = StringUtils.containsIgnoreCase(updated, "youtube-nocookie.com/embed/")
        || StringUtils.containsIgnoreCase(updated, "player.vimeo.com/video/");
    if (isVideoEmbed && !StringUtils.containsIgnoreCase(updated, "loading=")) {
      updated = updated.substring(0, IFRAME_TAG_START.length())
          + " loading=\"lazy\""
          + updated.substring(IFRAME_TAG_START.length());
    }
    return updated.equals(tag) ? null : updated;
  }

  /**
   * The index of the '>' that closes this tag, skipping any inside a quoted attribute value.
   *
   * <p>
   * A src can legitimately contain '>' once escaped, and a title attribute can contain one
   * outright, so scanning for the first '>' would cut a tag in half.
   * </p>
   */
  private static int findUnquotedGreaterThan(String html, int from) {
    char quote = 0;
    for (int i = from; i < html.length(); i++) {
      char c = html.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    return -1;
  }
}
