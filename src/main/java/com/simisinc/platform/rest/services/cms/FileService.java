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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a single file's public metadata, if the caller has access to its folder (issue #412).
 * <p>
 * Registered as {@code file/{fileId}}, keyed by the numeric id rather than {@code fileUniqueId}
 * as originally specced -- {@link FileItem} has no {@code uniqueId} field the way
 * {@link Folder}/{@code Blog}/{@code WebPage} do (it has {@code barcode} instead, which serves a
 * different purpose). Revisit if a durable public file identifier is actually needed.
 * </p>
 * <p>
 * Uses {@link LoadFileCommand#loadFileByIdForAuthorizedUser}, which applies the same guest/group
 * folder ACL as {@code DownloadFileWidget}'s non-admin path -- a prior version of this class
 * incorrectly claimed no such ACL was enforced anywhere and only checked {@code Folder.enabled}.
 * </p>
 *
 * @author SimIS Inc.
 */
public class FileService {

  private static Log LOG = LogFactory.getLog(FileService.class);

  // GET /file/{fileId}
  public ServiceResponse get(ServiceContext context) {

    long fileId = context.getPathParam() != null && StringUtils.isNumeric(context.getPathParam())
        ? Long.parseLong(context.getPathParam())
        : -1L;
    FileItem fileItem = LoadFileCommand.loadFileByIdForAuthorizedUser(fileId, context.getUserId());
    if (fileItem == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "File was not found");
      return response;
    }

    FileResponse fileResponse = new FileResponse(fileItem);

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "file");
    response.setData(fileResponse);
    return response;
  }
}
