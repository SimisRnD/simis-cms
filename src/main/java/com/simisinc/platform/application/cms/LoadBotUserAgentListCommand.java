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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads bot user-agent signatures from cache or storage and handles updating the cache
 *
 * @author elizabeth houser
 */
public class LoadBotUserAgentListCommand {

  private static Log LOG = LogFactory.getLog(LoadBotUserAgentListCommand.class);

  private static List<String> userAgentList = null;

  public static List<String> retrieveCachedUserAgentList() {
    if (userAgentList == null) {
      userAgentList = loadUserAgentList();
    }
    return userAgentList;
  }

  public static void refreshCachedUserAgentList() {
    userAgentList = loadUserAgentList();
  }

  public static void addToCache(BotUserAgent botUserAgent) {
    if (!userAgentList.contains(botUserAgent.getUserAgent())) {
      userAgentList.add(botUserAgent.getUserAgent());
    }
  }

  public static void removeFromCache(BotUserAgent botUserAgent) {
    while (userAgentList.contains(botUserAgent.getUserAgent())) {
      userAgentList.remove(botUserAgent.getUserAgent());
    }
  }

  public static List<String> loadUserAgentList() {
    List<String> userAgentList = new ArrayList<>();
    List<BotUserAgent> botUserAgentList = BotUserAgentRepository.findAll();
    for (BotUserAgent record : botUserAgentList) {
      userAgentList.add(record.getUserAgent());
    }
    LOG.info("Bot user-agent signatures found: " + userAgentList.size());
    return userAgentList;
  }

}
