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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.application.cms.DeleteSubFolderCommand;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/19 5:54 PM
 */
public class SubFolderDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sub-folder-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the sub-folder
    long subFolderId = context.getParameterAsLong("subFolderId");
    SubFolder subFolder = SubFolderRepository.findById(subFolderId);
    if (subFolder == null) {
      context.setErrorMessage("Error. Sub-Folder was not found.");
      return context;
    }
    context.getRequest().setAttribute("subFolder", subFolder);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long subFolderId = context.getParameterAsLong("subFolderId");
    if (subFolderId > -1) {
      SubFolder subFolder = SubFolderRepository.findById(subFolderId);
      try {
        DeleteSubFolderCommand.deleteSubFolder(subFolder);
        context.setSuccessMessage("Sub-Folder deleted");
        context.setRedirect("/admin/folder-details?folderId=" + subFolder.getFolderId());
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Sub-Folder could not be deleted.");
//        context.setRedirect("/admin/folders");
        return context;
      }
    }

    return context;
  }
}
