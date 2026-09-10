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

import jakarta.servlet.http.HttpServletResponse;

/**
 * The cache policy for generated pages: they are not cached, and they say so explicitly.
 *
 * <p>This class used to choose between a public cache header and a no-cache one. That choice never
 * happened. The public branch required a request whose session carried no {@code userSession}
 * attribute, and WebRequestFilter creates a UserSession for every visitor -- anonymous ones
 * included -- before PageServlet runs, so the attribute was always present and the branch was
 * unreachable from the day it merged (#1877). Every page has always gone out no-cache; removing the
 * branch changes nothing on the wire.
 *
 * <p><b>Why the header is still set, rather than omitted.</b> Sending no {@code Cache-Control} is
 * not the neutral option it resembles. With neither an expiry nor a validator a cache may invent a
 * freshness lifetime -- commonly a fraction of the age since Last-Modified -- so a page edited today
 * could be held for days by a browser that never asks again. Saying no-cache is what keeps an edit
 * visible. WebRequestFilter.isRevalidatedAsset records the same reasoning for static assets (#1827).
 *
 * <p><b>Why pages are not simply made cacheable.</b> This is the trap the dead branch left behind:
 * the one-line version of "fix it" is to test whether the session is logged IN rather than merely
 * present, which makes public pages cacheable. Front Door strips {@code Set-Cookie} from anything it
 * caches, and every page here mints a JSESSIONID, so cached pages would reach visitors with no
 * session at all -- breaking form posts and CSRF tokens. The cache key is the URL with no cookie
 * variation, so a signed-in visitor would also be served the anonymous copy. Making pages cacheable
 * requires anonymous requests that set no cookies and markup with no per-visitor variation, and
 * neither is true today.
 *
 * @author SimIS Inc.
 */
public class CacheStrategy {

  private CacheStrategy() {
    // static helper
  }

  /**
   * Declares the response uncacheable.
   *
   * <p>Pragma and Expires accompany Cache-Control for intermediaries predating it: two headers, and
   * one fewer class of surprise.
   */
  public static void setNoCache(HttpServletResponse response) {
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
    response.setHeader("Expires", "-1");
  }
}
