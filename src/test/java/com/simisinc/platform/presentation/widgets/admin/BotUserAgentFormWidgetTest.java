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
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import com.simisinc.platform.application.cms.SaveBotUserAgentCommand;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

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

  @Test
  void aValueWithStrayWhitespaceIsCheckedForDuplicatesAfterTrimming() throws Exception {
    // BotUserAgentRepository's add()/update() trim before the unique-constrained insert, so the
    // duplicate pre-check here must also use the trimmed value -- otherwise a value with stray
    // leading/trailing whitespace slips past this check and fails later at the DB layer with a
    // swallowed SQLException that surfaces as a generic, misleading error
    addQueryParameter(widgetContext, "userAgent", "  ExampleBot  ");
    addQueryParameter(widgetContext, "label", "Example crawler");

    BotUserAgent existingDuplicate = new BotUserAgent();
    existingDuplicate.setId(7L);
    existingDuplicate.setUserAgent("ExampleBot");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<SaveBotUserAgentCommand> saveCommand = mockStatic(SaveBotUserAgentCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findByUserAgent("ExampleBot")).thenReturn(existingDuplicate);

      WidgetContext result = new BotUserAgentFormWidget().post(widgetContext);

      Assertions.assertEquals("This user agent value already exists", result.getWarningMessage());
      repository.verify(() -> BotUserAgentRepository.findByUserAgent("  ExampleBot  "), never());
      saveCommand.verify(() -> SaveBotUserAgentCommand.save(any(BotUserAgent.class)), never());
    }
  }

  @Test
  void aGenuinelyNewValueWithStrayWhitespaceSavesCleanlyAfterTrimming() throws Exception {
    addQueryParameter(widgetContext, "userAgent", "  BrandNewBot  ");
    addQueryParameter(widgetContext, "label", "Brand new crawler");

    BotUserAgent saved = new BotUserAgent();
    saved.setId(11L);
    saved.setUserAgent("BrandNewBot");
    saved.setLabel("Brand new crawler");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<SaveBotUserAgentCommand> saveCommand = mockStatic(SaveBotUserAgentCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findByUserAgent("BrandNewBot")).thenReturn(null);
      saveCommand.when(() -> SaveBotUserAgentCommand.save(any(BotUserAgent.class))).thenReturn(saved);

      WidgetContext result = new BotUserAgentFormWidget().post(widgetContext);

      Assertions.assertEquals("Record was saved", result.getSuccessMessage());
      ArgumentCaptor<BotUserAgent> captor = ArgumentCaptor.forClass(BotUserAgent.class);
      saveCommand.verify(() -> SaveBotUserAgentCommand.save(captor.capture()));
      Assertions.assertEquals("BrandNewBot", captor.getValue().getUserAgent(),
          "The bean passed to save() should already be trimmed");
    }
  }
}
