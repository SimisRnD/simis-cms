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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.util.InetAddressUtils;

/**
 * Validates and saves blocked IP objects
 *
 * @author matt rajkowski
 * @created 3/25/20 10:10 AM
 */
public class SaveBlockedIPCommand {

  private static Log LOG = LogFactory.getLog(SaveBlockedIPCommand.class);

  public static BlockedIP save(BlockedIP blockedIPBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(blockedIPBean.getIpAddress())) {
      errorMessages.append("An IP address is required");
    } else if (!InetAddressUtils.isIPv4Address(blockedIPBean.getIpAddress()) &&
        !InetAddressUtils.isIPv6Address(blockedIPBean.getIpAddress())) {
      errorMessages.append("A valid IPv4 or IPv6 address is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    BlockedIP blockedIP;
    if (blockedIPBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      blockedIP = BlockedIPRepository.findById(blockedIPBean.getId());
      if (blockedIP == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      blockedIP = new BlockedIP();
    }
    blockedIP.setIpAddress(blockedIPBean.getIpAddress());
    blockedIP.setReason(blockedIPBean.getReason());
    if (blockedIPBean.getCreated() != null) {
      blockedIP.setCreated(blockedIPBean.getCreated());
    }
    blockedIP = BlockedIPRepository.save(blockedIP);
    if (blockedIP != null) {
      LoadBlockedIPListCommand.addIpToCache(blockedIP);
    }
    return blockedIP;
  }

  public static void immediateBlock(String ipAddress, String reason) {
    LoadBlockedIPListCommand.addIpToCache(ipAddress);
    BlockedIP blockedIP = new BlockedIP();
    blockedIP.setIpAddress(ipAddress);
    blockedIP.setReason(reason);
    BlockedIPRepository.save(blockedIP);
  }

}
