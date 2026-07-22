/*
 * Copyright 2022 SimIS Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

/**
 * Verifies the safe behaviors of {@link TrustedProxyIpFilter}: it is a
 * transparent pass-through by default, and it never takes the application down
 * when configured in an environment where RemoteIpFilter is unavailable. The
 * actual X-Forwarded-For resolution is provided by Tomcat's RemoteIpFilter and
 * is exercised in a servlet container rather than here.
 */
class TrustedProxyIpFilterTest {

  @Test
  void passesThroughUnchangedWhenNotConfigured() throws Exception {
    TrustedProxyIpFilter filter = new TrustedProxyIpFilter() {
      @Override
      protected String trustedProxiesSetting() {
        return null; // the environment variable is not set (the default)
      }
    };
    filter.init(mock(FilterConfig.class));

    ServletRequest request = mock(ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);

    // The original request is passed straight through, untouched
    verify(chain, times(1)).doFilter(request, response);
  }

  @Test
  void failsSafeWhenRemoteIpFilterIsUnavailable() throws Exception {
    // The variable is set, but RemoteIpFilter is not on this (non-container) classpath
    TrustedProxyIpFilter filter = new TrustedProxyIpFilter() {
      @Override
      protected String trustedProxiesSetting() {
        return "10\\.\\d+\\.\\d+\\.\\d+";
      }
    };
    assertDoesNotThrow(() -> filter.init(mock(FilterConfig.class))); // must not fail startup

    ServletRequest request = mock(ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);

    // Falls back to a transparent pass-through rather than failing
    verify(chain, times(1)).doFilter(request, response);
  }
}
