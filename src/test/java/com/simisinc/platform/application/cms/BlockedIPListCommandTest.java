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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.database.DB;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class BlockedIPListCommandTest {

  @Test
  void passesCheckWithEmptyConfiguration() {
    // Mock DB calls
    Connection jdbcConnection = mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);
      // Mock cached IP list
      List<String> emptyList = new ArrayList<>();
      try (MockedStatic<LoadBlockedIPListCommand> staticLoadBlockedIpListCommand = mockStatic(LoadBlockedIPListCommand.class)) {
        staticLoadBlockedIpListCommand.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(emptyList);

        BlockedIPListCommand.setList(BlockedIPListCommand.IP_ALLOW_LIST, emptyList);
        BlockedIPListCommand.setList(BlockedIPListCommand.IP_IGNORE_LIST, emptyList);
        BlockedIPListCommand.setList(BlockedIPListCommand.URL_BLOCK_LIST, emptyList);
        String resource = "/resource";
        String ipAddress = "127.0.0.1";
        Assertions.assertTrue(BlockedIPListCommand.passesCheck(resource, ipAddress));
      }
    }
  }

  @Test
  void doesNotPassIPCheck() {
    List<String> emptyList = new ArrayList<>();
    List<String> blockedIpList = new ArrayList<>();
    blockedIpList.add("1.2.3.4");

    BlockedIPListCommand.setList(BlockedIPListCommand.IP_ALLOW_LIST, emptyList);
    BlockedIPListCommand.setList(BlockedIPListCommand.IP_IGNORE_LIST, blockedIpList);
    BlockedIPListCommand.setList(BlockedIPListCommand.URL_BLOCK_LIST, emptyList);

    String resource = "/resource";
    String ipAddress = "1.2.3.4";
    boolean passesCheck = BlockedIPListCommand.passesCheck(resource, ipAddress);
    Assertions.assertFalse(passesCheck);
  }

  @Test
  void doesNotPassResourceCheck() {
    // Mock DB calls
    Connection jdbcConnection = mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);

      List<String> emptyList = new ArrayList<>();
      try (MockedStatic<LoadBlockedIPListCommand> staticLoadBlockedIpListCommand = mockStatic(LoadBlockedIPListCommand.class)) {
        staticLoadBlockedIpListCommand.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(emptyList);

        BlockedIP blockedIP = new BlockedIP();
        try (MockedStatic<SaveBlockedIPCommand> staticSaveBlockedIpCommand = mockStatic(SaveBlockedIPCommand.class)) {
          staticSaveBlockedIpCommand.when(() -> SaveBlockedIPCommand.save(any())).thenReturn(blockedIP);

          String invalidResource = "/resource";
          List<String> blockList = new ArrayList<>();
          blockList.add(invalidResource);

          BlockedIPListCommand.setList(BlockedIPListCommand.IP_ALLOW_LIST, emptyList);
          BlockedIPListCommand.setList(BlockedIPListCommand.IP_IGNORE_LIST, emptyList);
          BlockedIPListCommand.setList(BlockedIPListCommand.URL_BLOCK_LIST, blockList);

          String ipAddress = "1.2.3.4";
          boolean passesCheck = BlockedIPListCommand.passesCheck(invalidResource, ipAddress);
          Assertions.assertFalse(passesCheck);
        }
      }
    }
  }
}