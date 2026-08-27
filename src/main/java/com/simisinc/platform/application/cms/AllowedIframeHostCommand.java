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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * The single list of hosts an {@code <iframe>} may load from, used in both places that can enforce
 * it.
 *
 * <p>
 * Two layers, because they fail differently and neither is sufficient alone. {@link HtmlCommand}
 * consults this on save, so a disallowed embed never becomes stored content and the author finds
 * out while they can still fix it. {@code PageServlet} emits it as the CSP {@code frame-src}
 * directive, which also covers content stored before a host was removed from the list, content
 * written to the database by some route that bypasses the sanitizer, and a sanitizer bug. Enforcing
 * only at save leaves existing rows unchecked; enforcing only at render gives the author a blank
 * box and no explanation.
 * </p>
 *
 * <p>
 * The list is assembled rather than configured outright. Sites add their own hosts through the
 * {@code security.iframe.allowedHosts} site property, but the hosts the platform's own widgets
 * require are always present and cannot be removed by clearing that setting -- otherwise emptying a
 * text field silently breaks a shipped feature, which is exactly the failure this class exists to
 * prevent. Metabase is included on the same principle: when the integration is switched on, its
 * configured host is permitted automatically, because a customer enabling a feature should not also
 * have to discover a second setting to make it render.
 * </p>
 *
 * @author SimIS Inc.
 */
public class AllowedIframeHostCommand {

  /**
   * Hosts a video embed can legitimately come from. Always permitted; a site cannot remove these.
   *
   * <p>
   * This list was first written from what VideoWidget emits, which was the wrong question and shipped
   * a regression: the widget renders youtube-nocookie.com, but an author pasting YouTube's own share
   * markup gets www.youtube.com/embed, and three published news posts on the pilot carried exactly
   * that. Those embeds were stripped on save and refused by frame-src on render. The requirement
   * lives in authored content, not only in the platform.
   * </p>
   *
   * <p>
   * The point of the allowlist is to refuse frames from arbitrary third parties, not to enforce which
   * of a video vendor's own domains an author used. Both YouTube forms are therefore permitted, with
   * and without the www prefix, since content carries both.
   * </p>
   */
  private static final List<String> PLATFORM_HOSTS = List.of(
      // VideoWidget renders the nocookie domain
      "www.youtube-nocookie.com",
      "youtube-nocookie.com",
      // What YouTube's own "Copy embed code" produces, and what authored content actually contains
      "www.youtube.com",
      "youtube.com",
      // VideoWidget's Vimeo player, and what Vimeo's own embed code produces
      "player.vimeo.com");

  public static final String SITE_PROPERTY = "security.iframe.allowedHosts";

  /** A leading URL scheme, per RFC 3986: letter, then letters/digits/plus/period/hyphen, then ":". */
  private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");

  private AllowedIframeHostCommand() {
    // Static utility, not instantiated
  }

  /**
   * Every host an iframe may load from: the platform's own, the Metabase host when that integration
   * is enabled, and whatever the site has added.
   *
   * @return lower-cased hosts, in a stable order, without duplicates
   */
  public static List<String> allowedHosts() {
    Set<String> hosts = new LinkedHashSet<>(PLATFORM_HOSTS);

    if ("true".equals(LoadSitePropertyCommand.loadByName("bi.metabase.enabled"))) {
      String host = hostOf(LoadSitePropertyCommand.loadByName("bi.metabase.url"));
      if (host != null) {
        hosts.add(host);
      }
    }

    // Accepts commas, whitespace or newlines, and tolerates a full URL where a host was meant --
    // "https://example.com/embed" is what someone pastes when the label says host, and rejecting it
    // silently would be a worse answer than understanding it.
    String configured = LoadSitePropertyCommand.loadByName(SITE_PROPERTY);
    if (StringUtils.isNotBlank(configured)) {
      for (String entry : configured.split("[,\\s]+")) {
        String host = hostOf(entry);
        if (host != null) {
          hosts.add(host);
        }
      }
    }
    return new ArrayList<>(hosts);
  }

  /**
   * Whether an iframe {@code src} may load. Relative sources are same-origin and always allowed;
   * anything with a host must match the list, and a value that cannot be parsed is refused rather
   * than guessed at.
   *
   * @param src the raw src attribute
   * @return true when the iframe may be kept
   */
  public static boolean isAllowed(String src) {
    return isAllowed(src, allowedHosts());
  }

  /**
   * Whether an iframe {@code src} may load, against a list the caller already has.
   *
   * <p>
   * Callers checking several iframes should read {@link #allowedHosts()} once and pass it here, so a
   * document with many embeds does not repeat the site-property lookup per element.
   * </p>
   *
   * @param src the raw src attribute
   * @param allowed the hosts to check against
   * @return true when the iframe may be kept
   */
  public static boolean isAllowed(String src, List<String> allowed) {
    if (StringUtils.isBlank(src) || allowed == null) {
      return false;
    }
    String value = src.trim();
    // A protocol-relative "//host/path" carries a host despite looking relative, so it is resolved
    // rather than treated as same-origin.
    if (value.startsWith("//")) {
      value = "https:" + value;
    } else {
      Matcher scheme = SCHEME.matcher(value);
      if (!scheme.find()) {
        // No scheme at all -- a genuinely relative path, which is same-origin.
        return true;
      }
      // Anything with a scheme must be http or https. This is what refuses "javascript:alert(1)"
      // and "data:text/html,<script>...", both of which execute in the embedding page's context
      // and neither of which contains "://" to give itself away as absolute.
      String protocol = scheme.group(1).toLowerCase(Locale.ROOT);
      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        return false;
      }
    }
    String host = hostOf(value);
    return host != null && allowed.contains(host);
  }

  /** The CSP frame-src source list: 'self' plus every allowed host as an https origin. */
  public static String cspFrameSourceList() {
    StringBuilder sb = new StringBuilder("'self'");
    for (String host : allowedHosts()) {
      sb.append(" https://").append(host);
    }
    return sb.toString();
  }

  /** The host of a URL or bare hostname, lower-cased; null when it cannot be determined. */
  private static String hostOf(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String candidate = value.trim();
    if (!candidate.contains("://")) {
      candidate = "https://" + candidate;
    }
    try {
      String host = URI.create(candidate).getHost();
      return StringUtils.isBlank(host) ? null : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
