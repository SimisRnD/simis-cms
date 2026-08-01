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
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.provided.net.ConnectAddressPin;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the new SSRF-guarded {@code HttpPostCommand.executeUserUrl*} methods (issue #418)
 * end to end against a real local HTTP server, the same technique
 * {@code HttpGetCommandExecuteUserUrlPinTest} uses for the GET side: a blocked url must never
 * connect, and an allowed url must reach the real server with its DNS pinned via
 * {@link ConnectAddressPin}.
 */
class HttpPostCommandExecuteUserUrlTest {

  private HttpServer trapServer;

  @AfterEach
  void tearDown() {
    ConnectAddressPin.clear();
    if (trapServer != null) {
      trapServer.stop(0);
    }
  }

  @Test
  void executeUserUrlPostsToTheValidatedAddressAndReachesTheTrap() throws Exception {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    int port = startTrapServer(200, "TRAP-OK", receivedBody);
    String fakeHost = "httppost-pin-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";
    RemoteUrlValidationCommand.ValidationResult validated = new RemoteUrlValidationCommand.ValidationResult(
        true, fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");

    String body;
    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(validated);
      body = HttpPostCommand.executeUserUrl(url, headers, "{\"hello\":\"world\"}", HttpPostCommand.POST);
    }

    assertEquals("TRAP-OK", body);
    assertEquals("{\"hello\":\"world\"}", receivedBody.get());
  }

  @Test
  void executeUserUrlNeverConnectsWhenValidationRejectsTheUrl() {
    String fakeHost = "httppost-blocked-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":1/";
    RemoteUrlValidationCommand.ValidationResult rejected = new RemoteUrlValidationCommand.ValidationResult(false, null, null);

    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(rejected);
      assertNull(HttpPostCommand.executeUserUrl(url, null, "{}", HttpPostCommand.POST));
      assertNull(HttpPostCommand.executeUserUrlWithResponse(url, null, "{}", HttpPostCommand.POST));
    }
  }

  @Test
  void executeUserUrlWithResponseReturnsTheStatusCodeAndBodyEvenForANon2xx() throws Exception {
    AtomicReference<String> receivedBody = new AtomicReference<>();
    int port = startTrapServer(503, "Service Unavailable", receivedBody);
    String fakeHost = "httppost-503-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";
    RemoteUrlValidationCommand.ValidationResult validated = new RemoteUrlValidationCommand.ValidationResult(
        true, fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });

    HttpPostCommand.HttpPostResult result;
    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(validated);
      result = HttpPostCommand.executeUserUrlWithResponse(url, null, "{}", HttpPostCommand.POST);
    }

    assertEquals(503, result.getStatusCode());
    assertEquals("Service Unavailable", result.getBody());

    // The body-only overload treats a non-2xx as null, same contract as execute(...).
    String fakeHost2 = "httppost-503b-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url2 = "http://" + fakeHost2 + ":" + port + "/";
    RemoteUrlValidationCommand.ValidationResult validated2 = new RemoteUrlValidationCommand.ValidationResult(
        true, fakeHost2, new InetAddress[] { InetAddress.getByName("127.0.0.1") });
    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url2)).thenReturn(validated2);
      assertNull(HttpPostCommand.executeUserUrl(url2, null, "{}", HttpPostCommand.POST));
    }
  }

  private int startTrapServer(int statusCode, String responseBody, AtomicReference<String> receivedBody) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(statusCode, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    trapServer = server;
    return server.getAddress().getPort();
  }
}
