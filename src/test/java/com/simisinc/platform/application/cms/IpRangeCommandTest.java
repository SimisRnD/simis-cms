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

package com.simisinc.platform.application.cms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author elizabeth houser
 */
class IpRangeCommandTest {

  // --- matches(): plain address, no CIDR ---

  @Test
  void plainAddressMatchesItselfExactly() {
    Assertions.assertTrue(IpRangeCommand.matches("203.0.113.5", "203.0.113.5"));
  }

  @Test
  void plainAddressDoesNotMatchADifferentAddress() {
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.5", "203.0.113.6"));
  }

  // --- matches(): IPv4 CIDR ---

  @Test
  void ipv4CidrMatchesAnAddressInsideTheRange() {
    Assertions.assertTrue(IpRangeCommand.matches("203.0.113.0/24", "203.0.113.200"));
  }

  @Test
  void ipv4CidrDoesNotMatchAnAddressOutsideTheRange() {
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.0/24", "203.0.114.1"));
  }

  @Test
  void ipv4Slash32MatchesOnlyItsExactAddress() {
    Assertions.assertTrue(IpRangeCommand.matches("203.0.113.5/32", "203.0.113.5"));
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.5/32", "203.0.113.6"));
  }

  @Test
  void ipv4SlashZeroMatchesAnyIpv4Address() {
    Assertions.assertTrue(IpRangeCommand.matches("0.0.0.0/0", "1.2.3.4"));
    Assertions.assertTrue(IpRangeCommand.matches("0.0.0.0/0", "255.255.255.255"));
  }

  @Test
  void ipv4CidrRespectsNonByteAlignedPrefix() {
    // 203.0.113.128/26 covers 203.0.113.128-191
    Assertions.assertTrue(IpRangeCommand.matches("203.0.113.128/26", "203.0.113.191"));
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.128/26", "203.0.113.192"));
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.128/26", "203.0.113.127"));
  }

  // --- matches(): IPv6 CIDR ---

  @Test
  void ipv6CidrMatchesAnAddressInsideTheRange() {
    Assertions.assertTrue(IpRangeCommand.matches("2001:db8::/32", "2001:db8::1"));
  }

  @Test
  void ipv6CidrDoesNotMatchAnAddressOutsideTheRange() {
    Assertions.assertFalse(IpRangeCommand.matches("2001:db8::/32", "2001:db9::1"));
  }

  @Test
  void ipv6Slash128MatchesOnlyItsExactAddress() {
    Assertions.assertTrue(IpRangeCommand.matches("2001:db8::1/128", "2001:db8::1"));
    Assertions.assertFalse(IpRangeCommand.matches("2001:db8::1/128", "2001:db8::2"));
  }

  // --- matches(): address-family safety ---

  @Test
  void ipv4RangeNeverMatchesAnIpv6Address() {
    Assertions.assertFalse(IpRangeCommand.matches("0.0.0.0/0", "2001:db8::1"));
  }

  @Test
  void ipv6RangeNeverMatchesAnIpv4Address() {
    Assertions.assertFalse(IpRangeCommand.matches("::/0", "203.0.113.5"));
  }

  // --- matches(): malformed input is a safe non-match, not an exception ---

  @Test
  void malformedPrefixLengthDoesNotMatchOrThrow() {
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.0/notanumber", "203.0.113.5"));
  }

  @Test
  void outOfRangePrefixLengthDoesNotMatchOrThrow() {
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.0/33", "203.0.113.5"));
    Assertions.assertFalse(IpRangeCommand.matches("2001:db8::/129", "2001:db8::1"));
  }

  @Test
  void nonIpNetworkPartDoesNotMatchOrThrow() {
    Assertions.assertFalse(IpRangeCommand.matches("not-an-ip/24", "203.0.113.5"));
  }

  @Test
  void blankOrNullInputsDoNotMatchOrThrow() {
    Assertions.assertFalse(IpRangeCommand.matches(null, "203.0.113.5"));
    Assertions.assertFalse(IpRangeCommand.matches("203.0.113.0/24", null));
    Assertions.assertFalse(IpRangeCommand.matches("", "203.0.113.5"));
  }

  // --- isValidAddressOrCidr(): used by SaveBlockedIPCommand/SaveAllowedIPCommand ---

  @Test
  void validatesPlainIpv4AndIpv6Addresses() {
    Assertions.assertTrue(IpRangeCommand.isValidAddressOrCidr("203.0.113.5"));
    Assertions.assertTrue(IpRangeCommand.isValidAddressOrCidr("2001:db8::1"));
  }

  @Test
  void validatesIpv4AndIpv6CidrRanges() {
    Assertions.assertTrue(IpRangeCommand.isValidAddressOrCidr("203.0.113.0/24"));
    Assertions.assertTrue(IpRangeCommand.isValidAddressOrCidr("2001:db8::/32"));
  }

  @Test
  void rejectsAnIpv4PrefixLengthAbove32() {
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr("203.0.113.0/33"));
  }

  @Test
  void rejectsAnIpv6PrefixLengthAbove128() {
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr("2001:db8::/129"));
  }

  @Test
  void rejectsNegativePrefixLength() {
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr("203.0.113.0/-1"));
  }

  @Test
  void rejectsGarbageAndBlankInput() {
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr("not-an-ip"));
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr(""));
    Assertions.assertFalse(IpRangeCommand.isValidAddressOrCidr(null));
  }

}
