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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.provided.net.ConnectAddressPin;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the real, unmodified {@link HttpGetCommand#executeUserUrl(String)} end to end
 * against a real local HTTP server, adapting the issue #760 investigation's loopback-server /
 * fake-hostname technique so it goes through the actual production pinning code instead of a
 * standalone servlet (see {@code com.simisinc.platform.provided.net.ConnectAddressPinResolverTest}
 * for the SPI mechanism tested in isolation).
 *
 * <p>{@code RemoteUrlValidationCommand.validate(...)} is the one thing stubbed here, and only to
 * supply what a hostname that legitimately validated as public would look like: there is no real
 * public server available to this test suite to point the real validator at. Everything
 * downstream of that stub -- {@link HttpGetCommand}'s own {@code ConnectAddressPin.set},
 * {@code execute}, its {@code finally}-block {@code clear}, the real {@code HttpClient}, and the
 * real {@code ConnectAddressResolverProvider} installed from
 * {@code target/simis-cms-ssrf-pin-resolver.jar} on the test classpath -- runs unmodified.
 *
 * <p>Test hostnames use a {@code *.example.com} shape rather than the {@code *.invalid} names
 * {@code ConnectAddressPinResolverTest} uses: {@code execute()} itself (unmocked here) runs its
 * own {@code UrlValidator} check before ever touching DNS, and {@code UrlValidator} rejects
 * {@code .invalid} as an unrecognized TLD outright, before pinning is ever in play (confirmed
 * empirically while writing this test -- see git history for the {@code .invalid} version that
 * failed this way). {@code example.com} is IANA-reserved for documentation (RFC 2606) with no
 * wildcard DNS record, so a random subdomain is a syntactically ordinary hostname, but real DNS
 * resolution of it is never exercised here: {@code validate()} is stubbed and the actual connect
 * target is always the pin, so nothing in this file depends on {@code example.com} being
 * reachable or its subdomains resolving one way or the other.
 *
 * @author Liz Houser
 * @created 7/31/2026
 */
class HttpGetCommandExecuteUserUrlPinTest {

  private static final String TRAP_BODY = "EXECUTE-USER-URL-PIN-OK";

  private HttpServer trapServer;

  @AfterEach
  void tearDown() {
    ConnectAddressPin.clear();
    if (trapServer != null) {
      trapServer.stop(0);
    }
  }

  @Test
  void executeUserUrlPinsTheValidatedAddressAndReachesTheTrap() throws Exception {
    int port = startTrapServer();
    String fakeHost = "executeuserurl-pin-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";
    RemoteUrlValidationCommand.ValidationResult validated =
        new RemoteUrlValidationCommand.ValidationResult(true, fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });

    String body;
    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(validated);
      body = HttpGetCommand.executeUserUrl(url);
    }

    assertEquals(TRAP_BODY, body);
  }

  @Test
  void executeUserUrlNeverConnectsWhenValidationRejectsTheUrl() {
    String fakeHost = "executeuserurl-blocked-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":1/";
    RemoteUrlValidationCommand.ValidationResult rejected =
        new RemoteUrlValidationCommand.ValidationResult(false, null, null);

    try (MockedStatic<RemoteUrlValidationCommand> validation = mockStatic(RemoteUrlValidationCommand.class)) {
      validation.when(() -> RemoteUrlValidationCommand.validate(url)).thenReturn(rejected);
      assertNull(HttpGetCommand.executeUserUrl(url));
    }
  }

  @Test
  void plainExecuteHonorsAPreExistingPinWithoutManagingItItself() throws Exception {
    // The OAuth/Stripe/MailChimp overloads must never manage the pin. Pre-seed one exactly as
    // executeUserUrl would have, then call the PLAIN execute() -- which never calls
    // RemoteUrlValidationCommand or ConnectAddressPin at all -- and confirm the request still
    // reaches the trap: proving execute() does not proactively clear or otherwise interfere
    // with a pin that is already active on the thread when it runs. (Whether execute() itself
    // never SETS a pin is a simpler code-inspection fact -- it has no ConnectAddressPin
    // reference at all -- rather than something meaningful to assert at runtime.)
    int port = startTrapServer();
    String fakeHost = "execute-plain-" + System.nanoTime() + ".simis-ssrf-pin-test.example.com";
    String url = "http://" + fakeHost + ":" + port + "/";

    ConnectAddressPin.set(fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });

    assertEquals(TRAP_BODY, HttpGetCommand.execute(url));
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
