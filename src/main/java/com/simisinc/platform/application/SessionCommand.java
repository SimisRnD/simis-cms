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

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Methods for working with sessions
 *
 * @author matt rajkowski
 * @created 3/6/20 12:59 PM
 */
public class SessionCommand {

  private static Log LOG = LogFactory.getLog(SessionCommand.class);

  public static final String BOT_LIST = "bot-list.csv";

  private static Map<String, List<String>> listMap = new HashMap<>();
  private static Map<String, Long> lastModifiedMap = new HashMap<>();

  public static synchronized void load() {
    load(BOT_LIST);
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

  public static List<String> getBotList() {
    return listMap.get(BOT_LIST);
  }

  public static boolean checkForBot(String userAgent) {
    if (StringUtils.isBlank(userAgent)) {
      return true;
    }
    List<String> botList = listMap.get(BOT_LIST);
    return botList.stream().anyMatch(userAgent::contains);
  }
}
