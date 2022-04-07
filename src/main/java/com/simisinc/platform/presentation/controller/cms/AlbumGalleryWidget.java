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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/29/19 1:41 PM
 */
public class AlbumGalleryWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(AlbumGalleryWidget.class);

  private static String JSP = "/cms/album-gallery.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Check the folder
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
    if (StringUtils.isBlank(folderUniqueId)) {
      LOG.warn("Preference folderUniqueId is required");
      return null;
    }

    // Preferences
    context.getRequest().setAttribute("controlId", context.getPreferences().getOrDefault("controlId", "myAlbum"));
    context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));
    // Card size preferences
    String smallCardCount = context.getPreferences().getOrDefault("smallCardCount", "6");
    context.getRequest().setAttribute("smallCardCount", smallCardCount);
    String mediumCardCount = context.getPreferences().getOrDefault("mediumCardCount", smallCardCount);
    context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
    context.getRequest().setAttribute("largeCardCount", context.getPreferences().getOrDefault("largeCardCount", mediumCardCount));

    // Determine the folder, or all (access is checked, intent permissions are not)
    Folder folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    if (folder == null) {
      LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
      return null;
    }
    context.getRequest().setAttribute("folder", folder);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "12"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setColumnToSortBy("start_date", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the criteria
    SubFolderSpecification specification = new SubFolderSpecification();
    specification.setFolderId(folder.getId());
    specification.setHasFiles(true);

    // Get the sub-folders
    List<SubFolder> subFolderList = SubFolderRepository.findAll(specification, constraints);
    if (subFolderList.isEmpty()) {
      return context;
    }

    // Retrieve the poster image
    FileSpecification fileSpecification = new FileSpecification();
    fileSpecification.setFileType("image");

    for (SubFolder subFolder : subFolderList) {
      fileSpecification.setSubFolderId(subFolder.getId());

      DataConstraints fileConstraints = new DataConstraints(1, 1);
      fileConstraints.setUseCount(false);
      fileConstraints.setColumnToSortBy("created", "asc");

      List<FileItem> fileList = FileItemRepository.findAll(fileSpecification, fileConstraints);
      subFolder.setPosterFileItem(fileList.get(0));
    }
    context.getRequest().setAttribute("subFolderList", subFolderList);

    // Show the web page
    context.setJsp(JSP);
    return context;
  }
}
