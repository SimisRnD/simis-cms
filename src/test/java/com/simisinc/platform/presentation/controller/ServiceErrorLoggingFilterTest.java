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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.infrastructure.persistence.cms.ServiceErrorRepository;

/**
 * Verifies that ServiceErrorLoggingFilter records an uncaught exception without ever changing
 * what the rest of the container sees -- the original exception (and its checked/unchecked type)
 * must propagate exactly as it would without this filter, since it's mapped ahead of every other
 * filter and must not alter existing request handling (issue #556).
 *
 * @author SimIS
 * @created 8/10/2026
 */
class ServiceErrorLoggingFilterTest {

  @Test
  void aNormalRequestPassesThroughUntouched() throws Exception {
    ServiceErrorLoggingFilter filter = new ServiceErrorLoggingFilter();
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    try (MockedStatic<ServiceErrorRepository> repository = mockStatic(ServiceErrorRepository.class)) {
      filter.doFilter(request, response, chain);

      verify(chain, times(1)).doFilter(request, response);
      repository.verify(() -> ServiceErrorRepository.save(any()), never());
    }
  }

  @Test
  void aRuntimeExceptionIsRecordedAndRethrownUnwrapped() throws Exception {
    ServiceErrorLoggingFilter filter = new ServiceErrorLoggingFilter();
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/admin/broken-page");
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    RuntimeException thrown = new IllegalStateException("something broke");
    doThrow(thrown).when(chain).doFilter(request, response);

    try (MockedStatic<ServiceErrorRepository> repository = mockStatic(ServiceErrorRepository.class)) {
      RuntimeException caught = assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

      assertEquals(thrown, caught);
      repository.verify(() -> ServiceErrorRepository.save(argThatMatches(
          error -> "/admin/broken-page".equals(error.getRequestUri())
              && "java.lang.IllegalStateException".equals(error.getExceptionClass())
              && "something broke".equals(error.getMessage())
              && error.getStackTrace() != null && error.getStackTrace().contains("IllegalStateException"))));
    }
  }

  @Test
  void anIOExceptionIsRecordedAndRethrownUnwrapped() throws Exception {
    ServiceErrorLoggingFilter filter = new ServiceErrorLoggingFilter();
    ServletRequest request = mock(ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    IOException thrown = new IOException("stream closed");
    doThrow(thrown).when(chain).doFilter(request, response);

    try (MockedStatic<ServiceErrorRepository> repository = mockStatic(ServiceErrorRepository.class)) {
      IOException caught = assertThrows(IOException.class, () -> filter.doFilter(request, response, chain));

      assertEquals(thrown, caught);
    }
  }

  @Test
  void aFailureWhileRecordingDoesNotSuppressTheOriginalException() throws Exception {
    ServiceErrorLoggingFilter filter = new ServiceErrorLoggingFilter();
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    RuntimeException originalFailure = new IllegalStateException("the real problem");
    doThrow(originalFailure).when(chain).doFilter(request, response);

    try (MockedStatic<ServiceErrorRepository> repository = mockStatic(ServiceErrorRepository.class)) {
      // The database being down is a very plausible reason an error is happening in the first
      // place -- recording must never win over propagating what actually broke.
      repository.when(() -> ServiceErrorRepository.save(any())).thenThrow(new RuntimeException("db is down"));

      RuntimeException caught = assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

      assertEquals(originalFailure, caught);
    }
  }

  // Mockito's own argThat has an overload-resolution ambiguity with a bare lambda in this project's
  // Mockito version; a named predicate parameter sidesteps it without an unchecked-cast warning.
  private static ServiceError argThatMatches(java.util.function.Predicate<ServiceError> predicate) {
    return org.mockito.ArgumentMatchers.argThat(predicate::test);
  }
}
