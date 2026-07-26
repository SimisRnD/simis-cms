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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.simisinc.platform.infrastructure.instance.InstanceManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Determines HTTP caching strategy for responses based on page type, session state, and admin settings.
 * Coordinates between application (Cache-Control headers) and AFD (caching rules).
 *
 * Cacheable responses:
 *   - Public pages (no authentication required)
 *   - No active session context (no user ID, admin/editor flags)
 *   - No personalization or privacy-sensitive data
 *   - Not explicitly marked cache-exempt by admin
 *
 * Cache headers:
 *   - Public pages: "public, max-age=300, stale-while-revalidate=3600"
 *   - Private/session pages: "no-cache, no-store, max-age=0, must-revalidate"
 *   - Admin-exempt pages: "no-store" (never cache)
 *
 * @author claude
 * @created 7/26/26
 */
public class CacheStrategy {

  private static Log LOG = LogFactory.getLog(CacheStrategy.class);

  // Default cache durations (seconds)
  public static final int CACHE_MAX_AGE_SECONDS = 300;  // 5 minutes (public pages)
  public static final int CACHE_STALE_WHILE_REVALIDATE_SECONDS = 3600;  // 1 hour

  /**
   * Determine if a request's response is cacheable and set appropriate Cache-Control header.
   *
   * @param request  The HTTP request
   * @param response The HTTP response to set headers on
   * @param pageId   Optional page ID (used to check admin cache exemption; null = auto-determine)
   * @return true if cacheable (Cache-Control set to "public, max-age=..."), false if not cacheable
   */
  public static boolean setCacheHeaders(HttpServletRequest request, HttpServletResponse response, Long pageId) {
    // Requests with query strings are never cached (typically search, filters, forms)
    if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
      setNoCache(response);
      return false;
    }

    // Admin/editor bypass: force no-cache during authoring
    String bypassHeader = request.getHeader("X-Bypass-Cache");
    if ("true".equalsIgnoreCase(bypassHeader)) {
      setNoCache(response);
      return false;
    }

    // Session check: if user is authenticated, don't cache
    HttpSession session = request.getSession(false);
    if (session != null) {
      Object userObj = session.getAttribute("userSession");  // SessionConstants.USER
      if (userObj != null) {
        setNoCache(response);
        return false;
      }

      // Any other session data present = personalization = don't cache
      if (session.getAttributeNames().hasMoreElements()) {
        setNoCache(response);
        return false;
      }
    }

    // Page-level cache exemption (future: check page property in database)
    // For now, all remaining pages are cacheable

    // Set public cache header with revalidation policy
    response.setHeader("Cache-Control",
        "public, max-age=" + CACHE_MAX_AGE_SECONDS +
        ", stale-while-revalidate=" + CACHE_STALE_WHILE_REVALIDATE_SECONDS);
    LOG.debug("Set cacheable response header (max-age=" + CACHE_MAX_AGE_SECONDS + "s)");
    return true;
  }

  /**
   * Set no-cache headers (for authenticated, session, or exempt pages).
   */
  public static void setNoCache(HttpServletResponse response) {
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
    response.setHeader("Expires", "-1");
  }

  /**
   * Set no-store headers (for sensitive endpoints like /healthz, /api/...).
   */
  public static void setNoStore(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }
}
