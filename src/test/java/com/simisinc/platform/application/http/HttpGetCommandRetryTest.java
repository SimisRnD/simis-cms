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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Proves {@link HttpGetCommand#execute(String)} actually retries through {@link HttpRetryCommand}
 * on a real call path, not just that the two are theoretically wired together. See
 * {@link HttpPostCommandTest}'s header comment for why this is a hand-rolled ServerSocket
 * responder rather than com.sun.net.httpserver.HttpServer (JaCoCo cannot instrument it under
 * "ant ci-test"). {@link HttpRetryCommandTest} covers the retry/backoff mechanics themselves.
 */
class HttpGetCommandRetryTest {

  /** Starts a listener that responds to successive connections with each (status, body) pair in order. */
  private static int startServer(List<Integer> statuses, List<String> bodies) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    int port = serverSocket.getLocalPort();
    Deque<Integer> statusQueue = new ArrayDeque<>(statuses);
    Deque<String> bodyQueue = new ArrayDeque<>(bodies);
    Thread thread = new Thread(() -> {
      try (ServerSocket ss = serverSocket) {
        while (!statusQueue.isEmpty()) {
          try (Socket socket = ss.accept()) {
            int status = statusQueue.poll();
            String body = bodyQueue.poll();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // Consume the request line + headers so the client sees a clean response.
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
              // no-op
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
          }
        }
      } catch (IOException e) {
        // Surfaces to the test as a connection failure on the client side
      }
    });
    thread.setDaemon(true);
    thread.start();
    return port;
  }

  private static String url(int port) {
    // 127.0.0.1, not localhost: HttpGetCommand's own UrlValidator rejects a bare "localhost"
    // host as having no valid TLD, which would fail every request here before any connection.
    return "http://127.0.0.1:" + port + "/";
  }

  @Test
  void executeRetriesA503AndReturnsTheBodyOnceTheServerRecovers() throws IOException {
    int port = startServer(List.of(503, 200), List.of("", "recovered"));

    String result = HttpGetCommand.execute(url(port));

    assertEquals("recovered", result);
  }

  @Test
  void executeReturnsNullWithoutRetryingOnA404() throws IOException {
    int port = startServer(List.of(404), List.of("not found"));

    String result = HttpGetCommand.execute(url(port));

    assertNull(result);
  }

  @Test
  void httpDeleteCommandRetriesA503ThroughItsDelegationIntoHttpGetCommand() throws IOException {
    // DELETE is idempotent, so it's retried too -- proven here through the real
    // HttpDeleteCommand.execute(...) -> HttpGetCommand.execute(..., DELETE) delegation, not just
    // by reading the source.
    int port = startServer(List.of(503, 200), List.of("", "recovered"));

    String result = HttpDeleteCommand.execute(url(port));

    assertEquals("recovered", result);
  }
}
