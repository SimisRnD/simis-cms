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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;

/**
 * Loads and checks blocked IPs
 *
 * @author matt rajkowski
 * @created 4/15/20 8:00 AM
 */
public class BlockedIPListCommand {

  private static Log LOG = LogFactory.getLog(BlockedIPListCommand.class);
  static final String IP_ALLOW_LIST = "ip-allow-list.csv";
  static final String IP_IGNORE_LIST = "ip-deny-list.csv";
  static final String URL_BLOCK_LIST = "url-block-list.csv";

  private static Map<String, List<String>> listMap = new HashMap<>();
  private static Map<String, Long> lastModifiedMap = new HashMap<>();

  public static synchronized void load() {
    load(IP_ALLOW_LIST);
    load(IP_IGNORE_LIST);
    load(URL_BLOCK_LIST);
  }

  private static void load(String filename) {
    // Get a file handle
    File file = FileSystemCommand.getFileServerConfigPath("cms", filename);

    // Determine if the file is new
    if (lastModifiedMap.containsKey(filename) && !FileSystemCommand.isModified(file, lastModifiedMap.get(filename))) {
      return;
    }

    // Load the file
    List<String> list = FileSystemCommand.loadFileToList(file);

    // Cache the result
    listMap.put(filename, list);
    lastModifiedMap.put(filename, file.lastModified());
  }

  public static void setList(String filename, List<String> list) {
    listMap.put(filename, list);
    lastModifiedMap.put(filename, 0L);
  }

  public static boolean passesCheck(String resource, String ipAddress) {

    LOG.debug("Checking IP: " + ipAddress);

    // If allowed, return quickly
    List<String> ipAllowList = listMap.get(IP_ALLOW_LIST);
    if (ipAllowList != null && ipAllowList.contains(ipAddress)) {
      LOG.debug("Allowed IP: " + ipAddress);
      return true;
    }

    // If blocked in file, return an error
    List<String> ipIgnoreList = listMap.get(IP_IGNORE_LIST);
    if (ipIgnoreList != null && ipIgnoreList.contains(ipAddress)) {
      LOG.debug("Blocked IP: " + ipAddress);
      return false;
    }

    // If blocked in database, return an error
    if (LoadBlockedIPListCommand.retrieveCachedIpAddressList().contains(ipAddress)) {
      LOG.debug("Blocked IP: " + ipAddress);
      return false;
    }

    // If resource scanning, return an error
    List<String> urlBlockList = listMap.get(URL_BLOCK_LIST);
    if (urlBlockList.contains(resource)) {
      // Prevent future access
      SaveBlockedIPCommand.immediateBlock(ipAddress, resource);
      LOG.warn("Logged IP: " + ipAddress);
      return false;
    }

    return true;
  }

}
