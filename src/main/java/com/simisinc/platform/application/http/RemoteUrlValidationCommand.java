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

package com.simisinc.platform.application.http;

import java.net.InetAddress;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;

/**
 * SSRF guard for server-side fetches of a caller-supplied (untrusted) URL. Applied where the
 * URL is arbitrary user/admin input or is derived from a fetched response -- NOT on the
 * fixed third-party API clients (OAuth, Stripe, MapBox, ...), which legitimately target
 * configured endpoints that may be internal in some deployments.
 * <p>
 * A URL is allowed only when it is http/https and its host resolves exclusively to public,
 * routable addresses. Loopback, any-local, link-local (which includes the 169.254.169.254
 * cloud-metadata endpoint on Azure/AWS/GCP), private/site-local, IPv6 unique-local, and
 * multicast addresses are blocked -- the ranges an SSRF payload uses to reach internal
 * services or steal instance-metadata credentials. Because the check resolves the host and
 * inspects the resulting address, numeric-encoding tricks (e.g. http://2130706433/) are
 * caught too: they resolve to a loopback/private address and are rejected.
 * <p>
 * Residual: this does not by itself defeat DNS rebinding (the JDK HttpClient re-resolves the
 * host when it connects, so a hostname that returns a public address here could return an
 * internal one at fetch time). Blocking literal-IP and stable-DNS targets covers the common
 * metadata-endpoint attack; pinning the validated address at connect time is the stronger
 * follow-up.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
public class RemoteUrlValidationCommand {

  private static final Log LOG = LogFactory.getLog(RemoteUrlValidationCommand.class);

  private static final String[] ALLOWED_SCHEMES = { "http", "https" };

  /**
   * Determines whether a server-side fetch of the given URL is permitted. Fails closed:
   * a blank, malformed, non-http(s), unresolvable, or internally-routed URL returns false.
   *
   * @param url the caller-supplied URL to be fetched
   * @return true only if the URL is safe to fetch
   */
  public static boolean isFetchAllowed(String url) {
    if (StringUtils.isBlank(url)) {
      return false;
    }
    if (!new UrlValidator(ALLOWED_SCHEMES).isValid(url)) {
      LOG.debug("Blocked a non-http(s) or malformed url: " + url);
      return false;
    }
    try {
      String host = URI.create(url).getHost();
      if (StringUtils.isBlank(host)) {
        LOG.debug("Blocked a url with no resolvable host: " + url);
        return false;
      }
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses == null || addresses.length == 0) {
        return false;
      }
      // Every address the host resolves to must be public/routable; if any is internal,
      // reject -- a host that resolves to both a public and a private address is not safe.
      for (InetAddress address : addresses) {
        if (isBlockedAddress(address)) {
          LOG.warn("Blocked an SSRF-unsafe address for host " + host + ": " + address.getHostAddress());
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      // Unresolvable host, malformed authority, etc. -- fail closed.
      LOG.warn("Blocked a url that could not be validated: " + url);
      return false;
    }
  }

  /**
   * True when the address is one an SSRF payload uses to reach the host or internal network:
   * loopback, any-local, link-local (incl. the cloud metadata endpoint), private/site-local,
   * IPv6 unique-local, or multicast.
   */
  static boolean isBlockedAddress(InetAddress address) {
    if (address.isLoopbackAddress()      // 127.0.0.0/8, ::1
        || address.isAnyLocalAddress()   // 0.0.0.0, ::
        || address.isLinkLocalAddress()  // 169.254.0.0/16 (cloud metadata), fe80::/10
        || address.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
        || address.isMulticastAddress()) {
      return true;
    }
    // InetAddress.isSiteLocalAddress() does not recognize IPv6 unique-local (fc00::/7);
    // block it explicitly.
    byte[] bytes = address.getAddress();
    if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
      return true;
    }
    return false;
  }
}
