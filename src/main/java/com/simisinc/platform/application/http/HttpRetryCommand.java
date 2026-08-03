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

package com.simisinc.platform.application.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Shared retry-with-backoff wrapper around {@link HttpClient#send}, used by
 * {@link HttpGetCommand} and {@link HttpPostCommand} -- and, through them,
 * {@link HttpDeleteCommand}/{@link HttpPutCommand}/{@link HttpPatchCommand}, which delegate into
 * those two -- for calls to fixed, operator-configured third-party API endpoints (issue #455's
 * audit found this as the broadest gap: a single transient blip or rate-limit response from a
 * vendor currently fails the call outright with no retry at all).
 *
 * <p>Only retries {@link #RETRYABLE_METHODS idempotent methods} (GET/HEAD/PUT/DELETE): a 5xx or
 * timeout can arrive after a vendor has already accepted and acted on a POST/PATCH (e.g. sending
 * a campaign), so blindly resending one risks a duplicate, externally-visible side effect. A
 * non-idempotent request is sent exactly once, unchanged from pre-retry behavior.
 *
 * <p>For a retryable method, retries a connection failure or a 429/5xx response up to
 * {@link #MAX_ATTEMPTS} times. A {@link HttpTimeoutException} (the per-attempt request timeout
 * itself elapsing -- i.e. the vendor is hanging, not failing fast) is NOT retried: another
 * attempt would just cost another full timeout on this request-handling thread for little chance
 * of success. Other {@link IOException}s (connection refused/reset, which typically fail in
 * milliseconds) are retried. A retryable response's {@code Retry-After} header is honored when
 * present and within {@link #MAX_DELAY_MS}; otherwise backoff is exponential with jitter. A
 * non-retryable status (2xx, or a 4xx other than 429) is returned immediately on the first
 * attempt -- retrying an invalid API key or a bad request would waste time and, for some
 * vendors, risks tripping their own abuse detection.
 */
class HttpRetryCommand {

  private static final Log LOG = LogFactory.getLog(HttpRetryCommand.class);

  static final int MAX_ATTEMPTS = 3;
  private static final long BASE_DELAY_MS = 300;
  private static final long MAX_DELAY_MS = 2000;
  private static final long JITTER_MS = 100;

  /** RFC 7231-idempotent methods: repeating the same request is safe, so retry-on-failure is safe. */
  private static final Set<String> RETRYABLE_METHODS = Set.of("GET", "HEAD", "PUT", "DELETE");

  private HttpRetryCommand() {
  }

  static <T> HttpResponse<T> send(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
      throws IOException {
    if (!RETRYABLE_METHODS.contains(request.method())) {
      return sendOnce(client, request, bodyHandler);
    }

    IOException lastException = null;
    HttpResponse<T> lastResponse = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        HttpResponse<T> response = sendOnce(client, request, bodyHandler);
        lastException = null;
        if (!isRetryableStatus(response.statusCode())) {
          return response;
        }
        lastResponse = response;
        LOG.debug("Retryable status " + response.statusCode() + " from " + loggableUri(request.uri()) + " (attempt "
            + attempt + "/" + MAX_ATTEMPTS + ")");
      } catch (HttpTimeoutException e) {
        // The vendor is hanging rather than failing fast -- give up now rather than spend
        // another full per-attempt timeout on this thread for little chance of success.
        throw e;
      } catch (IOException e) {
        lastException = e;
        lastResponse = null;
        LOG.debug("Retryable exception from " + loggableUri(request.uri()) + " (attempt " + attempt + "/"
            + MAX_ATTEMPTS + "): " + e);
      }

      if (attempt == MAX_ATTEMPTS) {
        break;
      }
      Long retryAfterMs = lastResponse != null ? retryAfterMs(lastResponse) : null;
      if (retryAfterMs != null && retryAfterMs > MAX_DELAY_MS) {
        // The vendor asked for a wait longer than this synchronous call can reasonably honor --
        // stop rather than hammer it again after only a fraction of the requested delay.
        break;
      }
      if (!sleepBeforeRetry(retryAfterMs != null ? retryAfterMs : backoffMs(attempt))) {
        break;
      }
    }
    if (lastException != null) {
      throw lastException;
    }
    return lastResponse;
  }

  private static <T> HttpResponse<T> sendOnce(HttpClient client, HttpRequest request,
      HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
    try {
      return client.send(request, bodyHandler);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while sending request to " + loggableUri(request.uri()), e);
    }
  }

  private static boolean isRetryableStatus(int statusCode) {
    return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
  }

  /** The Retry-After header's delay in milliseconds, or null if absent/unparsable. */
  private static Long retryAfterMs(HttpResponse<?> response) {
    List<String> values = response.headers().allValues("Retry-After");
    if (values.isEmpty()) {
      return null;
    }
    String value = values.get(0).trim();
    try {
      long seconds = Long.parseLong(value);
      return seconds < 0 ? null : seconds * 1000;
    } catch (NumberFormatException e) {
      // Fall through to the HTTP-date form.
    }
    try {
      ZonedDateTime target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
      long millis = Duration.between(ZonedDateTime.now(target.getZone()), target).toMillis();
      return Math.max(millis, 0);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static long backoffMs(int attempt) {
    long backoff = (long) (BASE_DELAY_MS * Math.pow(2, attempt - 1));
    return Math.min(backoff + ThreadLocalRandom.current().nextLong(JITTER_MS), MAX_DELAY_MS);
  }

  /** Returns false (and restores the interrupt flag) if interrupted mid-sleep, so the caller stops retrying. */
  private static boolean sleepBeforeRetry(long delayMs) {
    try {
      Thread.sleep(delayMs);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** The URI without its query string, so a retry log line never repeats a credential passed as a query param. */
  private static String loggableUri(URI uri) {
    String authority = uri.getAuthority();
    String path = uri.getPath();
    return uri.getScheme() + "://" + (authority != null ? authority : "") + (path != null ? path : "");
  }
}
