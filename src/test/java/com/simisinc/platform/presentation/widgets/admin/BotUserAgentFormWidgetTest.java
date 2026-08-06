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

package com.simisinc.platform.presentation.widgets.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import com.simisinc.platform.application.cms.SaveBotUserAgentCommand;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Mirrors AllowedIPFormWidgetTest -- confirms adding a bot user-agent signature records an audit
 * event, following the same allow_list/block_list-derived CRUD pattern (issue #641).
 *
 * @author elizabeth houser
 */
class BotUserAgentFormWidgetTest extends WidgetBase {

  @Test
  void addingABotUserAgentRecordsAnAuditEvent() throws Exception {
    addQueryParameter(widgetContext, "userAgent", "ExampleBot");
    addQueryParameter(widgetContext, "label", "Example crawler");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(3L);
    saved.setUserAgent("ExampleBot");
    saved.setLabel("Example crawler");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<SaveBotUserAgentCommand> saveCommand = mockStatic(SaveBotUserAgentCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findByUserAgent("ExampleBot")).thenReturn(null);
      saveCommand.when(() -> SaveBotUserAgentCommand.save(any(BotUserAgent.class))).thenReturn(saved);

      new BotUserAgentFormWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("bot_user_agent.add"),
          eq(AuditEventCommand.SUCCESS), eq("bot_user_agent"), eq("3"), eq("ExampleBot"), any()));
    }
  }
}
