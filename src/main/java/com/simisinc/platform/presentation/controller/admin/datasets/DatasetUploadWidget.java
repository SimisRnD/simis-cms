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

package com.simisinc.platform.presentation.controller.admin.datasets;

import com.simisinc.platform.application.admin.DatasetFileCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:05 PM
 */
public class DatasetUploadWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/dataset-upload-form.jsp";
  private static Log LOG = LogFactory.getLog(DatasetUploadWidget.class);

  public WidgetContext execute(WidgetContext context) {
    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Check the form values
    String name = context.getParameter("name");
    String sourceUrl = context.getParameter("sourceUrl");
    String fileType = context.getParameter("fileType");
    String sourceInfo = context.getParameter("sourceInfo");

    // Populate the fields
    Dataset datasetBean = new Dataset();
    datasetBean.setName(name);
    datasetBean.setCreatedBy(context.getUserId());
    datasetBean.setModifiedBy(context.getUserId());
    if (StringUtils.isNotBlank(sourceUrl)) {
      datasetBean.setSourceUrl(sourceUrl.trim());
    }
    if (StringUtils.isNotBlank(sourceInfo)) {
      datasetBean.setSourceInfo(sourceInfo.trim());
    }
    LOG.info("fileType: " + fileType);
    if (DatasetFileCommand.handleNewFile(context, datasetBean, fileType)) {
      LOG.info("New dataset id... " + datasetBean.getId());
      context.setRedirect("/admin/dataset-preview?datasetId=" + datasetBean.getId());
      return context;
    } else {
      context.setErrorMessage("Dataset was not processed for type " + fileType);
      return context;
    }
  }

}
