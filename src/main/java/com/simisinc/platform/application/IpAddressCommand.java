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

import java.net.InetAddress;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.net.InetAddressUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Anonymizes IP addresses stored for analytics (visitor sessions and page hits). The full address is
 * geocoded upstream when a session is created, so the stored value is only an identifier -- masking the
 * host portion keeps the coarse network for abuse analysis without retaining a value that identifies an
 * individual visitor. Records that are legally or operationally required to keep the full address (block
 * list, login audit, orders, form and mailing-list submissions) are intentionally not passed through here.
 *
 * @author elizabeth houser
 */
public class IpAddressCommand {

  private static Log LOG = LogFactory.getLog(IpAddressCommand.class);

  /**
   * Returns the value anonymized when the {@code analytics.anonymizeIp} site property is enabled, otherwise
   * the value unchanged (opt-in, so default behavior is preserved).
   */
  public static String anonymizeForStorage(String ipAddress) {
    if (LoadSitePropertyCommand.loadByNameAsBoolean("analytics.anonymizeIp")) {
      return anonymize(ipAddress);
    }
    return ipAddress;
  }

  /**
   * Masks the host portion of an IP address: IPv4 to the /24 network (zeroes the last octet) and IPv6 to
   * the /48 network (zeroes the last 80 bits). A blank or unparseable value is returned unchanged.
   */
  public static String anonymize(String ipAddress) {
    if (StringUtils.isBlank(ipAddress)) {
      return ipAddress;
    }
    try {
      if (InetAddressUtils.isIPv4Address(ipAddress)) {
        byte[] bytes = InetAddress.getByName(ipAddress).getAddress();
        bytes[3] = 0;
        return InetAddress.getByAddress(bytes).getHostAddress();
      }
      if (InetAddressUtils.isIPv6Address(ipAddress)) {
        byte[] bytes = InetAddress.getByName(ipAddress).getAddress();
        for (int i = 6; i < bytes.length; i++) {
          bytes[i] = 0;
        }
        return InetAddress.getByAddress(bytes).getHostAddress();
      }
    } catch (Exception e) {
      LOG.warn("Could not anonymize ip address");
    }
    return ipAddress;
  }
}
