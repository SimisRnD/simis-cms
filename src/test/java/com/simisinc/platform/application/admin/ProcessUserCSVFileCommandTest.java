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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * The New User modal (users-list.jsp) has no checkbox for "All Guests" ("not a logged in user
 * group"), and the edit-user form mirrors that exclusion server-side (see
 * UserFormWidgetTest#postCannotGrantAllGuestsGroupEvenIfSubmitted). The CSV "Groups" column import
 * path is a separate route to the same group-membership table and must refuse "All Guests" too,
 * the same way it already refuses "All Users" (a group every user gets automatically, not one an
 * import file should be able to name).
 *
 * @author SimIS Inc.
 */
class ProcessUserCSVFileCommandTest extends WidgetBase {

  private static Group group(long id, String name) {
    Group g = new Group();
    g.setId(id);
    g.setName(name);
    return g;
  }

  private static Path writeCsv(Path dir, String content) throws Exception {
    Path csv = dir.resolve("import.csv");
    Files.writeString(csv, content, StandardCharsets.UTF_8);
    return csv;
  }

  @Test
  void csvGroupsColumnCannotGrantAllGuests(@TempDir Path tempDir) throws Exception {
    Path csvFile = writeCsv(tempDir,
        "Email,First Name,Last Name,Groups\ncsv-user@example.com,CSV,User,All Guests\n");

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/import.csv");

    Group allUsers = group(1L, "All Users");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(any())).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString());
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(any(), any())).thenReturn(csvFile.toFile());
      groupRepo.when(() -> GroupRepository.findByName("All Users")).thenReturn(allUsers);
      userRepo.when(() -> UserRepository.findByUsername(anyString())).thenReturn(null);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      saveCmd.when(() -> SaveUserCommand.saveUser(captor.capture())).thenAnswer(inv -> null);

      ProcessUserCSVFileCommand.processCSV(widgetContext);

      // The row named "All Guests" in its Groups column is skipped by raw value, the same way
      // "All Users" already is -- it should never even reach a GroupRepository lookup
      groupRepo.verify(() -> GroupRepository.findByName("All Guests"), never());
      List<Group> savedGroups = captor.getValue().getGroupList();
      assertFalse(savedGroups.stream().anyMatch(g -> "All Guests".equals(g.getName())),
          "a CSV import must never be able to grant 'All Guests' membership");
      assertEquals(1, savedGroups.size(), "only the default 'All Users' group should be assigned");
    }
  }
}
