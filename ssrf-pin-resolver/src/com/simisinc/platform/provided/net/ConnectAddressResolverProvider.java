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
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
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
          return Stream.of(pinned);
        }
        return builtin.lookupByName(host, lookupPolicy);
      }

      @Override
      public String lookupByAddress(byte[] addr) throws UnknownHostException {
        return builtin.lookupByAddress(addr);
      }
    };
  }

  @Override
  public String name() {
    return "simis-cms-connect-address-pin";
  }
}
