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
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/19/19 3:24 PM
 */
public class FileListByYearWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(FileListByYearWidget.class);

  private static String JSP = "/cms/file-folder-tabs.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("useViewer", context.getPreferences().getOrDefault("useViewer", "false"));
    context.getRequest().setAttribute("useDateForTitle", context.getPreferences().getOrDefault("useDateForTitle", "false"));
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
    String showWhenEmpty = context.getPreferences().getOrDefault("showWhenEmpty", "true");

    // Determine the folder, or all (access is checked, intent permissions are not)
    Folder folder = null;
    if (StringUtils.isNotBlank(folderUniqueId)) {
      folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    }
    if (folder == null) {
      LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
      return null;
    }


    // Load the start date years for the tabs
    List<Long> folderYearList = SubFolderRepository.queryDistinctStartDateAsYearForFolder(folder);
    context.getRequest().setAttribute("folderYearList", folderYearList);

    // Load the sub-folders by date
    DataConstraints constraints = new DataConstraints();
    constraints.setColumnToSortBy("start_date", "desc");

    // For each Year, find the sub-folders and files
    Map<Long, List<SubFolder>> folderYearMap = new LinkedHashMap<>();
    for (Long year : folderYearList) {

      SubFolderSpecification specification = new SubFolderSpecification();
      specification.setFolderId(folder.getId());
      specification.setHasFiles(true);
      specification.setYear(year);
      List<SubFolder> subFolderList = SubFolderRepository.findAll(specification, constraints);
      if (subFolderList.isEmpty()) {
        continue;
      }

      // For each sub-folder, find the corresponding files
      for (SubFolder subFolder : subFolderList) {
        FileSpecification fileSpecification = new FileSpecification();
        fileSpecification.setSubFolderId(subFolder.getId());
        fileSpecification.setForUserId(context.getUserId());
        List<FileItem> fileItemList = FileItemRepository.findAll(fileSpecification, null);
        if (fileItemList != null && !fileItemList.isEmpty()) {
          subFolder.setFileItemList(fileItemList);
        }
      }
      folderYearMap.put(year, subFolderList);
    }

    // Determine if the view will be displayed
    if (folderYearMap.isEmpty() && !"true".equals(showWhenEmpty)) {
      return null;
    }
    context.getRequest().setAttribute("folderYearMap", folderYearMap);

    // Show the view
    context.setJsp(JSP);
    return context;
  }
}
