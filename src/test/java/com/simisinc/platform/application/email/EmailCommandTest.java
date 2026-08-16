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

package com.simisinc.platform.application.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link EmailCommand#categorizeSendFailure}, particularly the plain-{@link SocketException}
 * case added alongside {@code ConnectException}/{@code UnknownHostException}/{@code
 * NoRouteToHostException} -- a cloud platform's egress firewall blocking outbound SMTP (e.g.
 * Azure's default block on port 25) typically resets the connection rather than refusing it,
 * which throws a plain SocketException that the original category list didn't recognize and fell
 * through to "unknown".
 */
class EmailCommandTest {

  @Test
  void categorizesAuthenticationFailure() {
    assertEquals("auth", EmailCommand.categorizeSendFailure(new AuthenticationFailedException("bad credentials")));
  }

  @Test
  void categorizesRejectedSend() {
    assertEquals("rejected", EmailCommand.categorizeSendFailure(new SendFailedException("rejected")));
  }

  @Test
  void categorizesTlsFailure() {
    assertEquals("tls", EmailCommand.categorizeSendFailure(new SSLException("handshake failed")));
  }

  @Test
  void categorizesTimeout() {
    assertEquals("timeout", EmailCommand.categorizeSendFailure(new SocketTimeoutException("timed out")));
  }

  @Test
  void categorizesConnectExceptionAsConnect() {
    assertEquals("connect", EmailCommand.categorizeSendFailure(new ConnectException("Connection refused")));
  }

  @Test
  void categorizesUnknownHostAsConnect() {
    assertEquals("connect", EmailCommand.categorizeSendFailure(new UnknownHostException("smtp.example.invalid")));
  }

  @Test
  void categorizesNoRouteToHostAsConnect() {
    assertEquals("connect", EmailCommand.categorizeSendFailure(new NoRouteToHostException("No route to host")));
  }

  @Test
  void categorizesAPlainSocketExceptionAsConnect() {
    // The case this test guards: a blocked (not refused/unreachable) port -- e.g. a cloud
    // platform's firewall resetting an outbound connection on port 25 -- throws a bare
    // SocketException, not a ConnectException.
    assertEquals("connect", EmailCommand.categorizeSendFailure(new SocketException("Connection reset")));
  }

  @Test
  void aConnectExceptionStillCategorizesAsConnectDespiteBeingASocketExceptionSubclass() {
    // ConnectException IS-A SocketException, so this only stays correct if the specific check is
    // still reached before (or independently of) the new generic SocketException fallback --
    // guards against a future refactor accidentally reordering the checks in a way that changes
    // behavior, even though both branches currently return the same "connect" category.
    assertEquals("connect", EmailCommand.categorizeSendFailure(new ConnectException("Connection refused")));
  }

  @Test
  void walksTheCauseChainToFindTheCategorizableException() {
    Exception wrapped = new Exception("SMTP send failed", new ConnectException("Connection refused"));
    assertEquals("connect", EmailCommand.categorizeSendFailure(wrapped));
  }

  @Test
  void returnsUnknownForAnUncategorizedException() {
    assertEquals("unknown", EmailCommand.categorizeSendFailure(new IllegalStateException("something else entirely")));
  }
}
