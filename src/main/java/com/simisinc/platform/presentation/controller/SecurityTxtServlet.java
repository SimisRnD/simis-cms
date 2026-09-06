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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serves <code>/.well-known/security.txt</code> (RFC 9116) -- the machine-readable channel a
 * security researcher looks for before reporting a vulnerability.
 *
 * <p>
 * Built the same way as {@link LlmsTxtServlet} and {@link RobotsServlet}: a
 * <code>config/cms/security.txt</code> override wins verbatim if present, otherwise the body is
 * generated from the <code>securitytxt.*</code> site property namespace, edited from
 * <code>/admin/security-txt-properties</code> through the same generic sitePropertiesEditor widget.
 * </p>
 *
 * <p>
 * Two deliberate differences from its siblings:
 * </p>
 * <ul>
 * <li><b>A blank contact returns 404, whatever the toggle says.</b> RFC 9116 makes
 * <code>Contact</code> mandatory, and a file advertising no way to reach anyone is worse than no
 * file -- it reads as a channel that exists, so a reporter stops looking for another one. This is
 * also why the shipped default is inert: every site gets the properties, none starts publishing
 * until an administrator supplies a contact.</li>
 * <li><b>No <code>site.online</code> gate.</b> {@link LlmsTxtServlet} applies one because it
 * discloses live navigation and collection structure. This file discloses only a contact the
 * administrator explicitly chose to publish, and a site that is not yet public is exactly when a
 * misconfiguration is most likely to need reporting.</li>
 * </ul>
 *
 * @author SimIS Inc.
 */
@WebServlet(name = "SecurityTxtServlet", urlPatterns = "/.well-known/security.txt")
public class SecurityTxtServlet extends HttpServlet {

  private static final Log LOG = LogFactory.getLog(SecurityTxtServlet.class);

  /** RFC 9116 requires an Expires field; RFC 3339 is the required format. */
  private static final DateTimeFormatter RFC3339 = DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  /** How far ahead the generated Expires sits. RFC 9116 recommends less than a year. */
  static final int EXPIRES_DAYS = 365;

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/plain;charset=UTF-8");

    try {
      String content = loadSecurityTxt();
      if (StringUtils.isBlank(content)) {
        Map<String, String> propertyMap = LoadSitePropertyCommand.loadAsMap("securitytxt");

        if (!isEnabled(propertyMap)) {
          // Not cached -- an administrator flipping this back on must take effect on the next
          // request, not be masked by a stale 404 in a browser or CDN for up to a day.
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          response.getWriter().print("# security.txt is disabled (securitytxt.enabled)\n");
          return;
        }

        List<String> contacts = contactsFrom(propertyMap.get("securitytxt.contact"));
        if (contacts.isEmpty()) {
          // The mandatory field is missing, so there is no valid document to serve. Uncached for
          // the same reason as the toggle above.
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          response.getWriter().print("# security.txt is not configured (securitytxt.contact)\n");
          return;
        }

        content = generateSecurityTxt(contacts, propertyMap,
            LoadSitePropertyCommand.loadAsMap("site"));
      }

      // Only a real, successfully generated (or statically overridden) body is cacheable -- the
      // same reasoning as RobotsServlet/LlmsTxtServlet (issue #417 / PR #935).
      response.setHeader("Cache-Control", "public, max-age=86400");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().print(content);
    } catch (Exception e) {
      LOG.error("Error serving security.txt: " + e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().print("# Error generating security.txt\n");
    }
  }

  /**
   * Same resolution order as {@link RobotsServlet#loadRobotsTxt()} and
   * {@link LlmsTxtServlet#loadLlmsTxt()}: <code>cms.path</code> (falling back to
   * <code>user.dir</code>), then <code>config/cms/security.txt</code> beneath it, served verbatim.
   * A hand-authored file is frequently a signed one, so it is emitted byte-for-byte -- reformatting
   * it would invalidate the signature it is wrapped in.
   */
  private String loadSecurityTxt() {
    try {
      String configPath = System.getProperty("cms.path");
      if (StringUtils.isBlank(configPath)) {
        configPath = System.getProperty("user.dir");
      }
      File file = new File(configPath, "config/cms/security.txt");
      if (file.exists() && file.canRead()) {
        LOG.debug("Loading custom security.txt from: " + file.getAbsolutePath());
        return new String(Files.readAllBytes(file.toPath()));
      }
    } catch (Exception e) {
      LOG.warn("Error loading custom security.txt file: " + e.getMessage());
    }
    return null;
  }

  /** Default-allow, matching llms.enabled -- an explicit "false" is the only disabling value. */
  static boolean isEnabled(Map<String, String> propertyMap) {
    return !"false".equals(propertyMap.getOrDefault("securitytxt.enabled", "true"));
  }

  /**
   * RFC 9116 allows repeated Contact fields in decreasing order of preference, so the property
   * accepts several separated by commas or newlines. Each must be a URI: a bare address is the
   * thing an administrator will actually type, so "security@example.com" is normalized to
   * "mailto:security@example.com" rather than emitted as-is and silently ignored by parsers.
   */
  static List<String> contactsFrom(String value) {
    List<String> result = new ArrayList<>();
    if (StringUtils.isBlank(value)) {
      return result;
    }
    for (String part : value.split("[,\\r\\n]")) {
      String contact = part.trim();
      if (contact.isEmpty()) {
        continue;
      }
      if (!contact.contains(":")) {
        contact = (contact.contains("@") ? "mailto:" : "https://") + contact;
      }
      result.add(contact);
    }
    return result;
  }

  /**
   * Expires is computed per request rather than stored, because the single most common failure of
   * a hand-maintained security.txt is silently going stale: a fixed date in a settings field would
   * quietly expire and there is nothing in the product to notice. A generated document is current
   * by construction. An administrator who needs a pinned date (typically because the file is
   * signed) uses the config/cms/security.txt override, which is served untouched.
   */
  String generateSecurityTxt(List<String> contacts, Map<String, String> propertyMap,
      Map<String, String> sitePropertyMap) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Security contact information for this site (RFC 9116)\n");
    for (String contact : contacts) {
      sb.append("Contact: ").append(contact).append("\n");
    }
    sb.append("Expires: ")
        .append(RFC3339.format(Instant.now().plus(EXPIRES_DAYS, ChronoUnit.DAYS)))
        .append("\n");

    appendIfPresent(sb, "Encryption", propertyMap.get("securitytxt.encryption"));
    appendIfPresent(sb, "Acknowledgments", propertyMap.get("securitytxt.acknowledgments"));
    appendIfPresent(sb, "Policy", propertyMap.get("securitytxt.policy"));
    appendIfPresent(sb, "Preferred-Languages", propertyMap.get("securitytxt.preferredLanguages"));

    // Canonical tells a reader the URL this document is meant to be served from, so a copy found
    // elsewhere can be recognized as a copy. Derived from site.url rather than the request, which
    // an attacker controls via the Host header.
    String siteUrl = sitePropertyMap.get("site.url");
    if (StringUtils.isNotBlank(siteUrl)) {
      sb.append("Canonical: ").append(StringUtils.stripEnd(siteUrl.trim(), "/"))
          .append("/.well-known/security.txt\n");
    }
    return sb.toString();
  }

  private static void appendIfPresent(StringBuilder sb, String field, String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(field).append(": ").append(value.trim()).append("\n");
    }
  }
}
