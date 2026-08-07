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

import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads allowed IP addresses list from cache or storage and handles updating the cache
 *
 * @author elizabeth houser
 */
public class LoadAllowedIPListCommand {

  private static Log LOG = LogFactory.getLog(LoadAllowedIPListCommand.class);

  // CopyOnWriteArrayList: this list is read (streamed/iterated) on essentially every request via
  // BlockedIPListCommand.passesCheck(), while being mutated only rarely, by admin add/delete
  // actions -- a plain ArrayList would be vulnerable to ConcurrentModificationException under that
  // read-heavy/write-rare access pattern.
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

  public static void addIpToCache(AllowedIP allowedIP) {
    addIpToCache(allowedIP.getIpAddress());
  }

  public static void addIpToCache(String ipAddress) {
    // Route through retrieveCachedIpAddressList() rather than the raw field so this is safe to
    // call even if nothing has loaded the cache yet (the field would otherwise still be null)
    List<String> list = retrieveCachedIpAddressList();
    if (!list.contains(ipAddress)) {
      list.add(ipAddress);
    }
  }

  public static void removeIpFromCache(AllowedIP allowedIP) {
    removeIpFromCache(allowedIP.getIpAddress());
  }

  public static void removeIpFromCache(String ipAddress) {
    List<String> list = retrieveCachedIpAddressList();
    while (list.contains(ipAddress)) {
      list.remove(ipAddress);
    }
  }

  public static List<String> loadIpAddressList() {
    List<String> ipAddressList = new CopyOnWriteArrayList<>();
    List<AllowedIP> allowedIPList = AllowedIPRepository.findAll();
    for (AllowedIP record : allowedIPList) {
      ipAddressList.add(record.getIpAddress());
    }
    LOG.info("Allowed IPs found: " + ipAddressList.size());
    return ipAddressList;
  }

}
