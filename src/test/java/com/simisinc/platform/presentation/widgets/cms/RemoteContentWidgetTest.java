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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Two properties of the remote-content widget:
 * <ol>
 * <li>the remote-image wrapper sanitizes its url (a widget preference) before placing it into the
 * src attribute, so a value with an embedded quote or an active scheme cannot break out of the
 * attribute and inject markup (stored XSS);</li>
 * <li>the server-side fetch is gated by {@link com.simisinc.platform.application.http.RemoteUrlValidationCommand}
 * so an operator-set url cannot point the server at an internal, link-local, or cloud-metadata
 * address (SSRF).</li>
 * </ol>
 *
 * @author Elizabeth Houser
 * @created 2026-07-19
 */
class RemoteContentWidgetTest extends WidgetBase {

  @Test
  void safeImageUrlProducesImgTag() {
    Assertions.assertEquals("<img src=\"https://cdn.example.com/photo.jpg\" />",
        RemoteContentWidget.buildImageTag("https://cdn.example.com/photo.jpg"));
  }

  @Test
  void quoteBreakoutUrlIsRejected() {
    // Ends with .jpg (so it reaches the image branch) but embeds a quote + event handler
    Assertions.assertNull(RemoteContentWidget.buildImageTag("https://x/\"onerror=\"alert(1)\".jpg"));
  }

  @Test
  void activeSchemeUrlIsRejected() {
    Assertions.assertNull(RemoteContentWidget.buildImageTag("javascript:alert(1)//.jpg"));
  }

  @Test
  void nullUrlIsRejected() {
    Assertions.assertNull(RemoteContentWidget.buildImageTag(null));
  }

  @Test
  void internalMetadataUrlIsBlockedBeforeAnyServerFetch() {
    // The cloud instance-metadata endpoint: the canonical SSRF credential-theft target.
    Map<String, String> preferences = new HashMap<>();
    preferences.put("url", "http://169.254.169.254/latest/meta-data/iam/");
    widgetContext.setPreferences(preferences);

    Cache cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.CONTENT_REMOTE_URL_CACHE)).thenReturn(cache);

      WidgetContext result = new RemoteContentWidget().execute(widgetContext);

      // Refused, and — the point of the guard — the server never fetched it.
      Assertions.assertNull(result);
      httpGet.verify(() -> HttpGetCommand.execute(any()), never());
    }
  }

  @Test
  void publicUrlIsAllowedThroughToTheFetch() {
    // A public, routable literal address must NOT be blocked — the guard should not over-refuse.
    Map<String, String> preferences = new HashMap<>();
    preferences.put("url", "http://93.184.216.34/feed");
    widgetContext.setPreferences(preferences);

    Cache cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.CONTENT_REMOTE_URL_CACHE)).thenReturn(cache);
      httpGet.when(() -> HttpGetCommand.execute("http://93.184.216.34/feed")).thenReturn("");

      new RemoteContentWidget().execute(widgetContext);

      httpGet.verify(() -> HttpGetCommand.execute("http://93.184.216.34/feed"), times(1));
    }
  }
}
