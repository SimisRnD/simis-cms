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

package com.simisinc.platform.presentation.widgets.admin.datasets;

import com.simisinc.platform.application.admin.DeleteDatasetCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 7:33 PM
 */
public class DatasetsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/datasets-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Load the datasets
    List<Dataset> datasetList = DatasetRepository.findAll();
    context.getRequest().setAttribute("datasetList", datasetList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Check for a shared error from another area
    String datasetError = context.getSharedRequestValue("datasetError");
    if (StringUtils.isNotBlank(datasetError)) {
      context.setErrorMessage(datasetError);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long datasetId = context.getParameterAsLong("datasetId");
    if (datasetId > -1) {
      Dataset dataset = DatasetRepository.findById(datasetId);
      if (dataset == null) {
        context.setErrorMessage("Dataset not found");
      } else {
        if (DeleteDatasetCommand.delete(dataset)) {
          context.setSuccessMessage("Dataset deleted");
        } else {
          context.setWarningMessage("Dataset could not be deleted, there are dependencies");
        }
      }
    }
    context.setRedirect("/admin/datasets");
    return context;
  }
}
