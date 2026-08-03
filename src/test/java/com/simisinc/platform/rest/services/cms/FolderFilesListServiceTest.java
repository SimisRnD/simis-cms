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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link FolderFilesListService} uses the same guest/group folder ACL
 * ({@link LoadFolderCommand#loadFolderByUniqueIdForAuthorizedUser} +
 * {@link FileSpecification#setForUserId}) as {@code FileListByFolderWidget} on the live site,
 * rather than only checking {@code Folder.enabled} (issue #412).
 *
 * @author SimIS Inc.
 */
class FolderFilesListServiceTest {

  private ServiceContext contextFor(String folderUniqueId) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(folderUniqueId);
    context.setParameterMap(new HashMap<>());
    return context;
  }

  private Folder folder(long id) {
    Folder folder = new Folder();
    folder.setId(id);
    folder.setEnabled(true);
    return folder;
  }

  @Test
  void getReturns404WhenTheCallerHasNoAccessToTheFolder() {
    // loadFolderByUniqueIdForAuthorizedUser returns null both when the folder doesn't exist AND
    // when it exists but the caller (e.g. a guest against a non-guest-accessible folder) isn't
    // authorized -- this endpoint must not distinguish the two in its response.
    ServiceContext context = contextFor("private-folder");

    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(eq("private-folder"), anyLong()))
          .thenReturn(null);

      ServiceResponse response = new FolderFilesListService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getScopesTheFileQueryToTheResolvedFolderIdAndTheCallersUserId() {
    ServiceContext context = contextFor("documents");
    User user = new User();
    user.setId(42L);
    context.setUser(user);
    Folder folder = folder(4L);

    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser("documents", 42L)).thenReturn(folder);
      fileRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      new FolderFilesListService().get(context);

      ArgumentCaptor<FileSpecification> specCaptor = ArgumentCaptor.forClass(FileSpecification.class);
      fileRepo.verify(() -> FileItemRepository.findAll(specCaptor.capture(), any(DataConstraints.class)));
      assertEquals(4L, specCaptor.getValue().getFolderId());
      assertEquals(42L, specCaptor.getValue().getForUserId());
    }
  }

  @Test
  void getPassesTheRequestedPageAndSizeToDataConstraints() {
    ServiceContext context = new ServiceContext();
    context.setPathParam("documents");
    HashMap<String, String[]> params = new HashMap<>();
    params.put("page", new String[] { "2" });
    params.put("size", new String[] { "10" });
    context.setParameterMap(params);
    Folder folder = folder(4L);

    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(eq("documents"), anyLong()))
          .thenReturn(folder);
      fileRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      new FolderFilesListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      fileRepo.verify(() -> FileItemRepository.findAll(any(FileSpecification.class), constraintsCaptor.capture()));
      assertEquals(2, constraintsCaptor.getValue().getPageNumber());
      assertEquals(10, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void getReturns200WithMappedFiles() {
    ServiceContext context = contextFor("documents");
    Folder folder = folder(4L);
    FileItem fileItem = new FileItem();
    fileItem.setId(101L);
    fileItem.setFilename("report.pdf");
    fileItem.setTitle("Annual Report");

    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(eq("documents"), anyLong()))
          .thenReturn(folder);
      fileRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of(fileItem));

      ServiceResponse response = new FolderFilesListService().get(context);

      assertEquals(200, response.getStatus());
      @SuppressWarnings("unchecked")
      List<FileResponse> data = (List<FileResponse>) response.getData();
      assertEquals(1, data.size());
      assertEquals("report.pdf", data.get(0).getFilename());
      assertEquals("Annual Report", data.get(0).getTitle());
    }
  }
}
