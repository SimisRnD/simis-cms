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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;

/**
 * Validates and saves bot user-agent signature objects
 *
 * @author elizabeth houser
 */
public class SaveBotUserAgentCommand {

  private static Log LOG = LogFactory.getLog(SaveBotUserAgentCommand.class);

  // Matching is a raw substring test (see SessionCommand.checkForBot()) against every visitor's
  // User-Agent header, so a short/generic entry has an outsized blast radius: it can misclassify a
  // large fraction of real traffic as bot. The shortest legitimate signature already shipped in the
  // seed data (src/main/resources/database/install/NEW_10190__new_bot_list.sql) is "Slurp" (Yahoo),
  // at 5 characters -- every other seeded value is 6+ characters. 5 is therefore the largest minimum
  // that doesn't reject any real-world signature already in use, while still rejecting obviously
  // too-short/generic entries (a single character or two, or a common short word).
  static final int MIN_USER_AGENT_LENGTH = 5;

  public static BotUserAgent save(BotUserAgent botUserAgentBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    String trimmedUserAgent = StringUtils.trimToNull(botUserAgentBean.getUserAgent());
    if (StringUtils.isBlank(trimmedUserAgent)) {
      errorMessages.append("A partial user agent value is required");
    } else if (trimmedUserAgent.length() < MIN_USER_AGENT_LENGTH) {
      errorMessages.append("This value is too short/generic and would likely match a large amount of real visitor " +
          "traffic -- use a more specific fragment of the actual User-Agent string (minimum " +
          MIN_USER_AGENT_LENGTH + " characters)");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    BotUserAgent botUserAgent;
    String previousUserAgent = null;
    if (botUserAgentBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      botUserAgent = BotUserAgentRepository.findById(botUserAgentBean.getId());
      if (botUserAgent == null) {
        throw new DataException("The existing record could not be found");
      }
      // Capture the pre-update value so the cache can be corrected below; once setUserAgent() is
      // called on this object, the prior value is gone
      previousUserAgent = botUserAgent.getUserAgent();
    } else {
      LOG.debug("Saving a new record... ");
      botUserAgent = new BotUserAgent();
    }
    botUserAgent.setUserAgent(trimmedUserAgent);
    botUserAgent.setLabel(botUserAgentBean.getLabel());
    if (botUserAgentBean.getCreated() != null) {
      botUserAgent.setCreated(botUserAgentBean.getCreated());
    }
    botUserAgent = BotUserAgentRepository.save(botUserAgent);
    if (botUserAgent != null) {
      // On an update where the value actually changed, remove the stale old value from the
      // in-memory cache first -- otherwise LoadBotUserAgentListCommand.addToCache() only appends,
      // leaving the old (now-incorrect) substring live in SessionCommand.checkForBot() indefinitely
      if (previousUserAgent != null && !previousUserAgent.equals(botUserAgent.getUserAgent())) {
        BotUserAgent staleRecord = new BotUserAgent();
        staleRecord.setUserAgent(previousUserAgent);
        LoadBotUserAgentListCommand.removeFromCache(staleRecord);
      }
      LoadBotUserAgentListCommand.addToCache(botUserAgent);
    }
    return botUserAgent;
  }

}
