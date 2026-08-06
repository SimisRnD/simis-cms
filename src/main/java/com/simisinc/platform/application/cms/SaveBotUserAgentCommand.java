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

  public static BotUserAgent save(BotUserAgent botUserAgentBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(botUserAgentBean.getUserAgent())) {
      errorMessages.append("A partial user agent value is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    BotUserAgent botUserAgent;
    if (botUserAgentBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      botUserAgent = BotUserAgentRepository.findById(botUserAgentBean.getId());
      if (botUserAgent == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      botUserAgent = new BotUserAgent();
    }
    botUserAgent.setUserAgent(botUserAgentBean.getUserAgent());
    botUserAgent.setLabel(botUserAgentBean.getLabel());
    if (botUserAgentBean.getCreated() != null) {
      botUserAgent.setCreated(botUserAgentBean.getCreated());
    }
    botUserAgent = BotUserAgentRepository.save(botUserAgent);
    if (botUserAgent != null) {
      LoadBotUserAgentListCommand.addToCache(botUserAgent);
    }
    return botUserAgent;
  }

}
