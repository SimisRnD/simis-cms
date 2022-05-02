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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.simisinc.platform.application.SessionCommand.BOT_LIST;
import static org.mockito.Mockito.mockStatic;

public class SessionCommandTests {

  @Test
  void failsCheckWithEmptyConfiguration() {
    Assertions.assertTrue(SessionCommand.checkForBot(null));

    String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1";
    Assertions.assertFalse(SessionCommand.checkForBot(userAgent));
  }

  @Test
  void checkWithConfiguration() {
    // Mock directory path
    try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
      staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");
      SessionCommand.load();

      String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1";
      List<String> botList = new ArrayList<>();
      botList.add("MyBot");
      SessionCommand.setList(BOT_LIST, botList);
      Assertions.assertFalse(SessionCommand.checkForBot(userAgent));
      Assertions.assertTrue(SessionCommand.checkForBot("MyBot"));
    }
  }
}