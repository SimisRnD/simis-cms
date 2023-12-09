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

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/29/19 1:41 PM
 */
public class PhotoGalleryWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(PhotoGalleryWidget.class);

  private static String JSP = "/cms/photo-gallery.jsp";

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
    context.getRequest().setAttribute("isSticky", context.getPreferences().getOrDefault("isSticky", "false"));
    context.getRequest().setAttribute("marginTop", context.getPreferences().getOrDefault("marginTop", "8"));
    context.getRequest().setAttribute("showCaption", context.getPreferences().getOrDefault("showCaption", "true"));

    // Determine the folder, or all (access is checked, intent permissions are not)
    Folder folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    if (folder == null) {
      LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
      return null;
    }
    context.getRequest().setAttribute("folder", folder);


    // Find the newest album and show it
    DataConstraints constraints = new DataConstraints(1, 1);
    constraints.setColumnToSortBy("start_date", "desc");

    SubFolderSpecification specification = new SubFolderSpecification();
    specification.setFolderId(folder.getId());
    specification.setHasFiles(true);

    List<SubFolder> subFolderList = SubFolderRepository.findAll(specification, constraints);
    if (subFolderList == null || subFolderList.isEmpty()) {
      return context;
    }
    SubFolder subFolder = subFolderList.get(0);
    context.getRequest().setAttribute("subFolder", subFolder);

    // Retrieve the images in the folder
    FileSpecification fileSpecification = new FileSpecification();
    fileSpecification.setFileType("image");
    fileSpecification.setSubFolderId(subFolder.getId());

    DataConstraints fileConstraints = new DataConstraints(1, -1);
    fileConstraints.setUseCount(false);
    fileConstraints.setColumnToSortBy("created", "asc");
    List<FileItem> fileList = FileItemRepository.findAll(fileSpecification, fileConstraints);

    context.getRequest().setAttribute("fileList", fileList);

    context.setJsp(JSP);
    return context;
  }
}
