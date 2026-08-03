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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a paginated list of files in a folder the caller has access to (issue #412).
 * <p>
 * Uses {@link LoadFolderCommand#loadFolderByUniqueIdForAuthorizedUser} and
 * {@link FileSpecification#setForUserId} -- the same guest/group ACL
 * ({@code Folder.allowsGuests} + {@code folder_groups}/{@code user_groups} membership) that
 * {@code FileListByFolderWidget} applies on the live site, embedded directly in
 * {@code FileItemRepository}'s WHERE-clause construction. A prior version of this class
 * incorrectly claimed no such ACL was enforced anywhere -- it is, and this endpoint now respects
 * it rather than exposing every enabled folder's contents to an anonymous/guest API caller.
 * </p>
 *
 * @author SimIS Inc.
 */
public class FolderFilesListService {

  private static Log LOG = LogFactory.getLog(FolderFilesListService.class);

  // GET /files/{folderUniqueId}?page={pageNumber}&size={pageSize}
  public ServiceResponse get(ServiceContext context) {

    String folderUniqueId = context.getPathParam();
    Folder folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    if (folder == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Folder was not found");
      return response;
    }

    int pageNumber = context.getParameterAsInt("page", 1);
    int pageSize = context.getParameterAsInt("size", 20);
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    FileSpecification specification = new FileSpecification();
    specification.setFolderId(folder.getId());
    specification.setForUserId(context.getUserId());

    List<FileItem> fileItemList = FileItemRepository.findAll(specification, constraints);

    List<FileResponse> recordList = new ArrayList<>();
    for (FileItem fileItem : fileItemList) {
      recordList.add(new FileResponse(fileItem));
    }

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "file", recordList, constraints);
    response.setData(recordList);
    return response;
  }
}
