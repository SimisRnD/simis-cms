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

import com.simisinc.platform.application.cms.LoadBotUserAgentListCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Methods for working with sessions
 *
 * @author matt rajkowski
 * @created 3/6/20 12:59 PM
 */
public class SessionCommand {

  private static Log LOG = LogFactory.getLog(SessionCommand.class);

  // No longer read at runtime (bot detection is DB-backed -- see LoadBotUserAgentListCommand),
  // but kept as a reference target for the already-applied Flyway migration
  // V20220331_1001__update_bots.java, which must not be edited (that would change the checksum
  // of a migration already recorded as run on existing databases).
  public static final String BOT_LIST = "bot-list.csv";

  public static synchronized void load() {
    LoadBotUserAgentListCommand.refreshCachedUserAgentList();
  }

  public static boolean checkForBot(String userAgent) {
    if (StringUtils.isBlank(userAgent)) {
      return true;
    }
    List<String> botList = LoadBotUserAgentListCommand.retrieveCachedUserAgentList();
    if (botList == null || botList.isEmpty()) {
      return false;
    }
    return botList.stream().anyMatch(botUserAgent -> StringUtils.containsIgnoreCase(userAgent, botUserAgent));
  }
}
