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

package com.simisinc.platform.domain.model;

import java.sql.Timestamp;
import java.util.Map;

/**
 * A social media profile link (issue #516) -- any platform name, not a fixed set.
 *
 * @author SimIS Inc.
 */
public class SocialMediaLink extends Entity {

  // Centralized so both the footer widget and the standard-footer JSPF resolve icons identically,
  // instead of each hand-maintaining their own copy (the old, hardcoded-per-platform approach).
  private static final Map<String, String> ICON_BY_PLATFORM = Map.ofEntries(
      Map.entry("facebook", "fa-facebook-square"),
      Map.entry("instagram", "fa-instagram"),
      Map.entry("linkedin", "fa-linkedin"),
      // Twitter rebranded to X in 2023; every name people actually write for it resolves to the
      // current X glyph (fa-x-twitter added to the vendored FontAwesome Free 6.1.1 bundle by hand
      // -- see all.css/v4-shims.css).
      //
      // The compound spellings are listed because a rebrand leaves both names in circulation and
      // people write them together -- and because "X/Twitter" is the name the Add a Platform form's
      // own help text tells an author to use (admin/social-media-link-form.jsp). Matching stays
      // exact rather than fuzzy on purpose: a contains() on "twitter" would claim "Twitterrific".
      Map.entry("twitter", "fa-x-twitter"),
      Map.entry("x", "fa-x-twitter"),
      Map.entry("x/twitter", "fa-x-twitter"),
      Map.entry("twitter/x", "fa-x-twitter"),
      Map.entry("x (twitter)", "fa-x-twitter"),
      Map.entry("twitter (x)", "fa-x-twitter"),
      Map.entry("x (formerly twitter)", "fa-x-twitter"),
      Map.entry("flickr", "fa-flickr"),
      Map.entry("youtube", "fa-youtube"),
      Map.entry("mastodon", "fa-mastodon"),
      Map.entry("tiktok", "fa-tiktok"),
      Map.entry("discord", "fa-discord"),
      Map.entry("github", "fa-github"));
  // Bundled FontAwesome Free 6.1.1 predates Threads/Bluesky glyphs, and any future/custom
  // platform name has no known icon at all -- fall back to a generic link icon.
  private static final String DEFAULT_ICON = "fa-link";

  private Long id = -1L;
  private String platformName = null;
  private String url = null;
  private int linkOrder = 100;
  private Timestamp created = null;

  public SocialMediaLink() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPlatformName() {
    return platformName;
  }

  public void setPlatformName(String platformName) {
    this.platformName = platformName;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getLinkOrder() {
    return linkOrder;
  }

  public void setLinkOrder(int linkOrder) {
    this.linkOrder = linkOrder;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public String getIconClass() {
    if (platformName == null) {
      return DEFAULT_ICON;
    }
    return ICON_BY_PLATFORM.getOrDefault(platformName.trim().toLowerCase(), DEFAULT_ICON);
  }
}
