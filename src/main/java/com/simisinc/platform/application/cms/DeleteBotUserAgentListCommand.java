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

/**
 * Deletes bot user-agent signature records
 *
 * @author elizabeth houser
 */
public class DeleteBotUserAgentListCommand {

  private static Log LOG = LogFactory.getLog(DeleteBotUserAgentListCommand.class);

  public static boolean delete(BotUserAgent record) {
    boolean removed = BotUserAgentRepository.remove(record);
    LoadBotUserAgentListCommand.removeFromCache(record);
    return removed;
  }

}
