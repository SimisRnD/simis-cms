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
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.*;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/18/19 9:20 AM
 */
public class FileListByFolderWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(FileListByFolderWidget.class);

  private static String JSP = "/cms/file-explorer.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));

    // Preferences
    context.getRequest().setAttribute("useViewer", context.getPreferences().getOrDefault("useViewer", "false"));
    context.getRequest().setAttribute("useDateForTitle", context.getPreferences().getOrDefault("useDateForTitle", "false"));
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
//    String rules = context.getPreferences().get("rules");
//    String orderBy = context.getPreferences().get("orderBy");
//    int withinLastDays = Integer.parseInt(context.getPreferences().getOrDefault("withinLastDays", "-1"));
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

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "12"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);

    // Determine the sorting and filters, use values for the request that are separate from the database values
    String typeFilterValue = context.getParameter("typeFilter", "any");
    String yearFilterValue = context.getParameter("yearFilter", "any");
    String sortByValue = context.getParameter("sortBy", "date");
    String sortOrderValue = context.getParameter("sortOrder", "newest");

    // Load the categories for the drop-down
    List<FolderCategory> folderCategoryList = FolderCategoryRepository.findAllByFolderId(folder.getId());
    context.getRequest().setAttribute("folderCategoryList", folderCategoryList);

    // Validate the typeFilter
    String typeFilter = "any";
    if (folderCategoryList != null) {
      for (FolderCategory type : folderCategoryList) {
        if (type.getName().equals(typeFilterValue)) {
          typeFilter = typeFilterValue;
          break;
        }
      }
    }
    context.getRequest().setAttribute("typeFilter", typeFilter);

    // Load the start date years for the drop-down from sub-folders
    List<Long> folderYearList = SubFolderRepository.queryDistinctStartDateAsYearForFolder(folder);
    context.getRequest().setAttribute("folderYearList", folderYearList);

    // Validate the yearFilter
    String yearFilter = "any";
    if (!"any".equals(yearFilterValue)) {
      for (Long year : folderYearList) {
        if (Long.parseLong(yearFilterValue) == year) {
          yearFilter = yearFilterValue;
          break;
        }
      }
    }
    context.getRequest().setAttribute("yearFilter", yearFilter);

    String pagingUri = "";
    if (!"date".equals(sortByValue) || !"newest".equals(sortOrderValue) || !"any".equals(typeFilter) || !"any".equals(yearFilter)) {
      pagingUri =
          "&typeFilter=" + UrlCommand.encodeUri(typeFilter) +
              "&yearFilter=" + UrlCommand.encodeUri(yearFilter) +
              "&sortBy=" + UrlCommand.encodeUri(sortByValue) +
              "&sortOrder=" + UrlCommand.encodeUri(sortOrderValue);
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, pagingUri);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortByValue);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_ORDER, sortOrderValue);

    // Year = any, 2019, etc.
    // Type = any, Agendas, etc.


    // Load the sub-folders by date
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setColumnToSortBy("start_date", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    SubFolderSpecification specification = new SubFolderSpecification();
    specification.setFolderId(folder.getId());
    specification.setHasFiles(true);
    if (!"any".equals(yearFilter)) {
      specification.setYear(Long.parseLong(yearFilter));
    }
    List<SubFolder> subFolderList = SubFolderRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("subFolderList", subFolderList);
    LOG.debug("Sub-folders found: " + subFolderList.size());

    // For each sub-folder, find the corresponding files
    boolean hasFiles = false;
    for (SubFolder subFolder : subFolderList) {
      FileSpecification fileSpecification = new FileSpecification();
      fileSpecification.setSubFolderId(subFolder.getId());
      fileSpecification.setForUserId(context.getUserId());

      List<FileItem> fileItemList = FileItemRepository.findAll(fileSpecification, null);
      if (fileItemList != null && !fileItemList.isEmpty()) {
        subFolder.setFileItemList(fileItemList);
        hasFiles = true;
      }
    }

    // Determine if the view will be displayed
    if (!hasFiles && !"true".equals(showWhenEmpty)) {
      return null;
    }

    // Show the view
    context.setJsp(JSP);
    return context;
  }
}
