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

import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/20 10:10 AM
 */
public class LoadBlockedIPListCommand {

  private static Log LOG = LogFactory.getLog(LoadBlockedIPListCommand.class);

  private static List<String> ipAddressList = null;

  public static List<String> retrieveCachedIpAddressList() {
    if (ipAddressList == null) {
      ipAddressList = loadIpAddressList();
    }
    return ipAddressList;
  }

  public static void refreshCachedIpAddressList() {
    ipAddressList = loadIpAddressList();
  }

  public static void addIpToCache(BlockedIP blockedIP) {
    if (!ipAddressList.contains(blockedIP.getIpAddress())) {
      ipAddressList.add(blockedIP.getIpAddress());
    }
  }

  public static void addIpToCache(String ipAddress) {
    if (!ipAddressList.contains(ipAddress)) {
      ipAddressList.add(ipAddress);
    }
  }

  public static void removeIpFromCache(BlockedIP blockedIP) {
    while (ipAddressList.contains(blockedIP.getIpAddress())) {
      ipAddressList.remove(blockedIP.getIpAddress());
    }
  }

  public static List<String> loadIpAddressList() {
    List<String> ipAddressList = new ArrayList<>();
    List<BlockedIP> blockedIP = BlockedIPRepository.findAll();
    for (BlockedIP record : blockedIP) {
      ipAddressList.add(record.getIpAddress());
    }
    LOG.info("IPs found: " + ipAddressList.size());
    return ipAddressList;
  }

}
