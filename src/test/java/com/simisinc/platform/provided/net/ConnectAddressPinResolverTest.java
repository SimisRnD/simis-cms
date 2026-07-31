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

package com.simisinc.platform.provided.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import com.sun.net.httpserver.HttpServer;

/**
 * Proves the {@link ConnectAddressResolverProvider} SPI mechanism itself, in isolation from
 * {@code RemoteUrlValidationCommand}/{@code HttpGetCommand}: a hostname that cannot resolve via
 * real DNS (an RFC 2606-reserved {@code .invalid} name) connects successfully once
 * {@link ConnectAddressPin#set} pins it to a local trap server, and the identical hostname
 * fails to connect when nothing was pinned for it. The second half matters as much as the
 * first: it proves the trap server is reached ONLY through a deliberate pin, not through some
 * other leak (a real network path, a JVM-wide negative-DNS-cache quirk, an overly loose host
 * match). This is the same technique the issue #760 investigation used against a real Tomcat
 * container (see {@code ssrf-pin-resolver/README.md}), adapted into a repeatable unit test.
 *
 * <p>This test's forked JVM genuinely installs {@link ConnectAddressResolverProvider} as its
 * {@code java.net.spi.InetAddressResolverProvider} -- {@code target/simis-cms-ssrf-pin-resolver.jar}
 * is on {@code web.classpath} (see {@code build.xml}'s {@code prepare}/{@code pin-resolver-jar}
 * targets), which is on this test's classpath, and the JDK's {@code ServiceLoader} finds it via
 * the {@code META-INF/services} entry inside that jar. What this test does NOT cover is the
 * container-classloader-timing constraint documented on {@link ConnectAddressPin} (Tomcat's own
 * bootstrap performing the very first lookup of the JVM's life before any webapp classloader
 * exists) -- a flat single-classloader test JVM has no such race. That half is re-verified for
 * this change by the docker-compose rehearsal against a real Tomcat 11 container, not here.
 *
 * <p>Also worth knowing while reading this class: the JDK's own {@code InetAddress} cache
 * ({@code networkaddress.cache.ttl}) sits ABOVE this resolver SPI and can answer a later lookup
 * of the exact same hostname string from cache without calling this resolver again. That is
 * pre-existing JDK behavior unrelated to this fix (it already applied to every hostname
 * resolution in the JVM before this module existed) and does not weaken the security property
 * here -- within one request, whatever {@code RemoteUrlValidationCommand.validate} resolved is
 * exactly what gets pinned and connected to, cache or no cache. It does mean "clear the pin,
 * then reconnect to the same hostname and expect failure" is NOT a reliable test technique
 * (confirmed empirically while writing this test); see {@link #clearRemovesThePinFromThreadLocalStorage()}.
 *
 * @author Liz Houser
 * @created 7/31/2026
 */
class ConnectAddressPinResolverTest {

  private static final String TRAP_BODY = "PIN-TRAP-OK";

  private HttpServer trapServer;

  @AfterEach
  void tearDown() {
    ConnectAddressPin.clear();
    if (trapServer != null) {
      trapServer.stop(0);
    }
  }

  @Test
  void pinnedFakeHostnameConnectsToExactlyThePinnedAddress() throws Exception {
    int port = startTrapServer();
    String fakeHost = "pinned-" + System.nanoTime() + ".invalid";

    ConnectAddressPin.set(fakeHost, new InetAddress[] { InetAddress.getByName("127.0.0.1") });
    HttpResponse<String> response = get(fakeHost, port);

    assertEquals(200, response.statusCode());
    assertEquals(TRAP_BODY, response.body());
  }

  @Test
  void unpinnedFakeHostnameNeverReachesTheTrap() {
    // No ConnectAddressPin.set() for this hostname: real DNS resolution of a .invalid name
    // must fail exactly as it would with no resolver provider installed at all.
    assertThrows(IOException.class, () -> {
      int port = startTrapServer();
      String fakeHost = "unpinned-" + System.nanoTime() + ".invalid";
      get(fakeHost, port);
    });
  }

  @Test
  void clearRemovesThePinFromThreadLocalStorage() throws Exception {
    // Deliberately NOT "pin, clear, then reconnect and expect failure": the JDK's OWN
    // InetAddress cache (networkaddress.cache.ttl) sits ABOVE this resolver SPI and, once a
    // hostname resolves successfully, can keep answering later lookups of that EXACT hostname
    // string from its cache without ever calling this resolver again -- confirmed empirically
    // while building this test (a cleared pin's hostname kept "resolving" to the stale pinned
    // address on a second real connect attempt, purely from that ambient cache, not from any
    // bug in clear()). That JDK-level cache is pre-existing JDK behavior, unrelated to this
    // fix, and does not weaken it: within a single request, whatever validate() resolved is
    // exactly what gets pinned and connected to either way. But it does make "reconnect and
    // expect failure" an unreliable way to prove clear() itself works. Asserting directly on
    // the ThreadLocal state instead sidesteps that confound entirely.
    String fakeHost = "clear-" + System.nanoTime() + ".invalid";
    InetAddress[] addresses = { InetAddress.getByName("127.0.0.1") };

    ConnectAddressPin.set(fakeHost, addresses);
    assertArrayEquals(addresses, ConnectAddressPin.get(fakeHost));

    ConnectAddressPin.clear();
    assertNull(ConnectAddressPin.get(fakeHost));
  }

  private HttpResponse<String> get(String host, int port) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
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
