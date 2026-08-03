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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.RestoreFileVersionCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/file-versions admin page (issue #502): lists a folder file's prior uploaded versions
 * (uploader, date, size, version label) and offers a restore action that promotes an archived
 * version's still-on-disk file back to being the live/current file.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class FileVersionsListWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(FileVersionsListWidget.class);

  static final long serialVersionUID = -8484048371911908896L;

  static String JSP = "/admin/file-versions-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    long fileId = context.getParameterAsLong("fileId", -1);
    FileItem file;
    if (context.hasRole("admin")) {
      file = FileItemRepository.findById(fileId);
    } else {
      file = LoadFileCommand.loadFileByIdForAuthorizedUser(fileId, context.getUserId());
    }
    if (file == null) {
      context.setErrorMessage("Error. File was not found.");
      return context;
    }
    context.getRequest().setAttribute("file", file);

    Folder folder = FolderRepository.findById(file.getFolderId());
    context.getRequest().setAttribute("folder", folder);
    if (file.getSubFolderId() > -1) {
      SubFolder subFolder = SubFolderRepository.findById(file.getSubFolderId());
      context.getRequest().setAttribute("subFolder", subFolder);
    }

    // Determine if the current user can restore a version (same bar as adding a file version)
    boolean canRestore = context.hasRole("admin") || context.hasRole("content-manager");
    if (canRestore && !context.hasRole("admin")) {
      canRestore = CheckFolderPermissionCommand.userHasAddPermission(file.getFolderId(), context.getUserId());
    }
    context.getRequest().setAttribute("canRestore", canRestore ? "true" : "false");

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    FileVersionSpecification specification = new FileVersionSpecification();
    specification.setFileId(fileId);
    List<FileVersion> versionList = FileVersionRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("versionList", versionList);

    context.getRequest().setAttribute("recordPagingParams", "fileId=" + fileId);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    if (!"restore".equals(context.getParameter("action"))) {
      return null;
    }

    // Permission is required -- restoring re-uses the same effect as adding a file version
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to restore a file version");
      return null;
    }
    context.getUserSession().renewFormToken();

    long fileVersionId = context.getParameterAsLong("fileVersionId", -1);
    FileVersion fileVersion = fileVersionId > -1 ? FileVersionRepository.findById(fileVersionId) : null;
    if (fileVersion == null) {
      context.setErrorMessage("The selected version was not found");
      return execute(context);
    }

    // The version must belong to the file the request claims it does
    long fileId = context.getParameterAsLong("fileId", -1);
    if (fileVersion.getFileId() != fileId) {
      LOG.warn("File version " + fileVersionId + " does not belong to file " + fileId);
      context.setErrorMessage("The selected version was not found");
      return execute(context);
    }

    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(fileVersion.getFolderId(), context.getUserId())) {
        LOG.warn("No permission to restore a version in this folder");
        context.setErrorMessage("You do not have permission to restore this file");
        return execute(context);
      }
    }

    try {
      FileItem restoredFile = RestoreFileVersionCommand.restore(fileVersion, context.getUserId());
      if (restoredFile == null) {
        throw new DataException("The version could not be restored due to a system error. Please try again.");
      }
      context.setSuccessMessage("The selected version is now the current file.");
    } catch (DataException e) {
      LOG.debug("An exception occurred: " + e.getMessage());
      context.setErrorMessage(e.getMessage());
    }

    return execute(context);
  }
}
