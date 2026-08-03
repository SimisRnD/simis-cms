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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HttpRetryCommand} against a real local listener that can serve a different,
 * pre-scripted response (or a malformed one) on each successive attempt -- see
 * {@link HttpPostCommandTest}'s header comment for why this is a hand-rolled ServerSocket
 * responder rather than com.sun.net.httpserver.HttpServer (JaCoCo cannot instrument it under
 * "ant ci-test").
 *
 * <p>Uses a malformed (non-HTTP) response, not an early connection close, to simulate a transient
 * I/O failure: the JDK's own {@code HttpClient} transparently retries a GET whose connection was
 * closed before any response bytes arrived, entirely beneath {@link HttpRetryCommand}'s own
 * try/catch -- a test built on that technique would pass even if this class's retry loop were
 * deleted. A malformed response has already been partially received, so it is not eligible for
 * that transparent retry and reliably surfaces as a real {@link IOException} to this class.
 */
class HttpRetryCommandTest {

  private record ScriptedResponse(Integer statusCode, String body, String retryAfterHeader, boolean isMalformed) {
    static ScriptedResponse status(int statusCode, String body) {
      return new ScriptedResponse(statusCode, body, null, false);
    }

    static ScriptedResponse status(int statusCode, String body, String retryAfterHeader) {
      return new ScriptedResponse(statusCode, body, retryAfterHeader, false);
    }

    /** Not a valid HTTP response -- the client sees a real parse failure, not a clean early close. */
    static ScriptedResponse malformed() {
      return new ScriptedResponse(null, null, null, true);
    }
  }

  private static int startServer(List<ScriptedResponse> script, AtomicInteger connectionCount) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    int port = serverSocket.getLocalPort();
    Deque<ScriptedResponse> queue = new ArrayDeque<>(script);
    Thread thread = new Thread(() -> {
      try (ServerSocket ss = serverSocket) {
        while (!queue.isEmpty()) {
          try (Socket socket = ss.accept()) {
            connectionCount.incrementAndGet();
            ScriptedResponse next = queue.poll();
            OutputStream out = socket.getOutputStream();
            if (next.isMalformed()) {
              out.write("NOT AN HTTP RESPONSE AT ALL\r\n\r\n".getBytes(StandardCharsets.UTF_8));
              out.flush();
              continue;
            }
            byte[] bodyBytes = next.body().getBytes(StandardCharsets.UTF_8);
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 ").append(next.statusCode()).append(" STATUS\r\n");
            response.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            if (next.retryAfterHeader() != null) {
              response.append("Retry-After: ").append(next.retryAfterHeader()).append("\r\n");
            }
            response.append("Connection: close\r\n\r\n");
            out.write(response.toString().getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
            out.flush();
          }
        }
        // Queue exhausted: closing the socket (the try-with-resources above) means any further
        // connection attempt is refused, not silently accepted -- used by the "last exception"
        // test below to get a distinguishable failure on the final attempt.
      } catch (IOException e) {
        // Surfaces to the test as a connection failure on the client side
      }
    });
    thread.setDaemon(true);
    thread.start();
    return port;
  }

  private static HttpRequest requestFor(int port, String method) {
    // 127.0.0.1, not localhost: consistent with HttpPostCommandTest's rationale, and avoids any
    // dependency on this JVM's hosts-file/DNS resolution for a bare "localhost".
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + "/"));
    switch (method) {
      case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody());
      case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.noBody());
      case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.noBody());
      case "DELETE" -> builder.DELETE();
      default -> builder.GET();
    }
    return builder.build();
  }

  @Test
  void succeedsOnTheFirstAttemptWithoutRetrying() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(200, "ok")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("ok", response.body());
    assertEquals(1, connections.get());
  }

  @Test
  void retriesA503AndSucceedsOnceTheServerRecovers() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(503, ""), ScriptedResponse.status(200, "recovered")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("recovered", response.body());
    assertEquals(2, connections.get());
  }

  @Test
  void retriesA429AndSucceedsOnceTheServerRecovers() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(429, ""), ScriptedResponse.status(200, "recovered")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals(2, connections.get());
  }

  @Test
  void doesNotRetryA404() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(404, "not found")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(404, response.statusCode());
    assertEquals(1, connections.get());
  }

  @Test
  void givesUpAfterMaxAttemptsOnAPersistentFailure() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(
        List.of(ScriptedResponse.status(503, ""), ScriptedResponse.status(503, ""), ScriptedResponse.status(503, "")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(503, response.statusCode());
    assertEquals(HttpRetryCommand.MAX_ATTEMPTS, connections.get());
  }

  @Test
  void retriesAMalformedResponseAndSucceedsOnceTheServerRecovers() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.malformed(), ScriptedResponse.status(200, "recovered")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("recovered", response.body());
    assertEquals(2, connections.get());
  }

  @Test
  void throwsAfterExhaustingRetriesOnAPersistentConnectionFailure() {
    // Nothing is listening on this port, so every attempt fails to connect at all.
    HttpRequest request = requestFor(1, "GET");
    long start = System.nanoTime();

    assertThrows(IOException.class, () -> HttpRetryCommand.send(HttpClient.newHttpClient(), request,
        HttpResponse.BodyHandlers.ofString()));

    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    // 2 backoff sleeps happen between the 3 attempts (300ms then ~600ms minimum) -- a regression
    // that collapsed the loop to a single attempt would finish in a few milliseconds instead.
    assertTrue(elapsedMs >= 800,
        "expected at least 2 backoff sleeps across " + HttpRetryCommand.MAX_ATTEMPTS + " attempts, took " + elapsedMs
            + "ms");
  }

  @Test
  void theExceptionThatSurfacesIsFromTheLastAttemptNotAnEarlierOne() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    // Only one scripted response: the server closes after serving it, so attempts 2+ get
    // connection-refused -- a different exception flavor than attempt 1's malformed-response
    // parse failure, proving the LAST attempt's exception is what surfaces, not the first.
    int port = startServer(List.of(ScriptedResponse.malformed()), connections);
    HttpRequest request = requestFor(port, "GET");

    IOException thrown = assertThrows(IOException.class, () -> HttpRetryCommand.send(HttpClient.newHttpClient(),
        request, HttpResponse.BodyHandlers.ofString()));

    assertTrue(thrown instanceof ConnectException,
        "expected the LAST attempt's connection-refused exception to surface, not attempt 1's malformed-response "
            + "exception; got " + thrown.getClass() + ": " + thrown.getMessage());
  }

  @Test
  void returnsTheFinalStatusRatherThanAnEarlierAttemptsStaleExceptionWhenAttemptsMixFailureModes() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.malformed(), ScriptedResponse.status(503, "still down"),
        ScriptedResponse.status(503, "still down")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    // The final (3rd) attempt produced a real HTTP response, so that must be what's returned --
    // not attempt 1's exception, which a real caller would otherwise see thrown instead of a
    // normal "received a bad status" outcome.
    assertEquals(503, response.statusCode());
    assertEquals(3, connections.get());
  }

  @Test
  void doesNotRetryOnAPerAttemptTimeoutSinceTheVendorIsHangingNotFailingFast() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startHangingServer(connections);
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + "/"))
        .timeout(Duration.ofMillis(200)).GET().build();

    assertThrows(HttpTimeoutException.class, () -> HttpRetryCommand.send(HttpClient.newHttpClient(), request,
        HttpResponse.BodyHandlers.ofString()));

    assertEquals(1, connections.get(), "a per-attempt timeout means the vendor is hanging -- retrying would just "
        + "cost another full timeout for little chance of success");
  }

  private static int startHangingServer(AtomicInteger connectionCount) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    int port = serverSocket.getLocalPort();
    Thread thread = new Thread(() -> {
      try (ServerSocket ss = serverSocket; Socket socket = ss.accept()) {
        connectionCount.incrementAndGet();
        Thread.sleep(5000);
      } catch (IOException | InterruptedException e) {
        // Test teardown races (client already gave up and closed) are expected here.
      }
    });
    thread.setDaemon(true);
    thread.start();
    return port;
  }

  @Test
  void honorsARetryAfterHeaderInsteadOfExponentialBackoffOnA429() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(
        List.of(ScriptedResponse.status(429, "", "1"), ScriptedResponse.status(200, "recovered")), connections);
    long start = System.nanoTime();

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertEquals(200, response.statusCode());
    assertEquals("recovered", response.body());
    assertTrue(elapsedMs >= 900,
        "expected the 1-second Retry-After to be honored instead of the ~300-700ms default backoff, took "
            + elapsedMs + "ms");
  }

  @Test
  void givesUpWithoutRetryingWhenRetryAfterExceedsTheDelayCap() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(429, "slow down", "999")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "GET"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(429, response.statusCode());
    assertEquals(1, connections.get(),
        "a Retry-After longer than this synchronous call can honor should stop retrying, not under-wait");
  }

  @Test
  void doesNotRetryPostSincePostIsNotIdempotent() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(503, "")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "POST"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(503, response.statusCode());
    assertEquals(1, connections.get());
  }

  @Test
  void doesNotRetryPatchSincePatchIsNotIdempotent() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(503, "")), connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "PATCH"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(503, response.statusCode());
    assertEquals(1, connections.get());
  }

  @Test
  void retriesPutSincePutIsIdempotent() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(503, ""), ScriptedResponse.status(200, "recovered")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "PUT"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals(2, connections.get());
  }

  @Test
  void retriesDeleteSinceDeleteIsIdempotent() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    int port = startServer(List.of(ScriptedResponse.status(503, ""), ScriptedResponse.status(200, "recovered")),
        connections);

    HttpResponse<String> response = HttpRetryCommand.send(HttpClient.newHttpClient(), requestFor(port, "DELETE"),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals(2, connections.get());
  }
}
