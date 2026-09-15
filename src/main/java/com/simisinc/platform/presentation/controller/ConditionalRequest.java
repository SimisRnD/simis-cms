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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Conditional-request and transfer-encoding helpers for the servlets that generate a whole document
 * per request -- {@code sitemap.xml} and the Atom feeds.
 *
 * <p>Both are polled on a schedule by a client that already holds a copy: a crawler for the sitemap,
 * a feed reader for the feed. Almost every one of those requests finds nothing changed, so the point
 * of a validator is to answer with an empty 304 instead of resending a document the client already
 * has. Without one there is nothing for the client to revalidate against and every poll transfers
 * the full body forever.
 *
 * <p>These three methods lived as private members of {@code SitemapServlet} (issue #619). Extracting
 * them is what issue #2042 needed: {@code FeedServlet} documented the same behavior as worth having
 * and deferred it precisely because the helpers could not be reached from outside that class. The
 * logic is unchanged by the move -- this is the sitemap's implementation, made reusable.
 *
 * @author SimIS Inc.
 */
public class ConditionalRequest {

  private ConditionalRequest() {
    // Static helpers only
  }

  /**
   * A strong entity tag for the given body, quoted as RFC 7232 section 2.3 requires.
   *
   * <p>Hashing the rendered body rather than deriving a tag from a timestamp is deliberate: a
   * document can change without its newest record moving. A sitemap loses a URL when a page is
   * archived; a feed loses an entry when a post is excluded from syndication. A timestamp-derived
   * tag would call both of those unchanged.
   */
  public static String entityTag(String content) {
    return "\"" + DigestUtils.md5Hex(content) + "\"";
  }

  /**
   * True when the request's conditional headers show the client's cached copy is still current.
   * If-None-Match is authoritative when present (RFC 7232 section 3.3); otherwise falls back to
   * If-Modified-Since, rounded up a second since HTTP dates truncate sub-second precision.
   */
  public static boolean isNotModified(HttpServletRequest request, long mostRecentTimestamp, String etag) {
    String ifNoneMatch = request.getHeader("If-None-Match");
    if (StringUtils.isNotBlank(ifNoneMatch)) {
      return "*".equals(ifNoneMatch) || ifNoneMatch.contains(etag);
    }
    if (mostRecentTimestamp > 0) {
      long ifModifiedSince = request.getDateHeader("If-Modified-Since");
      return ifModifiedSince >= 0 && mostRecentTimestamp <= ifModifiedSince + 1000;
    }
    return false;
  }

  public static boolean gzipSupported(HttpServletRequest request) {
    String acceptEncoding = request.getHeader("Accept-Encoding");
    return acceptEncoding != null && acceptEncoding.contains("gzip");
  }

  public static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipStream = new GZIPOutputStream(bos)) {
      gzipStream.write(text.getBytes("UTF-8"));
      gzipStream.flush();
    }
    return bos.toByteArray();
  }
}
