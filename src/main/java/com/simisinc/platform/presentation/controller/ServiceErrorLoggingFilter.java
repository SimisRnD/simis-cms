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

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.infrastructure.persistence.cms.ServiceErrorRepository;

/**
 * Records every uncaught exception that reaches this filter, so the admin Health Dashboard has
 * something queryable to show besides "check stdout" (issue #556). Mapped first in web.xml, ahead
 * of every other filter, so it wraps the entire chain -- any exception thrown by a later filter, a
 * servlet, or a JSP forward is caught here.
 *
 * <p>
 * This deliberately does not attempt to catch every {@code LOG.error(...)} call scattered across
 * the codebase -- that would mean either instrumenting every call site or hooking the SLF4J
 * backend, which here is {@code slf4j-simple} and has no appender/plugin mechanism to hook into.
 * Instead this captures the one thing that is both centrally reachable and genuinely
 * dashboard-worthy: an exception that escaped request handling entirely and would otherwise be
 * invisible to anyone not actively tailing the application log at that exact moment.
 * </p>
 *
 * @author SimIS
 * @created 8/10/2026
 */
public class ServiceErrorLoggingFilter implements Filter {

  private static Log LOG = LogFactory.getLog(ServiceErrorLoggingFilter.class);

  // Stack traces from a StackOverflowError or a deep recursive failure can run to many thousands
  // of frames; capped so one pathological error can't bloat the table or the dashboard render.
  private static final int MAX_STACK_TRACE_LENGTH = 8000;

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } catch (Throwable t) {
      recordError(request, t);
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      }
      if (t instanceof Error) {
        throw (Error) t;
      }
      // doFilter's own contract only allows checked IOException/ServletException; anything else
      // checked (there is no other realistic source in a servlet chain) is wrapped rather than
      // silently swallowed.
      throw new ServletException(t);
    }
  }

  /**
   * Persists {@code t}, guarded by its own try/catch so a failure while recording (for example the
   * database itself being down, which is a very plausible reason an error is happening in the
   * first place) can never suppress the original exception's normal propagation and error-page
   * handling.
   */
  private void recordError(ServletRequest request, Throwable t) {
    try {
      ServiceError error = new ServiceError();
      if (request instanceof HttpServletRequest) {
        error.setRequestUri(((HttpServletRequest) request).getRequestURI());
      }
      error.setExceptionClass(t.getClass().getName());
      error.setMessage(StringUtils.left(t.getMessage(), 1000));
      error.setStackTrace(StringUtils.left(ExceptionUtils.getStackTrace(t), MAX_STACK_TRACE_LENGTH));
      ServiceErrorRepository.save(error);
    } catch (Throwable recordingFailure) {
      LOG.error("Could not record a service error (original exception still propagates)", recordingFailure);
    }
  }
}
