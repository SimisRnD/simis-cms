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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;

/**
 * Verifies that the widget sanitizes the Instagram Graph API permalink and media url before building
 * the card markup, so a hostile value cannot break out of the href/src attributes and inject script
 * (stored XSS). The api values are persisted by a background job and rendered server-side here, which
 * is the sink where they must be made safe.
 *
 * @author Elizabeth Houser
 * @created 2026-07-19
 */
class InstagramWidgetTest {

  private static InstagramMedia imageMedia(String permalink, String mediaUrl) {
    InstagramMedia media = new InstagramMedia();
    media.setMediaType("IMAGE");
    media.setPermalink(permalink);
    media.setMediaUrl(mediaUrl);
    return media;
  }

  @Test
  void safePermalinkAndMediaUrlProduceCard() {
    String html = InstagramWidget.buildCardHtml(
        imageMedia("https://www.instagram.com/p/ABC123/", "https://scontent.cdninstagram.com/v/t51/photo.jpg"));
    Assertions.assertEquals(
        "<p><a target=\"_blank\" href=\"https://www.instagram.com/p/ABC123/\">"
            + "<img src=\"https://scontent.cdninstagram.com/v/t51/photo.jpg\" /></a></p>",
        html);
  }

  @Test
  void quoteBreakoutInPermalinkDropsCard() {
    // A double quote would close href="..." and let the rest inject markup
    Assertions.assertNull(InstagramWidget.buildCardHtml(
        imageMedia("https://x/\"><script>alert(1)</script>", "https://scontent.cdninstagram.com/a.jpg")));
  }

  @Test
  void quoteBreakoutInMediaUrlDropsCard() {
    // A double quote plus event handler would break out of src="..."
    Assertions.assertNull(InstagramWidget.buildCardHtml(
        imageMedia("https://www.instagram.com/p/ABC123/", "https://x/a.jpg\" onerror=\"alert(1)")));
  }

  @Test
  void activeSchemeUrlDropsCard() {
    // javascript: in the permalink and data: in the media url are both rejected
    Assertions.assertNull(InstagramWidget.buildCardHtml(
        imageMedia("javascript:alert(1)", "https://scontent.cdninstagram.com/a.jpg")));
    Assertions.assertNull(InstagramWidget.buildCardHtml(
        imageMedia("https://www.instagram.com/p/ABC123/", "data:text/plain;base64,SGk=")));
  }

  @Test
  void nullPermalinkOrMediaUrlDropsCard() {
    Assertions.assertNull(
        InstagramWidget.buildCardHtml(imageMedia(null, "https://scontent.cdninstagram.com/a.jpg")));
    Assertions.assertNull(
        InstagramWidget.buildCardHtml(imageMedia("https://www.instagram.com/p/ABC123/", null)));
  }
}
