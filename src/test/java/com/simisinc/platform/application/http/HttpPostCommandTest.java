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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HttpPostCommand} against a real local HTTP listener rather than mocking
 * java.net.http.HttpClient itself -- the entire point of {@link HttpPostCommand#executeForStatusCode}
 * is a real status-code vs. response-body distinction, which a mock of the method under test cannot
 * verify. The listener is a minimal hand-rolled java.net.ServerSocket responder (not
 * com.sun.net.httpserver.HttpServer): this codebase's JaCoCo agent version cannot instrument the JDK's
 * platform-module httpserver classes, so a real jdk.httpserver-based test listener fails under
 * "ant ci-test" specifically, even though the request/response semantics being verified here have
 * nothing to do with that module.
 *
 * @author SimIS Inc.
 */
class HttpPostCommandTest {

  private record ReceivedRequest(String method, Map<String, String> headers) {
  }

  /** Starts a one-shot listener that responds to the first connection with the given status/body. */
  private static int startServer(int status, String body, AtomicReference<ReceivedRequest> captured) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    int port = serverSocket.getLocalPort();
    Thread thread = new Thread(() -> {
      try (ServerSocket ss = serverSocket; Socket socket = ss.accept()) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        String method = requestLine == null ? null : requestLine.split(" ")[0];
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
          int colon = line.indexOf(':');
          if (colon > 0) {
            headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
          }
        }
        if (captured != null) {
          captured.set(new ReceivedRequest(method, headers));
        }

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(status).append(" STATUS\r\n");
        response.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        response.append("Connection: close\r\n\r\n");

        OutputStream out = socket.getOutputStream();
        out.write(response.toString().getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
      } catch (IOException e) {
        // Surfaces to the test as a connection failure on the client side
      }
    });
    thread.setDaemon(true);
    thread.start();
    return port;
  }

  private static String url(int port) {
    // 127.0.0.1, not localhost: HttpPostCommand's own UrlValidator (unchanged by this rework)
    // rejects a bare "localhost" host as having no valid TLD, which would silently fail every
    // request here at the pre-flight validation step before any real connection is attempted.
    return "http://127.0.0.1:" + port + "/";
  }

  @Test
  void executeReturnsTheBodyOnA200WithContent() throws IOException {
    int port = startServer(200, "hello world", null);

    String result = HttpPostCommand.execute(url(port), null, "payload");

    assertEquals("hello world", result);
  }

  @Test
  void executeReturnsNullOnA204EmptyBodySuccess() throws IOException {
    int port = startServer(204, "", null);

    String result = HttpPostCommand.execute(url(port), null, "payload");

    assertNull(result, "the body-returning overload cannot distinguish empty-success from failure");
  }

  @Test
  void executeReturnsNullOnA404() throws IOException {
    int port = startServer(404, "not found", null);

    String result = HttpPostCommand.execute(url(port), null, "payload");

    assertNull(result);
  }

  @Test
  void executeForStatusCodeReturnsTheStatusOnA204EmptyBody() throws IOException {
    int port = startServer(204, "", null);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "", HttpPostCommand.POST);

    assertEquals(204, status);
  }

  @Test
  void executeForStatusCodeReturnsTheStatusOnA200WithContent() throws IOException {
    int port = startServer(200, "hello world", null);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "payload", HttpPostCommand.POST);

    assertEquals(200, status);
  }

  @Test
  void executeForStatusCodeReturnsTheStatusOnAFailure() throws IOException {
    int port = startServer(404, "not found", null);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "payload", HttpPostCommand.POST);

    assertEquals(404, status);
  }

  @Test
  void executeForStatusCodeReturnsMinusOneForABlankUrl() {
    int status = HttpPostCommand.executeForStatusCode("", null, "payload", HttpPostCommand.POST);

    assertEquals(-1, status);
  }

  @Test
  void executeForStatusCodeReturnsMinusOneForAnInvalidUrl() {
    int status = HttpPostCommand.executeForStatusCode("not-a-url", null, "payload", HttpPostCommand.POST);

    assertEquals(-1, status);
  }

  @Test
  void executeForStatusCodeSendsAPutWhenRequested() throws IOException {
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "", captured);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "payload", HttpPostCommand.PUT);

    assertEquals(200, status);
    assertEquals("PUT", captured.get().method());
  }

  @Test
  void executeForStatusCodeSendsProvidedHeaders() throws IOException {
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "", captured);
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic dGVzdA==");

    HttpPostCommand.executeForStatusCode(url(port), headers, "payload", HttpPostCommand.POST);

    assertEquals("Basic dGVzdA==", captured.get().headers().get("Authorization"));
  }

  @Test
  void executeForStatusCodeSendsAPatchWhenRequested() throws IOException {
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "", captured);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "payload", HttpPostCommand.PATCH);

    assertEquals(200, status);
    assertEquals("PATCH", captured.get().method());
  }

  @Test
  void executeSendsAPostByDefault() throws IOException {
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "ok", captured);

    Map<String, String> params = new HashMap<>();
    params.put("a", "1");
    HttpPostCommand.execute(url(port), params);

    assertEquals("POST", captured.get().method());
  }
}
