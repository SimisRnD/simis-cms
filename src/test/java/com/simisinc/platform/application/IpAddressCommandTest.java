/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tests masking of the host portion of an IP address for analytics storage
 *
 * @author elizabeth houser
 */
class IpAddressCommandTest {

  @Test
  void ipv4IsMaskedToTheNetwork() {
    assertEquals("192.168.1.0", IpAddressCommand.anonymize("192.168.1.234"));
    assertEquals("8.8.8.0", IpAddressCommand.anonymize("8.8.8.8"));
    // Already-zeroed host is unchanged
    assertEquals("10.0.0.0", IpAddressCommand.anonymize("10.0.0.0"));
  }

  @Test
  void ipv6IsMaskedToTheNetwork() {
    // /48: the first three hextets are kept, the rest zeroed
    assertEquals("2001:db8:1234:0:0:0:0:0", IpAddressCommand.anonymize("2001:db8:1234:5678:9abc:def0:1234:5678"));
  }

  @Test
  void blankAndUnparseableValuesAreUnchanged() {
    assertEquals("", IpAddressCommand.anonymize(""));
    assertEquals(null, IpAddressCommand.anonymize(null));
    // Not an IP literal -> returned unchanged (no DNS lookup attempted)
    assertEquals("not-an-ip", IpAddressCommand.anonymize("not-an-ip"));
  }

  // --- forAction: the address that belongs on a record of an action (issue #1782) ---

  private static HttpServletRequest requestFrom(String remoteAddr) {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getRemoteAddr()).thenReturn(remoteAddr);
    return request;
  }

  @Test
  void theRequestsAddressWinsOverTheSessionsOwn() {
    // The bug this exists for: a session created at 9am on one network, an action performed at
    // 5pm on another. web.xml's 60 minute timeout is an IDLE one, so the session is still the
    // same object and still carries the address it was constructed with.
    assertEquals("203.0.113.9",
        IpAddressCommand.forAction(requestFrom("203.0.113.9"), "198.51.100.4"));
  }

  @Test
  void theSessionsAddressIsUsedWhenThereIsNoRequest() {
    // A caller with no request in hand is no worse off than before this change.
    assertEquals("198.51.100.4", IpAddressCommand.forAction(null, "198.51.100.4"));
  }

  @Test
  void aRequestWithNoAddressFallsBackRatherThanStoringBlank() {
    assertEquals("198.51.100.4",
        IpAddressCommand.forAction(requestFrom(null), "198.51.100.4"));
    assertEquals("198.51.100.4",
        IpAddressCommand.forAction(requestFrom("   "), "198.51.100.4"));
  }

  @Test
  void bothAbsentIsNullRatherThanAnException() {
    assertNull(IpAddressCommand.forAction(null, null));
  }

  @Test
  void theFullAddressIsKeptNotAnonymized() {
    // IpAddressCommand's own javadoc: mailing-list submissions are among the records that keep the
    // full address. forAction must not quietly route through anonymize().
    assertEquals("203.0.113.9",
        IpAddressCommand.forAction(requestFrom("203.0.113.9"), null));
  }
}
