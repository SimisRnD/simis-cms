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

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the requested hostname
 *
 * @author matt rajkowski
 * @created 4/21/22 6:00 PM
 */
public class HostnameCommand {

  private static Log LOG = LogFactory.getLog(HostnameCommand.class);
  static final String HOSTNAME_ALLOW_LIST = "hostname-allow-list.csv";

  private static Map<String, List<String>> listMap = new HashMap<>();
  private static Map<String, Long> lastModifiedMap = new HashMap<>();

  public static synchronized void load() {
    load(HOSTNAME_ALLOW_LIST);
  }

  private static void load(String filename) {
    // Get a file handle
    String serverConfigPath = FileSystemCommand.getFileServerConfigPath();
    File file = new File(serverConfigPath + "cms/" + filename);

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

  public static boolean passesCheck(String hostname) {
    // If allowed, return quickly
    List<String> hostnameAllowList = listMap.get(HOSTNAME_ALLOW_LIST);
    if (hostnameAllowList == null || hostnameAllowList.isEmpty() || hostnameAllowList.contains(hostname)) {
      LOG.debug("Allowed hostname: " + hostname);
      return true;
    }
    LOG.warn("Invalid hostname: " + hostname);
    return false;
  }

}
