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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.net.InetAddressUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Matches a single IP address against either a plain address entry or a CIDR range entry
 * (e.g. "203.0.113.0/24"), used by the allow/deny/block lists in {@link BlockedIPListCommand}.
 * <p>
 * The network part of any pattern is validated as a literal IPv4/IPv6 address with
 * {@link InetAddressUtils} before it's ever handed to {@link InetAddress#getByName(String)} -
 * that method silently attempts a DNS lookup for anything it doesn't recognize as a literal
 * address, which would be both slow and inappropriate for admin-entered firewall data.
 *
 * @author elizabeth houser
 */
public class IpRangeCommand {

  private static Log LOG = LogFactory.getLog(IpRangeCommand.class);

  private IpRangeCommand() {
  }

  /**
   * @param value a plain IPv4/IPv6 address, or a CIDR range like "203.0.113.0/24" or "2001:db8::/32"
   * @return true if value is a valid address or a valid CIDR range for its address family
   */
  public static boolean isValidAddressOrCidr(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }
    String[] parts = value.split("/", 2);
    boolean isV4 = InetAddressUtils.isIPv4(parts[0]);
    boolean isV6 = !isV4 && InetAddressUtils.isIPv6(parts[0]);
    if (!isV4 && !isV6) {
      return false;
    }
    if (parts.length == 1) {
      return true;
    }
    try {
      int prefixLength = Integer.parseInt(parts[1]);
      int maxPrefixLength = isV4 ? 32 : 128;
      return prefixLength >= 0 && prefixLength <= maxPrefixLength;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * @param pattern     a plain IP address, or a CIDR range such as "203.0.113.0/24"
   * @param ipAddress   a single, literal IP address to test
   * @return true if ipAddress equals pattern, or falls within pattern's CIDR range
   */
  public static boolean matches(String pattern, String ipAddress) {
    if (StringUtils.isBlank(pattern) || StringUtils.isBlank(ipAddress)) {
      return false;
    }
    if (!pattern.contains("/")) {
      // Case-insensitive: IPv6 literals are conventionally written with either case for their hex
      // digits (e.g. "2001:DB8::1" and "2001:db8::1" are the same address), and this method's non-
      // CIDR branch was a case-sensitive equals() -- most visibly a gap in the new cross-list
      // overlap check (SaveAllowedIPCommand/SaveBlockedIPCommand#findCoveringEntry), which could
      // miss a same-address-different-case entry on the other list as an overlap.
      return pattern.equalsIgnoreCase(ipAddress);
    }
    String[] parts = pattern.split("/", 2);
    String network = parts[0];
    boolean networkIsV4 = InetAddressUtils.isIPv4(network);
    boolean networkIsV6 = !networkIsV4 && InetAddressUtils.isIPv6(network);
    if (!networkIsV4 && !networkIsV6) {
      return false;
    }
    // The candidate must be a literal address of the SAME family - a v4 range never matches a v6
    // address and vice versa, even if byte lengths happened to coincide.
    boolean candidateIsV4 = InetAddressUtils.isIPv4(ipAddress);
    boolean candidateIsV6 = !candidateIsV4 && InetAddressUtils.isIPv6(ipAddress);
    if (networkIsV4 != candidateIsV4 || networkIsV6 != candidateIsV6) {
      return false;
    }
    int prefixLength;
    try {
      prefixLength = Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      return false;
    }
    int maxPrefixLength = networkIsV4 ? 32 : 128;
    if (prefixLength < 0 || prefixLength > maxPrefixLength) {
      return false;
    }
    try {
      // Safe: both strings were already confirmed to be literal IPv4/IPv6 addresses above, so
      // getByName() parses them directly and never attempts a DNS lookup.
      byte[] networkBytes = InetAddress.getByName(network).getAddress();
      byte[] candidateBytes = InetAddress.getByName(ipAddress).getAddress();
      return matchesPrefix(networkBytes, candidateBytes, prefixLength);
    } catch (UnknownHostException e) {
      LOG.debug("Could not parse IP for range match: " + e.getMessage());
      return false;
    }
  }

  private static boolean matchesPrefix(byte[] network, byte[] candidate, int prefixLength) {
    if (network.length != candidate.length) {
      return false;
    }
    int fullBytes = prefixLength / 8;
    int remainingBits = prefixLength % 8;
    for (int i = 0; i < fullBytes; i++) {
      if (network[i] != candidate[i]) {
        return false;
      }
    }
    if (remainingBits > 0) {
      int mask = (0xFF << (8 - remainingBits)) & 0xFF;
      if ((network[fullBytes] & mask) != (candidate[fullBytes] & mask)) {
        return false;
      }
    }
    return true;
  }

}
