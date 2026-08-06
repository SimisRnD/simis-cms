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

import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * Bot detection is now database-backed (see LoadBotUserAgentListCommand) rather than reading
 * config/cms/bot-list.csv from a server-side file store -- that file never reached a
 * Docker/Azure container's file store, so out-of-the-box bot detection was always a no-op.
 *
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class SessionCommandTest {

  @Test
  void failsCheckWithEmptyConfiguration() {
    try (MockedStatic<BotUserAgentRepository> staticRepository = mockStatic(BotUserAgentRepository.class)) {
      staticRepository.when(BotUserAgentRepository::findAll).thenReturn(Collections.emptyList());
      SessionCommand.load();

      Assertions.assertTrue(SessionCommand.checkForBot(null));

      String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1";
      Assertions.assertFalse(SessionCommand.checkForBot(userAgent));
    }
  }

  @Test
  void checkWithConfiguration() {
    try (MockedStatic<BotUserAgentRepository> staticRepository = mockStatic(BotUserAgentRepository.class)) {
      BotUserAgent myBot = new BotUserAgent();
      myBot.setUserAgent("MyBot");
      List<BotUserAgent> botList = Collections.singletonList(myBot);
      staticRepository.when(BotUserAgentRepository::findAll).thenReturn(botList);
      SessionCommand.load();

      String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1";
      Assertions.assertFalse(SessionCommand.checkForBot(userAgent));
      Assertions.assertTrue(SessionCommand.checkForBot("MyBot"));
    }
  }
}
