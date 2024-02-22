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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Displays a list of files
 *
 * @author matt rajkowski
 * @created 12/17/18 4:14 PM
 */
public class FileListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(FileListWidget.class);

  private static String JSP = "/cms/file-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("useViewer", context.getPreferences().getOrDefault("useViewer", "false"));
    context.getRequest().setAttribute("showLinks", context.getPreferences().getOrDefault("showLinks", "true"));
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
    String rules = context.getPreferences().get("rules");
    String orderBy = context.getPreferences().get("orderBy");
    int withinLastDays = Integer.parseInt(context.getPreferences().getOrDefault("withinLastDays", "-1"));
    String showWhenEmpty = context.getPreferences().getOrDefault("showWhenEmpty", "true");

    // Determine the folder, or all (access is checked, intent permissions are not)
    Folder folder = null;
    if (StringUtils.isNotBlank(folderUniqueId)) {
      folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
      if (folder == null) {
        LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
        return null;
      }
    }

    // Determine the specifications
    FileSpecification fileSpecification = new FileSpecification();
    if (folder != null) {
      fileSpecification.setFolderId(folder.getId());
    }
    if (withinLastDays > 0) {
      fileSpecification.setWithinLastDays(withinLastDays);
    }
    // Determine the permissions for viewing files
    if (rules != null && rules.contains("user-created")) {
      // Let the user see the files they created
      fileSpecification.setCreatedBy(context.getUserId());
    } else {
      // Enforce the folder access (like drop box rule)
      fileSpecification.setForUserId(context.getUserId());
    }

    // Determine the constraints
    DataConstraints constraints = new DataConstraints();
    if ("newest".equals(orderBy)) {
      constraints.setColumnToSortBy("created", "desc");
    } else if ("oldest".equals(orderBy)) {
      constraints.setColumnToSortBy("created", "asc");
    } else if ("reverse".equals(orderBy) || "descending".equals(orderBy)) {
      constraints.setColumnToSortBy("title", "desc");
    } else {
      constraints.setColumnToSortBy("title", "asc");
    }
    constraints.setPageSize(-1);

    // Load the list
    List<FileItem> fileItemList = FileItemRepository.findAll(fileSpecification, constraints);
    if (fileItemList == null || fileItemList.isEmpty()) {
      if (!"true".equals(showWhenEmpty)) {
        return context;
      }
    }
    context.getRequest().setAttribute("fileItemList", fileItemList);

    // Show the view
    context.setJsp(JSP);
    return context;
  }
}
