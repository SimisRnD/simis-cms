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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns a browser's Content-Security-Policy violation report into the (directive, host) pair worth
 * storing.
 *
 * <p>
 * Browsers send these two different ways and the shape is not the same. The original {@code
 * report-uri} posts {@code application/csp-report}, a single object under a "csp-report" key with
 * hyphenated field names. The Reporting API's {@code report-to} posts {@code
 * application/reports+json}, an array of envelopes with the violation under "body" and camel-cased
 * field names. Both are still in use -- report-uri is deprecated but is what several browsers
 * actually send -- so both are emitted and both are read here.
 * </p>
 *
 * <p>
 * Two normalizations matter more than the parsing. The blocked url is reduced to its host: a
 * violation report is one of the few places a third party's URL parameters could reach this
 * database, and a source list needs the host anyway. The document url is reduced to its path for
 * the same reason -- the query string of the page someone was on can carry anything.
 * </p>
 *
 * @author SimIS Inc.
 */
public class CspViolationReportCommand {

  /**
   * The largest report body that will be read.
   *
   * <p>
   * A violation report is a few hundred bytes. This endpoint cannot require authentication, so the
   * cap is what stops a caller from making the server read an arbitrarily large body; it is set far
   * above any real report so nothing legitimate is refused.
   * </p>
   */
  public static final int MAX_REPORT_BYTES = 64 * 1024;

  private CspViolationReportCommand() {
    // Static utility, not instantiated
  }

  /** One violation worth recording. */
  public static class Violation {
    private final String effectiveDirective;
    private final String blockedHost;
    private final String documentPath;

    public Violation(String effectiveDirective, String blockedHost, String documentPath) {
      this.effectiveDirective = effectiveDirective;
      this.blockedHost = blockedHost;
      this.documentPath = documentPath;
    }

    public String getEffectiveDirective() {
      return effectiveDirective;
    }

    public String getBlockedHost() {
      return blockedHost;
    }

    public String getDocumentPath() {
      return documentPath;
    }
  }

  /**
   * Reads every violation in a report body, in either browser format.
   *
   * @param payload the parsed request body
   * @return the violations found; empty when the body is not a violation report
   */
  public static List<Violation> parse(JsonNode payload) {
    List<Violation> violations = new ArrayList<>();
    if (payload == null) {
      return violations;
    }
    // Reporting API: an array of envelopes, only some of which are CSP violations
    if (payload.isArray()) {
      for (JsonNode envelope : payload) {
        String type = envelope.path("type").asText("");
        if (!"csp-violation".equals(type)) {
          continue;
        }
        Violation violation = read(envelope.path("body"));
        if (violation != null) {
          violations.add(violation);
        }
      }
      return violations;
    }
    // report-uri: a single object under "csp-report"
    JsonNode report = payload.path("csp-report");
    if (report.isMissingNode()) {
      return violations;
    }
    Violation violation = read(report);
    if (violation != null) {
      violations.add(violation);
    }
    return violations;
  }

  /** Reads one violation, accepting either field-naming convention. */
  private static Violation read(JsonNode report) {
    if (report == null || report.isMissingNode()) {
      return null;
    }
    String directive = firstOf(report, "effectiveDirective", "effective-directive", "violatedDirective",
        "violated-directive");
    if (StringUtils.isBlank(directive)) {
      return null;
    }
    // "script-src-elem 'self'" -- the directive can arrive with its source list attached
    directive = directive.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);

    String blocked = firstOf(report, "blockedURL", "blocked-uri", "blockedURI");
    String host = normalizeBlockedUri(blocked);
    if (host == null) {
      return null;
    }
    String document = firstOf(report, "documentURL", "document-uri", "documentURI");
    return new Violation(directive, host, normalizeDocumentUri(document));
  }

  private static String firstOf(JsonNode report, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode node = report.path(fieldName);
      if (!node.isMissingNode() && !node.isNull()) {
        String value = node.asText(null);
        if (StringUtils.isNotBlank(value)) {
          return value;
        }
      }
    }
    return null;
  }

  /**
   * The host a blocked url points at, or the CSP keyword for a violation that has no host.
   *
   * <p>
   * Browsers report inline and eval violations with a bare word rather than a url -- "inline",
   * "eval", "self", "data", "blob". Those are kept, quoted the way they would be written in a
   * policy, because "an inline style was refused" is a real finding; they just are not hosts.
   * </p>
   *
   * @param blockedUri the reported blocked url
   * @return a lower-cased host, a quoted CSP keyword, or null when nothing useful can be read
   */
  public static String normalizeBlockedUri(String blockedUri) {
    if (StringUtils.isBlank(blockedUri)) {
      return null;
    }
    String value = blockedUri.trim();
    // Keywords, reported without a scheme
    if (!value.contains("://")) {
      String keyword = value.toLowerCase(Locale.ROOT);
      // A trailing colon is how some browsers report scheme-only blocks, e.g. "data:"
      if (keyword.endsWith(":")) {
        keyword = keyword.substring(0, keyword.length() - 1);
      }
      if ("inline".equals(keyword) || "eval".equals(keyword) || "self".equals(keyword)
          || "data".equals(keyword) || "blob".equals(keyword) || "filesystem".equals(keyword)
          || "wasm-eval".equals(keyword) || "unsafe-eval".equals(keyword)) {
        return "'" + keyword + "'";
      }
      return null;
    }
    try {
      String host = URI.create(value).getHost();
      if (StringUtils.isBlank(host)) {
        return null;
      }
      host = host.toLowerCase(Locale.ROOT);
      // Never store more than a column can hold, and never store something absurd
      return host.length() > 255 ? null : host;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * The path of the page a violation happened on, with any query string removed.
   *
   * @param documentUri the reported document url
   * @return a path, or null when none can be read
   */
  public static String normalizeDocumentUri(String documentUri) {
    if (StringUtils.isBlank(documentUri)) {
      return null;
    }
    String value = documentUri.trim();
    String path;
    try {
      path = value.contains("://") ? URI.create(value).getPath() : value;
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (StringUtils.isBlank(path)) {
      return null;
    }
    // Defensive: a path can still carry a query or fragment when the value was not a parseable url
    for (String separator : new String[] { "?", "#" }) {
      int index = path.indexOf(separator);
      if (index >= 0) {
        path = path.substring(0, index);
      }
    }
    if (StringUtils.isBlank(path)) {
      return null;
    }
    return path.length() > 512 ? path.substring(0, 512) : path;
  }
}
