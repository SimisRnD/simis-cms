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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.net.spi.InetAddressResolverProvider;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Installs a {@link InetAddressResolverProvider} that serves an already-validated, pinned
 * address (see {@link ConnectAddressPin}) for a hostname's connect-time DNS lookup instead of
 * re-resolving it -- closing the DNS-rebinding TOCTOU gap described in issue #760. Every lookup
 * that is not pinned -- which is nearly all of them, including every lookup Tomcat itself ever
 * performs -- delegates unchanged to the JDK's builtin resolver, so this has no effect outside
 * the narrow window {@code HttpGetCommand.executeUserUrl} sets a pin for.
 *
 * <p>Registered via {@code META-INF/services/java.net.spi.InetAddressResolverProvider} in this
 * module's jar, which {@code ant pin-resolver-jar} builds and {@code docker/app/Dockerfile}
 * copies to {@code CATALINA_HOME/lib}. See {@link ConnectAddressPin}'s javadoc for the hard
 * deployment constraint this class shares and why it was verified against a real container
 * rather than assumed: this jar belongs on Tomcat's shared classloader only, never inside the
 * WAR.
 */
public final class ConnectAddressResolverProvider extends InetAddressResolverProvider {

  @Override
  public InetAddressResolver get(Configuration configuration) {
    InetAddressResolver builtin = configuration.builtinResolver();
    return new InetAddressResolver() {
      @Override
      public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
        InetAddress[] pinned = ConnectAddressPin.get(host);
        if (pinned != null) {
          InetAddress[] matching = matchingFamily(pinned, lookupPolicy);
          if (matching.length == 0) {
            // The pin exists for this host, but none of its addresses match the address
            // family this particular lookup asked for (e.g. an IPv4-only caller on a JVM
            // started with -Djava.net.preferIPv4Stack=true, against a pin that only holds an
            // AAAA record). Falling back to builtin.lookupByName here would re-resolve the
            // host via real DNS -- exactly the unpinned, racy lookup this resolver exists to
            // prevent -- so a family mismatch fails closed instead, the same outcome a normal
            // resolver gives when a host genuinely has no address of the requested family.
            throw new UnknownHostException(host
                + ": pinned address(es) do not include the address family this lookup "
                + "requires (characteristics=" + lookupPolicy.characteristics() + ")");
          }
          return Stream.of(matching);
        }
        return builtin.lookupByName(host, lookupPolicy);
      }

      @Override
      public String lookupByAddress(byte[] addr) throws UnknownHostException {
        return builtin.lookupByAddress(addr);
      }
    };
  }

  /**
   * Narrows {@code pinned} to the address family {@code lookupPolicy} actually requested, the
   * same filtering the builtin resolver applies. Nothing in this codebase currently sets
   * {@code java.net.preferIPv4Stack} or {@code preferIPv6Addresses} (both default to a dual-
   * stack {@code LookupPolicy} that requests IPv4 and IPv6 together), so in practice this is a
   * no-op today; it exists so a pinned dual-stack host does not hand an IPv4-only (or
   * IPv6-only) caller an address family it explicitly said it cannot use, should that ever
   * change.
   *
   * <p>Package-private rather than {@code private} so
   * {@code ConnectAddressResolverProviderFamilyFilterTest} can exercise it directly:
   * {@link InetAddressResolverProvider.Configuration}, {@link #get}'s parameter type, is a JDK
   * sealed interface permitting only an internal {@code sun.net} class, so no test outside
   * {@code java.base} can construct one to drive {@link #get} end to end. This method is the
   * one piece of the family-filtering logic a test can reach directly.
   */
  static InetAddress[] matchingFamily(InetAddress[] pinned, LookupPolicy lookupPolicy) {
    int characteristics = lookupPolicy.characteristics();
    boolean wantsIPv4 = (characteristics & LookupPolicy.IPV4) != 0;
    boolean wantsIPv6 = (characteristics & LookupPolicy.IPV6) != 0;
    if (wantsIPv4 == wantsIPv6) {
      // Both families requested (the default, dual-stack case) or neither bit is set (not a
      // combination this JDK's LookupPolicy is documented to produce) -- either way there is no
      // single family to narrow to, so hand back every pinned address unfiltered, exactly as
      // this method's caller did before it existed.
      return pinned;
    }
    return Arrays.stream(pinned)
        .filter(addr -> wantsIPv4 ? addr instanceof Inet4Address : addr instanceof Inet6Address)
        .toArray(InetAddress[]::new);
  }

  @Override
  public String name() {
    return "simis-cms-connect-address-pin";
  }
}
