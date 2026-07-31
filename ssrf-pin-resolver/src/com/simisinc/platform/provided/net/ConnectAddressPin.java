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

import java.net.InetAddress;

/**
 * Thread-scoped holder for a connect-time DNS pin: an exact set of already-validated addresses
 * that a subsequent {@code java.net.http.HttpClient} connection to a specific hostname must
 * resolve to, instead of re-resolving the hostname itself.
 *
 * <p>This closes the DNS-rebinding gap called out in {@code RemoteUrlValidationCommand}'s
 * javadoc: a hostname that resolves to a public address at SSRF-validation time could resolve
 * to an internal one moments later, when the JDK {@code HttpClient} re-resolves it to actually
 * connect. Pinning the exact address(es) validated means there is no second lookup for a
 * guarded fetch to race -- see issue #760.
 *
 * <p><b>Deployment constraint -- do not relax without re-reading issue #760's investigation.</b>
 * This class and {@link ConnectAddressResolverProvider} must be loaded ONLY from Tomcat's
 * shared/Common classloader ({@code CATALINA_HOME/lib}, wired via {@code docker/app/Dockerfile}
 * and this project's {@code ant pin-resolver-jar} target) -- never from {@code WEB-INF/lib} or
 * {@code WEB-INF/classes}. Both halves of that constraint were verified empirically against a
 * real Tomcat 11 container while building this:
 *
 * <ol>
 *   <li>Tomcat's own bootstrap resolves a hostname (e.g. "localhost") on its own thread, using
 *       whichever {@code java.net.spi.InetAddressResolverProvider} the JDK's
 *       {@code ServiceLoader} finds first -- before any webapp classloader exists. The JDK
 *       caches that decision for the life of the JVM. A provider that is visible only to a
 *       webapp classloader is discovered too late and is never consulted again: the pin would
 *       silently never take effect.
 *   <li>Tomcat's webapp classloaders are child-first. If a copy of this class were ALSO present
 *       in {@code WEB-INF/lib} (or {@code WEB-INF/classes}), the webapp would load and write to
 *       its OWN copy -- a different class-identity object from the one
 *       {@link ConnectAddressResolverProvider} (bound to the single {@code CATALINA_HOME/lib}
 *       copy) reads. The pin would silently never reach the resolver. There must be exactly one
 *       copy of this class on the classpath, and it must be the {@code CATALINA_HOME/lib} one.
 * </ol>
 *
 * <p>Webapp code (see {@code HttpGetCommand.executeUserUrl}) sets and clears the pin through the
 * public methods here; ordinary parent-delegated classloading resolves this class from
 * {@code CATALINA_HOME/lib} even though the call site compiles and runs from {@code WEB-INF}.
 * {@link ConnectAddressResolverProvider} reads the pin through the package-private
 * {@link #get(String)}, since it is loaded from the same jar and package.
 */
public final class ConnectAddressPin {

  private static final ThreadLocal<String> PINNED_HOST = new ThreadLocal<>();
  private static final ThreadLocal<InetAddress[]> PINNED_ADDRESSES = new ThreadLocal<>();

  private ConnectAddressPin() {
  }

  /**
   * Pins {@code host} to exactly {@code addresses} for connect-time DNS resolution on the
   * calling thread. Must be paired with {@link #clear()} in a {@code finally} block: Tomcat
   * reuses worker threads across requests, so a pin left set here would leak into whatever the
   * same thread handles next.
   *
   * @param host the exact hostname string the caller is about to connect to (must match the
   *             host {@code HttpClient} passes to the resolver at connect time)
   * @param addresses the address(es) already confirmed safe for {@code host}
   */
  public static void set(String host, InetAddress[] addresses) {
    PINNED_HOST.set(host);
    PINNED_ADDRESSES.set(addresses);
  }

  /** Clears any pin set on the calling thread. Safe to call even if nothing was set. */
  public static void clear() {
    PINNED_HOST.remove();
    PINNED_ADDRESSES.remove();
  }

  /**
   * The addresses pinned for {@code host} on the calling thread, or {@code null} if nothing is
   * pinned or the pinned host does not match. Package-private: only
   * {@link ConnectAddressResolverProvider} reads the pin -- webapp code never should.
   */
  static InetAddress[] get(String host) {
    String pinnedHost = PINNED_HOST.get();
    if (host == null || pinnedHost == null || !pinnedHost.equalsIgnoreCase(host)) {
      return null;
    }
    return PINNED_ADDRESSES.get();
  }
}
