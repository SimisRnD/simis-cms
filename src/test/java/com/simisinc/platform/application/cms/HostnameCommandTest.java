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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.simisinc.platform.application.cms.HostnameCommand.HOSTNAME_ALLOW_LIST;
import static org.mockito.Mockito.mockStatic;

public class HostnameCommandTest {

  @Test
  void passesCheckWithEmptyConfiguration() {
    String hostname = "localhost";
    boolean passesCheck = HostnameCommand.passesCheck(hostname);
    Assertions.assertTrue(passesCheck);
  }

  @Test
  void passesCheckWithConfiguration() {
    // Mock directory path
    try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
      staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");
      HostnameCommand.load();

      String hostname = "localhost";
      List<String> approvedList = new ArrayList<>();
      approvedList.add(hostname);
      HostnameCommand.setList(HOSTNAME_ALLOW_LIST, approvedList);
      Assertions.assertTrue(HostnameCommand.passesCheck(hostname));
    }
  }

  @Test
  void doesNotPassCheck() {
    // Mock directory path
    try (MockedStatic<FileSystemCommand> staticFileSystemCommand = mockStatic(FileSystemCommand.class)) {
      staticFileSystemCommand.when(FileSystemCommand::getFileServerConfigPath).thenReturn(".");
      HostnameCommand.load();

      String hostnameAllowed = "localhost";
      List<String> approvedList = new ArrayList<>();
      approvedList.add(hostnameAllowed);
      HostnameCommand.setList(HOSTNAME_ALLOW_LIST, approvedList);
      Assertions.assertFalse(HostnameCommand.passesCheck("example.com"));
    }
  }
}