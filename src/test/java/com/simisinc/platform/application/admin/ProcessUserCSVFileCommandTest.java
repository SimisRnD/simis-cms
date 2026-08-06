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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.events.cms.UserInvitedEvent;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * The manual "Add User" path (UsersListWidget.addUserAction) fires a UserInvitedEvent so the new
 * account gets its invite/welcome email; the bulk CSV-import path historically did not, so
 * CSV-imported users were silently missing that email. These cover that a successfully saved row
 * fires the same event, resolving "invitedBy" from the uploading admin, and that a row whose save
 * did not produce a persisted user (saveUser() returned null, guarded the same way the audit event
 * already was) does not fire it.
 *
 * @author SimIS Inc.
 */
class ProcessUserCSVFileCommandTest extends WidgetBase {

  private static Group allUsersGroup() {
    Group group = new Group();
    group.setId(1L);
    group.setName("All Users");
    return group;
  }

  private static User invitingAdmin() {
    User admin = new User();
    admin.setId(1L);
    admin.setEmail("admin@example.com");
    return admin;
  }

  private static User savedUser(String email) {
    User user = new User();
    user.setId(42L);
    user.setEmail(email);
    return user;
  }

  private static Path writeCsv(Path dir, String content) throws Exception {
    Path csv = dir.resolve("import.csv");
    Files.writeString(csv, content, StandardCharsets.UTF_8);
    return csv;
  }

  @Test
  void successfullyImportedRowTriggersInviteEvent(@TempDir Path tempDir) throws Exception {
    Path csvFile = writeCsv(tempDir, "Email,First Name,Last Name\ncsv-user@example.com,CSV,User\n");

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/import.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(any())).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString());
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(any(), any())).thenReturn(csvFile.toFile());
      groupRepo.when(() -> GroupRepository.findByName("All Users")).thenReturn(allUsersGroup());
      userRepo.when(() -> UserRepository.findByUsername(anyString())).thenReturn(null);
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(invitingAdmin());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser("csv-user@example.com"));

      int userCount = ProcessUserCSVFileCommand.processCSV(widgetContext);
      assertEquals(1, userCount);

      // The invite/welcome email is sent asynchronously off this event, the same way it is for the
      // manual Add User form -- resolved once from the uploading admin (userId 1, per WidgetBase.login())
      ArgumentCaptor<UserInvitedEvent> captor = ArgumentCaptor.forClass(UserInvitedEvent.class);
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(captor.capture()));
      assertEquals("csv-user@example.com", captor.getValue().getUser().getEmail());
      assertEquals("admin@example.com", captor.getValue().getInvitedBy().getEmail());
    }
  }

  @Test
  void rowWhoseSaveDidNotReturnAUserDoesNotTriggerInviteEvent(@TempDir Path tempDir) throws Exception {
    Path csvFile = writeCsv(tempDir, "Email,First Name,Last Name\ncsv-user@example.com,CSV,User\n");

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/import.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(any())).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString());
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(any(), any())).thenReturn(csvFile.toFile());
      groupRepo.when(() -> GroupRepository.findByName("All Users")).thenReturn(allUsersGroup());
      userRepo.when(() -> UserRepository.findByUsername(anyString())).thenReturn(null);
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(invitingAdmin());
      // Same guard the per-row audit event already relies on (saved != null)
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(null);

      ProcessUserCSVFileCommand.processCSV(widgetContext);

      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }
}
