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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

/**
 * Issue #1877. The point of these is not that no-cache is clever -- it is that the header is sent
 * at all.
 *
 * <p>Omitting Cache-Control is not the neutral choice it resembles: with neither an expiry nor a
 * validator a cache may invent a freshness lifetime, so a page edited today can be held for days by
 * a browser that never asks again. The removed branch was unreachable, and deleting the call along
 * with it would have left pages bare -- which is a behaviour change, and a worse one than the dead
 * code.
 *
 * @author elizabeth houser
 */
class CacheStrategyTest {

  private static Map<String, String> headersAfterNoCache() {
    Map<String, String> headers = new HashMap<>();
    HttpServletResponse response = mock(HttpServletResponse.class);
    doAnswer(i -> {
      headers.put(i.getArgument(0), i.getArgument(1));
      return null;
    }).when(response).setHeader(anyString(), anyString());

    CacheStrategy.setNoCache(response);
    return headers;
  }

  @Test
  void aPageIsDeclaredUncacheableRatherThanLeftUnspecified() {
    Map<String, String> headers = headersAfterNoCache();
    String cacheControl = headers.get("Cache-Control");
    assertTrue(cacheControl != null && !cacheControl.isBlank(),
        "a page with no Cache-Control invites heuristic freshness, which is not 'no caching'");
    assertTrue(cacheControl.contains("no-cache"), cacheControl);
    assertTrue(cacheControl.contains("no-store"), cacheControl);
    assertTrue(cacheControl.contains("must-revalidate"), cacheControl);
  }

  @Test
  void theOlderDirectivesAccompanyItForIntermediariesThatPredateCacheControl() {
    Map<String, String> headers = headersAfterNoCache();
    assertTrue("no-cache".equals(headers.get("Pragma")), String.valueOf(headers.get("Pragma")));
    assertTrue("-1".equals(headers.get("Expires")), String.valueOf(headers.get("Expires")));
  }

  @Test
  void theUnreachableCachingBranchIsGoneAndStaysGone() {
    // Asserted by reflection because the previous three tests all pass against the OLD class too --
    // they exercise setNoCache, which both versions have. Without this, a revert of the removal is
    // invisible to the suite, which is exactly what happened once while writing it.
    java.util.Set<String> methods = new java.util.HashSet<>();
    for (java.lang.reflect.Method m : CacheStrategy.class.getDeclaredMethods()) {
      methods.add(m.getName());
    }
    assertTrue(methods.contains("setNoCache"), "the header must still be set: " + methods);
    assertTrue(!methods.contains("setCacheHeaders"),
        "setCacheHeaders held the branch that could never run (#1877): " + methods);
    assertTrue(!methods.contains("setNoStore"),
        "setNoStore had no caller anywhere in the application: " + methods);
    assertTrue(CacheStrategy.class.getDeclaredFields().length == 0,
        "the max-age constants belonged to the removed branch and have no other use");
  }

  @Test
  void nothingHereEverMakesAPagePubliclyCacheable() {
    // The trap the removed branch left behind: making pages cacheable is a one-line change away,
    // and Front Door strips Set-Cookie from anything it caches, so cached pages would reach
    // visitors with no session -- breaking form posts and CSRF. If this assertion ever fails,
    // read the class javadoc before changing it.
    Map<String, String> headers = headersAfterNoCache();
    String cacheControl = headers.get("Cache-Control");
    assertTrue(!cacheControl.contains("public"),
        "a page must not advertise itself as publicly cacheable: " + cacheControl);
    assertTrue(!cacheControl.contains("max-age=" + 300),
        "the removed public branch used max-age=300; it must not come back: " + cacheControl);
  }
}
