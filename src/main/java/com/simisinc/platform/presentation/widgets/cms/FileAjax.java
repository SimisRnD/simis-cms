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

import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Returns the specified file
 *
 * @author matt rajkowski
 * @created 12/18/18 11:44 AM
 */
public class FileAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    long id = context.getParameterAsLong("id", -1);
    if (id == -1) {
      context.setJson("[]");
      return context;
    }

    // Access the file
    FileItem fileItem = null;
    if (context.hasRole("admin")) {
      // The file can be downloaded
      fileItem = LoadFileCommand.loadItemById(id);
    } else {
      // User must have view access in the folder's user group
      fileItem = LoadFileCommand.loadFileByIdForAuthorizedUser(id, context.getUserId());
    }
    if (fileItem == null) {
      context.setJson("[]");
      return context;
    }

    // Determine the values to be shown
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"id\":").append(fileItem.getId()).append(",");
    sb.append("\"folderId\":").append(fileItem.getFolderId()).append(",");
    sb.append("\"subFolderId\":").append(fileItem.getSubFolderId()).append(",");
    sb.append("\"categoryId\":").append(fileItem.getCategoryId()).append(",");
    if (StringUtils.isNotEmpty(fileItem.getSummary())) {
      sb.append("\"summary\":\"").append(JsonCommand.toJson(fileItem.getSummary())).append("\",");
    }
    if (StringUtils.isNotEmpty(fileItem.getVersion())) {
      sb.append("\"version\":\"").append(JsonCommand.toJson(fileItem.getVersion())).append("\",");
    }
    sb.append("\"title\":\"").append(JsonCommand.toJson(fileItem.getTitle())).append("\",");
    sb.append("\"filename\":\"").append(JsonCommand.toJson(fileItem.getFilename())).append("\"");
    sb.append("}");

    context.setJson(sb.toString());
    return context;
  }
}
