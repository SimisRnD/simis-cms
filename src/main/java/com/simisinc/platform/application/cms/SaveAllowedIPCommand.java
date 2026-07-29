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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;

/**
 * Validates and saves allowed IP objects
 *
 * @author elizabeth houser
 */
public class SaveAllowedIPCommand {

  private static Log LOG = LogFactory.getLog(SaveAllowedIPCommand.class);

  public static AllowedIP save(AllowedIP allowedIPBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(allowedIPBean.getIpAddress())) {
      errorMessages.append("An IP address is required");
    } else if (!InetAddressUtils.isIPv4(allowedIPBean.getIpAddress()) &&
        !InetAddressUtils.isIPv6(allowedIPBean.getIpAddress())) {
      errorMessages.append("A valid IPv4 or IPv6 address is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    AllowedIP allowedIP;
    if (allowedIPBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      allowedIP = AllowedIPRepository.findById(allowedIPBean.getId());
      if (allowedIP == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      allowedIP = new AllowedIP();
    }
    allowedIP.setIpAddress(allowedIPBean.getIpAddress());
    allowedIP.setReason(allowedIPBean.getReason());
    if (allowedIPBean.getCreated() != null) {
      allowedIP.setCreated(allowedIPBean.getCreated());
    }
    allowedIP = AllowedIPRepository.save(allowedIP);
    if (allowedIP != null) {
      LoadAllowedIPListCommand.addIpToCache(allowedIP);
    }
    return allowedIP;
  }

}
