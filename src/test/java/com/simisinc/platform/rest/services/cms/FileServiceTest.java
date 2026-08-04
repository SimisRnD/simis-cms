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

package com.simisinc.platform.rest.services.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link FileService} uses {@link LoadFileCommand#loadFileByIdForAuthorizedUser}, which
 * applies the same guest/group folder ACL as {@code DownloadFileWidget}'s non-admin path, rather
 * than only checking {@code Folder.enabled} (issue #412).
 *
 * @author SimIS Inc.
 */
class FileServiceTest {

  private ServiceContext contextFor(String pathParam) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(pathParam);
    return context;
  }

  private ServiceContext contextFor(String pathParam, long userId) {
    ServiceContext context = contextFor(pathParam);
    User user = new User();
    user.setId(userId);
    context.setUser(user);
    return context;
  }

  @Test
  void getReturns404ForANonNumericPathParam() {
    ServiceContext context = contextFor("not-a-number");

    ServiceResponse response = new FileService().get(context);

    assertEquals(404, response.getStatus());
  }

  @Test
  void getReturns404WhenTheCallerHasNoAccessToTheFile() {
    // loadFileByIdForAuthorizedUser returns null both when the file doesn't exist AND when it
    // exists but the caller isn't authorized for its folder -- must not distinguish the two.
    ServiceContext context = contextFor("101", 7L);

    try (MockedStatic<LoadFileCommand> load = mockStatic(LoadFileCommand.class)) {
      load.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(101L, 7L)).thenReturn(null);

      ServiceResponse response = new FileService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getPassesTheParsedFileIdAndTheCallersUserId() {
    ServiceContext context = contextFor("101", 42L);
    FileItem fileItem = new FileItem();
    fileItem.setId(101L);
    fileItem.setFilename("report.pdf");

    try (MockedStatic<LoadFileCommand> load = mockStatic(LoadFileCommand.class)) {
      load.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(101L, 42L)).thenReturn(fileItem);

      ServiceResponse response = new FileService().get(context);

      assertEquals(200, response.getStatus());
      load.verify(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(101L, 42L));
    }
  }

  @Test
  void getReturns200ForAFileTheCallerHasAccessTo() {
    ServiceContext context = contextFor("101", 42L);
    FileItem fileItem = new FileItem();
    fileItem.setId(101L);
    fileItem.setFilename("report.pdf");
    fileItem.setTitle("Annual Report");

    try (MockedStatic<LoadFileCommand> load = mockStatic(LoadFileCommand.class)) {
      load.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(101L, 42L)).thenReturn(fileItem);

      ServiceResponse response = new FileService().get(context);

      assertEquals(200, response.getStatus());
      FileResponse data = (FileResponse) response.getData();
      assertEquals("report.pdf", data.getFilename());
      assertEquals("Annual Report", data.getTitle());
    }
  }
}
