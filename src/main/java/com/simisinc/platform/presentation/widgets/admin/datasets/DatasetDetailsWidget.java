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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to configure dataset details
 *
 * @author matt rajkowski
 * @created 4/24/18 8:05 PM
 */
public class DatasetDetailsWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(DatasetDetailsWidget.class);

  private static String JSP = "/admin/dataset-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    Dataset dataset = null;
    if (context.getRequestObject() != null) {
      dataset = (Dataset) context.getRequestObject();
      context.getRequest().setAttribute("dataset", dataset);
    } else {
      // Check for the specified dataset
      long datasetId = context.getParameterAsLong("datasetId");
      dataset = DatasetRepository.findById(datasetId);
      if (dataset == null) {
        context.setErrorMessage("Dataset was not found");
        context.setRedirect("/admin/datasets");
        return context;
      }
      context.getRequest().setAttribute("dataset", dataset);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the current dataset
    long datasetId = context.getParameterAsLong("datasetId");
    Dataset dataset = DatasetRepository.findById(datasetId);
    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      return context;
    }

    // Recommend a return URL
    context.setRedirect("/admin/dataset-details?datasetId=" + dataset.getId());

    // Populate dataset fields
    dataset.setName(context.getParameter("name"));
    dataset.setSourceInfo(context.getParameter("sourceInfo"));
    dataset.setModifiedBy(context.getUserId());

    // Validate the required fields
    if (StringUtils.isBlank(dataset.getName())) {
      context.setErrorMessage("A name is required, please check the fields and try again");
      context.setRequestObject(dataset);
      return context;
    }

    // Save the dataset record
    dataset = DatasetRepository.updateDetails(dataset);
    if (dataset == null) {
      context.setErrorMessage("An error occurred, the dataset was not saved");
      context.setRequestObject(dataset);
      return context;
    }

    context.setSuccessMessage("The details were saved successfully");
    return context;
  }
}
