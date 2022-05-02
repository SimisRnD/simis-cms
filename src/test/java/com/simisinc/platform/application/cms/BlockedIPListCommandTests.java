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

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.infrastructure.database.DB;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

public class BlockedIPListCommandTests {

  @Test
  void passesCheckWithEmptyConfiguration() {
    // Mock DB calls
    Connection jdbcConnection = Mockito.mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);

      // Mock directory path
      try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
        staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");

        // Mock cached list
        List<String> emptyList = new ArrayList<>();
        try (MockedStatic<LoadBlockedIPListCommand> staticLoadBlockedIpListCommand = mockStatic(LoadBlockedIPListCommand.class)) {
          staticLoadBlockedIpListCommand.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(emptyList);
          BlockedIPListCommand.load();

          String resource = "/resource";
          String ipAddress = "127.0.0.1";
          Assertions.assertTrue(BlockedIPListCommand.passesCheck(resource, ipAddress));
        }
      }
    }
  }

  @Test
  void doesNotPassIPCheck() {
    // Mock directory path
    try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
      staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");

      try (MockedStatic<LoadBlockedIPListCommand> staticLoadBlockedIpListCommand = mockStatic(LoadBlockedIPListCommand.class)) {
        List<String> blockedIpList = new ArrayList<>();
        blockedIpList.add("1.2.3.4");
        staticLoadBlockedIpListCommand.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(blockedIpList);

        BlockedIPListCommand.load();

        String resource = "/resource";
        String ipAddress = "1.2.3.4";
        boolean passesCheck = BlockedIPListCommand.passesCheck(resource, ipAddress);
        Assertions.assertFalse(passesCheck);
      }
    }
  }

  @Test
  void doesNotPassResourceCheck() {
    // Mock DB calls
    Connection jdbcConnection = Mockito.mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);

      // Mock directory path
      try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
        staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");

        // Mock cached list
        List<String> emptyList = new ArrayList<>();
        try (MockedStatic<LoadBlockedIPListCommand> staticLoadBlockedIpListCommand = mockStatic(LoadBlockedIPListCommand.class)) {
          staticLoadBlockedIpListCommand.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(emptyList);
          BlockedIPListCommand.load();

          String invalidResource = "/resource";
          List<String> blockList = new ArrayList<>();
          blockList.add(invalidResource);
          BlockedIPListCommand.setList(BlockedIPListCommand.URL_BLOCK_LIST, blockList);

          String ipAddress = "1.2.3.4";
          boolean passesCheck = BlockedIPListCommand.passesCheck(invalidResource, ipAddress);
          Assertions.assertFalse(passesCheck);
        }
      }
    }
  }
}