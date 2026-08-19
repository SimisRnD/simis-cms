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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
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

  // -- applyTransportSecurity ------------------------------------------------------------------
  // Asserted against a real SimpleEmail rather than a mock: commons-email's setters are final, so
  // the type cannot be mocked (a trap this codebase has hit before), but a real instance's flags
  // read back fine and prove exactly what the SMTP transport would be told to do.

  @Test
  void appliesNeitherSchemeWhenBothAreOff() {
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "false", "false");
    assertFalse(email.isSSLOnConnect());
    assertFalse(email.isStartTLSEnabled());
    assertFalse(email.isStartTLSRequired());
  }

  @Test
  void appliesImplicitSslWhenOnlySslIsOn() {
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "true", "false");
    assertTrue(email.isSSLOnConnect());
    assertFalse(email.isStartTLSEnabled());
  }

  @Test
  void appliesStartTlsWhenOnlyStartTlsIsOn() {
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "false", "true");
    assertFalse(email.isSSLOnConnect());
    assertTrue(email.isStartTLSEnabled());
  }

  @Test
  void requiresStartTlsRatherThanMerelyEnablingIt() {
    // Enabled-without-required silently falls back to an unencrypted connection when the server
    // does not advertise STARTTLS, which would put the SMTP password on the wire in clear text.
    // An admin who turned this on asked for encryption, so the send must fail instead.
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "false", "true");
    assertTrue(email.isStartTLSRequired());
  }

  @Test
  void prefersImplicitSslAndDropsStartTlsWhenBothAreOn() {
    // The two are alternatives, not layers -- an already-encrypted connection has nothing to
    // upgrade. Enabling both is a misconfiguration; implicit SSL wins and STARTTLS is dropped
    // (a warning is logged) rather than both being handed to the transport.
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "true", "true");
    assertTrue(email.isSSLOnConnect());
    assertFalse(email.isStartTLSEnabled());
    assertFalse(email.isStartTLSRequired());
  }

  @Test
  void treatsBlankAndNullPropertyValuesAsOff() {
    // A site that predates the mail.starttls property reads it as null, and a site whose upgrade
    // seeded it reads "false" -- both must behave identically to the pre-change default.
    Email nullValues = new SimpleEmail();
    EmailCommand.applyTransportSecurity(nullValues, null, null);
    assertFalse(nullValues.isSSLOnConnect());
    assertFalse(nullValues.isStartTLSEnabled());

    Email blankValues = new SimpleEmail();
    EmailCommand.applyTransportSecurity(blankValues, "", "");
    assertFalse(blankValues.isSSLOnConnect());
    assertFalse(blankValues.isStartTLSEnabled());
  }

  @Test
  void onlyTheExactStringTrueEnablesAScheme() {
    // The stored property is a boolean-typed site property, which persists the literal "true" or
    // "false" -- anything else is not a value this code should treat as on.
    Email email = new SimpleEmail();
    EmailCommand.applyTransportSecurity(email, "TRUE", "yes");
    assertFalse(email.isSSLOnConnect());
    assertFalse(email.isStartTLSEnabled());
  }
}
