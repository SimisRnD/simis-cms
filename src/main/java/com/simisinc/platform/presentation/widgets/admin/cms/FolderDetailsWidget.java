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

import com.simisinc.platform.application.cms.DeleteFolderCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/12/18 4:33 PM
 */
public class FolderDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/folder-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the folder
    long folderId = context.getParameterAsLong("folderId");
    Folder folder = FolderRepository.findById(folderId);
    if (folder == null) {
      context.setErrorMessage("Error. Folder was not found.");
      return context;
    }
    context.getRequest().setAttribute("folder", folder);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long folderId = context.getParameterAsLong("folderId");
    if (folderId > -1) {
      Folder folder = FolderRepository.findById(folderId);
      try {
        DeleteFolderCommand.deleteFolder(folder);
        context.setSuccessMessage("Folder deleted");
        context.setRedirect("/admin/folders");
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Folder could not be deleted.");
        context.setRedirect("/admin/folders");
        return context;
      }
    }

    return context;
  }
}
