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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.HashMap;
import java.util.List;
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
    return startServer(List.of(status), List.of(body), captured);
  }

  /**
   * Starts a listener that responds to successive connections with each (status, body) pair in
   * order -- used to prove that {@link HttpPostCommand} actually retries through
   * {@link HttpRetryCommand} rather than just being wired to a mechanism no real call path uses.
   */
  private static int startServer(List<Integer> statuses, List<String> bodies, AtomicReference<ReceivedRequest> captured)
      throws IOException {
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
  void executeDoesNotRetryA503OnAPostSincePostIsNotIdempotent() throws IOException {
    // A 5xx can arrive after a vendor already accepted and acted on a POST (e.g. sent a
    // campaign) -- HttpRetryCommand deliberately never retries POST/PATCH, so this must behave
    // exactly as it did before retry/backoff existed: one attempt, null on failure.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(List.of(503), List.of(""), captured);

    String result = HttpPostCommand.execute(url(port), null, "payload");

    assertNull(result);
    assertEquals("POST", captured.get().method());
  }

  @Test
  void executeForStatusCodeRetriesA503AndReturnsTheStatusOnceAPutRecovers() throws IOException {
    // PUT is idempotent, unlike POST/PATCH, so it IS retried.
    int port = startServer(List.of(503, 200), List.of("", "recovered"), null);

    int status = HttpPostCommand.executeForStatusCode(url(port), null, "payload", HttpPostCommand.PUT);

    assertEquals(200, status);
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

  @Test
  void executeWithResponseKeepsTheBodyOfA400() throws IOException {
    // The case issue 1616 is about. Cloudflare answers a wrong Turnstile secret with exactly this
    // shape, and execute() drops it -- so CaptchaCommand could only report "Remote content is
    // empty" and an operator had no way to tell a bad secret from a network fault.
    String cloudflareStyle = "{\"error-codes\":[\"invalid-input-secret\"],\"success\":false}";
    int port = startServer(400, cloudflareStyle, null);

    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url(port), null, "payload",
        HttpPostCommand.POST);

    assertNotNull(result);
    assertEquals(400, result.getStatusCode());
    assertEquals(cloudflareStyle, result.getBody(), "the explanation must survive the non-2xx status");
    assertFalse(result.isSuccess());
  }

  @Test
  void executeDiscardsTheSameBodyExecuteWithResponseKeeps() throws IOException {
    // Pins the contrast rather than describing it: same response, two overloads, and the reason
    // the new one had to exist.
    int port = startServer(400, "{\"error-codes\":[\"invalid-input-secret\"]}", null);

    assertNull(HttpPostCommand.execute(url(port), null, "payload"));
  }

  @Test
  void executeWithResponseReturnsStatusAndBodyOnA200() throws IOException {
    int port = startServer(200, "hello world", null);

    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url(port), null, "payload",
        HttpPostCommand.POST);

    assertNotNull(result);
    assertEquals(200, result.getStatusCode());
    assertEquals("hello world", result.getBody());
    assertTrue(result.isSuccess());
  }

  @Test
  void executeWithResponseReturnsNullWhenTheRequestCannotBeSent() {
    // A url that fails pre-flight validation never reaches a server, so there is no status to
    // report -- null, not a result carrying a synthetic status.
    assertNull(HttpPostCommand.executeWithResponse("not a url", null, "payload", HttpPostCommand.POST));
    assertNull(HttpPostCommand.executeWithResponse("", null, "payload", HttpPostCommand.POST));
  }

  @Test
  void executeWithResponseFormEncodesParameters() throws IOException {
    // The single-argument overload is the one CaptchaCommand uses; it must still send a form body.
    int port = startServer(200, "ok", null);
    Map<String, String> parameters = new HashMap<>();
    parameters.put("secret", "s3cret");
    parameters.put("response", "token");

    HttpPostCommand.HttpPostResult result = HttpPostCommand.executeWithResponse(url(port), parameters);

    assertNotNull(result);
    assertEquals(200, result.getStatusCode());
  }


  @Test
  void aFormEncodedPostDeclaresItsEncoding() throws IOException {
    // Issue 1624. These overloads build an "a=1&b=2" body and used to send it with no
    // Content-Type, because Java's HttpClient adds none. Whether that worked was up to the remote:
    // Google's siteverify accepted it, Cloudflare's answered 415 and named the missing header, so
    // Turnstile verification could never have succeeded on any secret.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "{}", captured);
    Map<String, String> parameters = new HashMap<>();
    parameters.put("secret", "s3cret");
    parameters.put("response", "token");

    HttpPostCommand.execute(url(port), parameters);

    assertEquals("application/x-www-form-urlencoded", captured.get().headers().get("Content-Type"));
  }

  @Test
  void aCallersOwnContentTypeIsNotOverridden() throws IOException {
    // MailChimp posts JSON through the string overload; guessing on its behalf would break it.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "{}", captured);
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    Map<String, String> parameters = new HashMap<>();
    parameters.put("a", "1");

    HttpPostCommand.execute(url(port), headers, parameters, HttpPostCommand.POST);

    assertEquals("application/json", captured.get().headers().get("Content-Type"));
  }

  @Test
  void aCallersContentTypeIsHonouredWhateverItsCase() throws IOException {
    // Header names are case-insensitive; matching only "Content-Type" would send two of them.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "{}", captured);
    Map<String, String> headers = new HashMap<>();
    headers.put("content-type", "application/json");
    Map<String, String> parameters = new HashMap<>();
    parameters.put("a", "1");

    HttpPostCommand.execute(url(port), headers, parameters, HttpPostCommand.POST);

    assertNull(captured.get().headers().get("Content-Type"),
        "the caller's lower-case header must not be joined by a second one");
    assertEquals("application/json", captured.get().headers().get("content-type"));
  }

  @Test
  void theCallersHeaderMapIsNotMutated() throws IOException {
    // A caller that reuses its header map across calls must not silently acquire this.
    int port = startServer(200, "{}", null);
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic dGVzdA==");
    Map<String, String> parameters = new HashMap<>();
    parameters.put("a", "1");

    HttpPostCommand.execute(url(port), headers, parameters, HttpPostCommand.POST);

    assertEquals(1, headers.size(), "the caller's map must come back as it went in");
    assertFalse(headers.containsKey("Content-Type"));
  }

  @Test
  void aStringBodyIsNotGivenAFormContentType() throws IOException {
    // Only the parameters overloads know the body is form-encoded, because they encoded it. A raw
    // string could be anything, and labelling it would be a guess.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "{}", captured);

    HttpPostCommand.execute(url(port), null, "{\"already\":\"json\"}");

    assertNull(captured.get().headers().get("Content-Type"));
  }


  @Test
  void executeWithResponseAlsoDeclaresTheFormEncoding() throws IOException {
    // The regression that shipped. Issue 1616 added this as a second form-encoding entry point and
    // moved the captcha onto it; the issue 1624 fix then landed on execute(), the overload the
    // captcha had stopped calling. The header was declared on a path nothing used, and Turnstile
    // went on answering 415 with the fix supposedly deployed.
    AtomicReference<ReceivedRequest> captured = new AtomicReference<>();
    int port = startServer(200, "{}", captured);
    Map<String, String> parameters = new HashMap<>();
    parameters.put("secret", "s3cret");
    parameters.put("response", "token");

    HttpPostCommand.executeWithResponse(url(port), parameters);

    assertEquals("application/x-www-form-urlencoded", captured.get().headers().get("Content-Type"));
  }

  @Test
  void everyFormEncodingEntryPointDeclaresTheEncoding() throws IOException {
    // Asserted together rather than one test each, so adding a third entry point that forgets the
    // header fails here rather than in production against whichever remote is strict about it.
    Map<String, String> parameters = new HashMap<>();
    parameters.put("a", "1");

    AtomicReference<ReceivedRequest> viaExecute = new AtomicReference<>();
    HttpPostCommand.execute(url(startServer(200, "{}", viaExecute)), parameters);

    AtomicReference<ReceivedRequest> viaWithResponse = new AtomicReference<>();
    HttpPostCommand.executeWithResponse(url(startServer(200, "{}", viaWithResponse)), parameters);

    assertEquals("application/x-www-form-urlencoded", viaExecute.get().headers().get("Content-Type"));
    assertEquals("application/x-www-form-urlencoded", viaWithResponse.get().headers().get("Content-Type"));
  }


  @Test
  void aCredentialInAQueryStringIsNotWrittenToALog() {
    // Issue 1615: Google's reCAPTCHA Enterprise assessment endpoint takes its API key as ?key=,
    // which is the form Google's own instructions print, so the credential is part of the url by
    // design. Every url this class logs goes through here first -- including the DEBUG lines, since
    // DEBUG is exactly the level someone turns on while chasing the failure.
    assertEquals("https://example.org/v1/assessments?key=REDACTED",
        HttpPostCommand.redactUrl("https://example.org/v1/assessments?key=AIzaSyTOPSECRET"));
    assertEquals("https://example.org/a?x=1&api_key=REDACTED&y=2",
        HttpPostCommand.redactUrl("https://example.org/a?x=1&api_key=hunter2&y=2"));
    assertEquals("https://example.org/a?access_token=REDACTED",
        HttpPostCommand.redactUrl("https://example.org/a?access_token=abc"));
    assertEquals("https://example.org/a?KEY=REDACTED",
        HttpPostCommand.redactUrl("https://example.org/a?KEY=abc"), "header names vary in case");
  }

  @Test
  void aUrlWithNothingSecretInItIsLeftAlone() {
    assertEquals("https://example.org/a?page=2&sort=name",
        HttpPostCommand.redactUrl("https://example.org/a?page=2&sort=name"));
    assertNull(HttpPostCommand.redactUrl(null));
  }

}
