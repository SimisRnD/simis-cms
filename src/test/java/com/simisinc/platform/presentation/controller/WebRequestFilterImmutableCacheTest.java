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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

/**
 * When the immutable cache header is withdrawn.
 *
 * <p>The first version withdrew on any status that was not exactly 200 or 304. Tomcat's
 * DefaultServlet -- which serves /fonts and /css -- sets other statuses on its way to serving a 200,
 * so fonts shipped with no-store: a guaranteed re-download every visit, worse than the missing
 * header the change was meant to fix. Assets under /assets/img go through PageServlet, never hit
 * that path, and cached correctly, which made a logic error look like a path-specific one.
 *
 * <p>Only an error may withdraw it. These cases exist because the previous tests covered which
 * paths qualify but never what the wrapper does with a status.
 */
class WebRequestFilterImmutableCacheTest {

  private static HttpServletResponse wrapped() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    new WebRequestFilter.ImmutableAssetResponse(response);
    return response;
  }

  private static void verifyStillImmutable(HttpServletResponse response) {
    verify(response, times(1)).setHeader("Cache-Control", WebRequestFilter.IMMUTABLE_CACHE_CONTROL);
    verify(response, never()).setHeader("Cache-Control", "no-store");
  }

  @Test
  void theHeaderIsSetAsSoonAsTheResponseIsWrapped() {
    verifyStillImmutable(wrapped());
  }

  @Test
  void successAndRevalidationKeepTheHeader() {
    for (int status : new int[] { 200, 304 }) {
      HttpServletResponse response = mock(HttpServletResponse.class);
      new WebRequestFilter.ImmutableAssetResponse(response).setStatus(status);
      verifyStillImmutable(response);
    }
  }

  @Test
  void benignNonSuccessStatusesKeepTheHeader() {
    // The regression: any of these previously withdrew the header on a response that still
    // served the asset with a 200.
    for (int status : new int[] { 201, 202, 206, 301, 302, 307 }) {
      HttpServletResponse response = mock(HttpServletResponse.class);
      new WebRequestFilter.ImmutableAssetResponse(response).setStatus(status);
      verify(response, never()).setHeader("Cache-Control", "no-store");
    }
  }

  @Test
  void errorStatusesWithdrawTheHeader() {
    for (int status : new int[] { 400, 403, 404, 410, 500, 503 }) {
      HttpServletResponse response = mock(HttpServletResponse.class);
      new WebRequestFilter.ImmutableAssetResponse(response).setStatus(status);
      verify(response, times(1)).setHeader("Cache-Control", "no-store");
    }
  }

  @Test
  void sendErrorWithdrawsTheHeader() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    new WebRequestFilter.ImmutableAssetResponse(response).sendError(404);
    verify(response, times(1)).setHeader("Cache-Control", "no-store");

    HttpServletResponse withMessage = mock(HttpServletResponse.class);
    new WebRequestFilter.ImmutableAssetResponse(withMessage).sendError(500, "boom");
    verify(withMessage, times(1)).setHeader("Cache-Control", "no-store");
  }
}
