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

import com.simisinc.platform.application.cms.LoadSubFolderCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Returns the specified file
 *
 * @author matt rajkowski
 * @created 8/29/19 9:43 PM
 */
public class PhotoListAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    long subFolderId = context.getParameterAsLong("subFolderId", -1);
    if (subFolderId == -1) {
      context.setJson("[]");
      return context;
    }

    SubFolder subFolder = LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(subFolderId, context.getUserId());
    if (subFolder == null) {
      context.setJson("[]");
      return context;
    }

    // Retrieve the images in the folder
    FileSpecification fileSpecification = new FileSpecification();
    fileSpecification.setFileType("image");
    fileSpecification.setSubFolderId(subFolder.getId());

    DataConstraints fileConstraints = new DataConstraints(1, -1);
    fileConstraints.setUseCount(false);
    fileConstraints.setColumnToSortBy("created", "asc");
    List<FileItem> fileList = FileItemRepository.findAll(fileSpecification, fileConstraints);

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();

    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    for (FileItem fileItem : fileList) {

      String url = context.getContextPath() + "/assets/view/" + sdf.format(fileItem.getCreated()) + "-" + fileItem.getId() + "/" + UrlCommand.encodeUri(fileItem.getFilename());
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"id\":").append(fileItem.getId()).append(",");
      sb.append("\"folderId\":").append(fileItem.getFolderId()).append(",");
      sb.append("\"subFolderId\":").append(fileItem.getSubFolderId()).append(",");
      if (StringUtils.isNotEmpty(fileItem.getSummary())) {
        sb.append("\"summary\":\"").append(JsonCommand.toJson(fileItem.getSummary())).append("\",");
      }
      sb.append("\"title\":\"").append(JsonCommand.toJson(fileItem.getTitle())).append("\",");
      sb.append("\"filename\":\"").append(JsonCommand.toJson(fileItem.getFilename())).append("\",");
      sb.append("\"url\":\"").append(JsonCommand.toJson(url)).append("\"");
      sb.append("}");
    }

    String photoArray = "[" + sb.toString() + "]";

    context.setJson("{" +
        "\"title\": \"" + JsonCommand.toJson(subFolder.getName()) + "\"," +
        "\"photoList\": " + photoArray +
        "}");

    return context;
  }
}
