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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.provided.net.ConnectAddressPin;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the real, unmodified {@link HttpDownloadFileCommand#executeUserUrl(String, java.io.File)}
 * end to end against a real local HTTP server -- the {@code HttpDownloadFileCommand} counterpart
 * of {@link HttpGetCommandExecuteUserUrlPinTest}, closing the gap issue #760's fix left open
 * (its commit message flagged this method as still exposed to DNS rebinding, since it only
 * called {@code isFetchAllowed} and never pinned the connect address).
 *
 * <p>{@code RemoteUrlValidationCommand.validate(...)} is the one thing stubbed here, and only to
 * supply what a hostname that legitimately validated as public would look like: there is no real
 * public server available to this test suite to point the real validator at. Everything
 * downstream of that stub -- {@link HttpDownloadFileCommand}'s own {@code ConnectAddressPin.set},
 * {@code execute}, its {@code finally}-block {@code clear}, the real {@code HttpClient}, and the
 * real {@code ConnectAddressResolverProvider} installed from
 * {@code target/simis-cms-ssrf-pin-resolver.jar} on the test classpath -- runs unmodified.
 *
 * <p>Test hostnames use a {@code *.example.com} shape for the same reason
 * {@code HttpGetCommandExecuteUserUrlPinTest} does: {@code execute()} itself (unmocked here) runs
 * its own {@code UrlValidator} check before ever touching DNS, and {@code UrlValidator} rejects
 * {@code .invalid} as an unrecognized TLD outright, before pinning is ever in play.
 * {@code example.com} is IANA-reserved for documentation (RFC 2606) with no wildcard DNS record,
 * so a random subdomain is a syntactically ordinary hostname, but real DNS resolution of it is
 * never exercised here: {@code validate()} is stubbed and the actual connect target is always the
 * pin, so nothing in this file depends on {@code example.com} being reachable or its subdomains
 * resolving one way or the other.
 *
 * @author Liz Houser
 * @created 7/31/2026
 */
class HttpDownloadFileCommandExecuteUserUrlPinTest {

  private static final String TRAP_BODY = "EXECUTE-USER-URL-PIN-OK";

  private HttpServer trapServer;
  private File tempFile;

  @AfterEach
  void tearDown() {
    ConnectAddressPin.clear();
    if (trapServer != null) {
      trapServer.stop(0);
    }
    if (tempFile != null && tempFile.exists()) {
      tempFile.delete();
    }
  }

  @Test
  void executeUserUrlPinsTheValidatedAddressAndReachesTheTrap() throws Exception {
    int port = startTrapServer();
    String fakeHost = "downloadfile-pin-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";
    RemoteUrlValidationCommand.ValidationResult validated =
        new RemoteUrlValidationCommand.ValidationResult(true, fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });
    tempFile = File.createTempFile("http-download-pin-test", ".tmp");

    boolean result;
    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(validated);
      result = HttpDownloadFileCommand.executeUserUrl(url, tempFile);
    }

    assertTrue(result);
    assertEquals(TRAP_BODY, Files.readString(tempFile.toPath()));
  }

  @Test
  void executeUserUrlNeverConnectsWhenValidationRejectsTheUrl() {
    String fakeHost = "downloadfile-blocked-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":1/";
    RemoteUrlValidationCommand.ValidationResult rejected =
        new RemoteUrlValidationCommand.ValidationResult(false, null, null);
    tempFile = new File(System.getProperty("java.io.tmpdir"), "http-download-pin-test-" + System.nanoTime() + ".tmp");

    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(rejected);
      assertFalse(HttpDownloadFileCommand.executeUserUrl(url, tempFile));
    }
    assertFalse(tempFile.exists(), "a rejected url must never be fetched, so no file should be written");
  }

  @Test
  void plainExecuteHonorsAPreExistingPinWithoutManagingItItself() throws Exception {
    // PERLSApiClientCommand calls the plain execute(...) directly against a fixed, operator-
    // controlled endpoint and must never have its own pin management -- pre-seed one exactly as
    // executeUserUrl would have, then call the PLAIN execute() -- which never calls
    // RemoteUrlValidationCommand or ConnectAddressPin at all -- and confirm the request still
    // reaches the trap: proving execute() does not proactively clear or otherwise interfere
    // with a pin that is already active on the thread when it runs.
    int port = startTrapServer();
    String fakeHost = "downloadfile-plain-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";
    tempFile = File.createTempFile("http-download-pin-test", ".tmp");

    ConnectAddressPin.set(fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });

    assertTrue(HttpDownloadFileCommand.execute(url, tempFile));
    assertEquals(TRAP_BODY, Files.readString(tempFile.toPath()));
  }

  private int startTrapServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = TRAP_BODY.getBytes();
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    trapServer = server;
    return server.getAddress().getPort();
  }
}
