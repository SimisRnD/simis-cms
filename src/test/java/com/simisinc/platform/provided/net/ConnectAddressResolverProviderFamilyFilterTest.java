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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.InetAddress;
import java.net.spi.InetAddressResolver.LookupPolicy;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ConnectAddressResolverProvider#matchingFamily(InetAddress[], LookupPolicy)}
 * directly: the address-family filtering {@code lookupByName} applies to a pinned host's
 * addresses before returning them, mirroring what the builtin resolver already does for an
 * unpinned lookup.
 *
 * <p>This cannot be tested by driving {@link ConnectAddressResolverProvider#get} end to end the
 * way {@link ConnectAddressPinResolverTest} drives the whole SPI through a real
 * {@code HttpClient}: {@code get}'s parameter, {@code InetAddressResolverProvider.Configuration},
 * is a JDK <em>sealed</em> interface that permits exactly one implementation
 * ({@code sun.net.ResolverProviderConfiguration}, internal to {@code java.base}) -- no code
 * outside the JDK itself, mock frameworks included, can construct or subclass one. {@code
 * matchingFamily} is the package-private seam that carries the actual filtering decision, so
 * testing it directly still proves the logic that matters; what is NOT re-proven here is that
 * {@code lookupByName} wires an empty result to a thrown {@code UnknownHostException} rather
 * than silently falling back to the builtin resolver -- that four-line guard clause is verified
 * by reading {@link ConnectAddressResolverProvider#get}, not by a runtime test, for the sealed-
 * interface reason above.
 *
 * <p>Also worth knowing: nothing in this codebase sets {@code java.net.preferIPv4Stack} or
 * {@code preferIPv6Addresses} today (confirmed by grep), so in normal operation
 * {@code lookupByName} only ever receives the dual-stack {@code LookupPolicy}
 * {@link #dualStackPolicyReturnsEveryPinnedAddressUnfiltered()} covers; the single-family tests
 * below cover a defense-in-depth path this application's current configuration does not
 * currently reach.
 *
 * @author Liz Houser
 * @created 7/31/2026
 */
class ConnectAddressResolverProviderFamilyFilterTest {

  @Test
  void ipv4OnlyPolicyKeepsOnlyTheIpv4PinnedAddress() throws Exception {
    InetAddress v4 = InetAddress.getByName("127.0.0.1");
    InetAddress v6 = InetAddress.getByName("::1");

    InetAddress[] result = ConnectAddressResolverProvider.matchingFamily(
        new InetAddress[] { v4, v6 }, LookupPolicy.of(LookupPolicy.IPV4));

    assertArrayEquals(new InetAddress[] { v4 }, result);
  }

  @Test
  void ipv6OnlyPolicyKeepsOnlyTheIpv6PinnedAddress() throws Exception {
    InetAddress v4 = InetAddress.getByName("127.0.0.1");
    InetAddress v6 = InetAddress.getByName("::1");

    InetAddress[] result = ConnectAddressResolverProvider.matchingFamily(
        new InetAddress[] { v4, v6 }, LookupPolicy.of(LookupPolicy.IPV6));

    assertArrayEquals(new InetAddress[] { v6 }, result);
  }

  @Test
  void dualStackPolicyReturnsEveryPinnedAddressUnfiltered() throws Exception {
    InetAddress[] pinned = { InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1") };

    InetAddress[] result = ConnectAddressResolverProvider.matchingFamily(
        pinned, LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6));

    // Same array reference, not just an equal one: the dual-stack/no-op branch hands the input
    // straight back rather than copying it.
    assertSame(pinned, result);
  }

  @Test
  void familyMismatchYieldsAnEmptyResultRatherThanTheWrongFamily() throws Exception {
    InetAddress v6Only = InetAddress.getByName("::1");

    InetAddress[] result = ConnectAddressResolverProvider.matchingFamily(
        new InetAddress[] { v6Only }, LookupPolicy.of(LookupPolicy.IPV4));

    // lookupByName turns this empty result into a thrown UnknownHostException rather than
    // falling back to the builtin (unpinned) resolver -- see this class's javadoc for why that
    // wiring is verified by inspection rather than here.
    assertArrayEquals(new InetAddress[0], result);
  }
}
