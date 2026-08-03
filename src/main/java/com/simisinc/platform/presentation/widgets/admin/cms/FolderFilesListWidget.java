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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteFileCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderCategoryRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/12/18 4:33 PM
 */
public class FolderFilesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/folder-files-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Use the folder for permissions
    Folder folder;

    // Check for a sub-folder
    long folderId = -1;
    long subFolderId = context.getParameterAsLong("subFolderId");
    if (subFolderId > -1) {
      SubFolder subFolder = SubFolderRepository.findById(subFolderId);
      if (subFolder == null) {
        context.setErrorMessage("Error. Sub-Folder was not found.");
        return context;
      }
      folderId = subFolder.getFolderId();
      context.getRequest().setAttribute("subFolder", subFolder);
    } else {
      // Determine the folder
      folderId = context.getParameterAsLong("folderId");
    }

    if (context.hasRole("admin")) {
      folder = FolderRepository.findById(folderId);
    } else {
      folder = LoadFolderCommand.loadFolderByIdForAuthorizedUser(folderId, context.getUserId());
    }
    if (folder == null) {
      context.setErrorMessage("Error. Folder was not found.");
      return context;
    }
    context.getRequest().setAttribute("folder", folder);

    // Determine permissions for UI
    boolean canEdit = CheckFolderPermissionCommand.userHasEditPermission(folder.getId(), context.getUserId());
    boolean canDelete = CheckFolderPermissionCommand.userHasDeletePermission(folder.getId(), context.getUserId());
    if (context.hasRole("admin")) {
      canEdit = true;
      canDelete = true;
    }
    context.getRequest().setAttribute("canEdit", canEdit ? "true" : "false");
    context.getRequest().setAttribute("canDelete", canDelete ? "true" : "false");

    // Load the folders for the drop-down so user can move file to different folder
    List<Folder> folderList;
    if (context.hasRole("admin")) {
      folderList = FolderRepository.findAll();
    } else {
      folderList = LoadFolderCommand.findAllAuthorizedForUser(context.getUserId());
    }
    context.getRequest().setAttribute("folderList", folderList);

    // Load the sub-folders for the drop-down so user can move file to different sub-folder
    SubFolderSpecification subFolderSpecification = new SubFolderSpecification();
    subFolderSpecification.setFolderId(folder.getId());
    List<SubFolder> subFolderList = SubFolderRepository.findAll(subFolderSpecification, null);
    context.getRequest().setAttribute("subFolderList", subFolderList);

    // Load the categories for the drop-down
    List<FolderCategory> folderCategoryList = FolderCategoryRepository.findAllByFolderId(folder.getId());
    context.getRequest().setAttribute("folderCategoryList", folderCategoryList);

    // Determine the files to show
    FileSpecification specification = new FileSpecification();
    specification.setFolderId(folder.getId());
    if (subFolderId > -1) {
      specification.setSubFolderId(subFolderId);
    } else {
      specification.setInASubFolder(false);
    }

    // Search by filename/title (issue #502)
    String query = context.getParameter(RequestConstants.RECORD_QUERY);
    context.getRequest().setAttribute(RequestConstants.RECORD_QUERY, query);
    if (StringUtils.isNotBlank(query)) {
      specification.setSearchTerm(query.trim());
    }

    // Sort by name/date/size/downloads (issue #502) -- an invalid or missing value falls back to
    // "date", which maps to the same "created DESC" order this list used before sorting existed,
    // so a plain page load (no sortBy param) keeps its prior ordering.
    String sortBy = context.getParameter(RequestConstants.RECORD_SORT_BY, "date");
    DataConstraints constraints = new DataConstraints();
    switch (sortBy) {
      case "name":
        constraints.setColumnToSortBy("title");
        break;
      case "size":
        constraints.setColumnToSortBy("file_length", "desc");
        break;
      case "downloads":
        constraints.setColumnToSortBy("download_count", "desc");
        break;
      case "date":
      default:
        sortBy = "date";
        constraints.setColumnToSortBy("created", "desc");
        break;
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortBy);

    // Load the files
    List<FileItem> fileList = FileItemRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("fileList", fileList);

    // Determine how many versions each file has on record, so the UI only offers a "Version
    // History" link when there's actually a prior version to show/restore (issue #502). The
    // files.version_count column exists but is never populated by any writer, so it can't be
    // trusted for this -- count file_versions directly instead.
    Map<Long, Long> versionCountMap = new HashMap<>();
    for (FileItem fileItem : fileList) {
      versionCountMap.put(fileItem.getId(), FileVersionRepository.countByFileId(fileItem.getId()));
    }
    context.getRequest().setAttribute("versionCountMap", versionCountMap);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * A file is being updated
   *
   * @param context
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to update the file");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Determine the return page
    long currentFolderId = Long.parseLong(context.getRequest().getParameter("currentFolderId"));
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(currentFolderId, context.getUserId())) {
        LOG.warn("No permission to update modify the folder");
        return null;
      }
    }
    long currentSubFolderId = Long.parseLong(context.getRequest().getParameter("currentSubFolderId"));

    // Determine if there is a new file version
    FileItem fileItemBean = null;
    boolean isNewVersion = false;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      isNewVersion = (fileItemBean != null);
      if (isNewVersion) {
        // There's a new document version
        fileItemBean.setId(context.getParameterAsLong("id"));
        fileItemBean.setFolderId(context.getParameterAsLong("folderId"));
        fileItemBean.setSubFolderId(context.getParameterAsLong("subFolderId"));
        fileItemBean.setCategoryId(context.getParameterAsLong("categoryId"));
        fileItemBean.setVersion(context.getParameter("version"));
        fileItemBean.setTitle(context.getParameter("title"));
        fileItemBean.setSummary(context.getParameter("summary"));
        fileItemBean.setCreatedBy(context.getUserId());
        fileItemBean.setModifiedBy(context.getUserId());
        // Validate the file
        ValidateFileCommand.checkFile(fileItemBean);
        // Insert a version record, then update the file item to the latest details
        FileItem fileItem = SaveFileCommand.saveNewVersionOfFile(fileItemBean);
        if (fileItem == null) {
          throw new DataException("Your information could not be saved due to a system error. Please try again.");
        }
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "folder_file.version", AuditEventCommand.SUCCESS,
            "folder_file", String.valueOf(fileItem.getId()), fileItem.getFilename(), "version=" + fileItem.getVersion());
      } else {
        // It's a form update of an old version
        // Populate the fields
        fileItemBean = new FileItem();
        BeanUtils.populate(fileItemBean, context.getParameterMap());
        fileItemBean.setCreatedBy(context.getUserId());
        fileItemBean.setModifiedBy(context.getUserId());
        // Update the file item
        FileItem fileItem = SaveFileCommand.saveFile(fileItemBean);
        if (fileItem == null) {
          throw new AppException("The information could not be saved due to a system error. Please try again.");
        }
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "folder_file.update", AuditEventCommand.SUCCESS,
            "folder_file", String.valueOf(fileItem.getId()), fileItem.getFilename(), null);
      }
    } catch (AppException | DataException data) {
      LOG.debug("An exception occurred: " + data.getMessage());
      // Clean up the file if it exists
      SaveFilePartCommand.cleanupFile(fileItemBean);
      // Let the user know
      context.setErrorMessage(data.getMessage());
      context.setRequestObject(fileItemBean);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT,
          isNewVersion ? "folder_file.version" : "folder_file.update", AuditEventCommand.FAILURE,
          "folder_file", fileItemBean != null ? String.valueOf(fileItemBean.getId()) : "-1",
          fileItemBean != null ? fileItemBean.getFilename() : null, data.getMessage());
    }

    // Determine the page to return to
    if (currentSubFolderId > 0) {
      context.setRedirect("/admin/sub-folder-details?folderId=" + currentFolderId + "&subFolderId=" + currentSubFolderId);
    } else {
      context.setRedirect("/admin/folder-details?folderId=" + currentFolderId);
    }
    return context;
  }

  /**
   * A file is being deleted
   *
   * @param context
   * @return
   */
  public WidgetContext delete(WidgetContext context) {

    // Check for file to be deleted
    long fileId = context.getParameterAsLong("fileId", -1);
    FileItem record;
    if (context.hasRole("admin")) {
      record = LoadFileCommand.loadItemById(fileId);
    } else {
      record = LoadFileCommand.loadFileByIdForAuthorizedUser(fileId, context.getUserId());
    }
    if (record == null) {
      LOG.warn("File record does not exist or no access: " + fileId);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "folder_file.delete", AuditEventCommand.FAILURE,
          "folder_file", String.valueOf(fileId), null, "not found or access denied");
      return null;
    }

    // @todo make sure the folder's user group can delete

    String targetId = String.valueOf(record.getId());
    String targetLabel = record.getFilename();
    try {
      // NOTE: the pre-existing "File deleted" success message/redirect below does not check
      // DeleteFileCommand.deleteFile()'s boolean return value (a latent bug, out of scope here); the
      // audit record below uses the real return value so it reflects what actually happened.
      boolean removed = DeleteFileCommand.deleteFile(record);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "folder_file.delete",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE, "folder_file", targetId, targetLabel, null);
      context.setSuccessMessage("File deleted");
      if (record.getSubFolderId() > -1) {
        context.setRedirect("/admin/sub-folder-details?folderId=" + record.getFolderId() + "&subFolderId=" + record.getSubFolderId());
      } else {
        context.setRedirect("/admin/folder-details?folderId=" + record.getFolderId());
      }
      return context;
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "folder_file.delete", AuditEventCommand.FAILURE,
          "folder_file", targetId, targetLabel, e.getMessage());
      context.setErrorMessage("Error. File could not be deleted.");
//        context.setRedirect("/admin/collections");
    }

    return context;
  }
}
