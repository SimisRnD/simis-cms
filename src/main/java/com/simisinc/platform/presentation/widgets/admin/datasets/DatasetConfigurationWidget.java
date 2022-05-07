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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.DatasetColumnJSONCommand;
import com.simisinc.platform.application.admin.DatasetFileCommand;
import com.simisinc.platform.application.admin.DeleteDatasetCommand;
import com.simisinc.platform.application.admin.LoadJsonCommand;
import com.simisinc.platform.application.admin.SaveDatasetCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:05 PM
 */
public class DatasetConfigurationWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/dataset-configuration.jsp";
  private static Log LOG = LogFactory.getLog(DatasetConfigurationWidget.class);

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
      String datasetIdValue = context.getSharedRequestValue("datasetId");
      if (datasetId == -1 && StringUtils.isNumeric(datasetIdValue)) {
        datasetId = Long.parseLong(datasetIdValue);
      }
      dataset = DatasetRepository.findById(datasetId);
      if (dataset == null) {
        context.setErrorMessage("Dataset was not found");
        context.setRedirect("/admin/datasets");
        return context;
      }
      context.getRequest().setAttribute("dataset", dataset);
    }

    // Convert JSON config to plain-text columnConfiguration
    if (dataset.getFileType().contains("json")) {
      String columnConfiguration = DatasetColumnJSONCommand.createPlainTextString(dataset);
      context.getRequest().setAttribute("columnConfiguration", columnConfiguration);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Check form values and extra operations
    String downloadValue = context.getParameter("doDownload");
    boolean doDownload = StringUtils.isNotBlank(downloadValue) && "true".equals(downloadValue);

    // Determine the current dataset
    long datasetId = context.getParameterAsLong("datasetId");
    Dataset dataset = DatasetRepository.findById(datasetId);
    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      return context;
    }
    dataset.setModifiedBy(context.getUserId());

    // Populate dataset fields
    dataset.setName(context.getParameter("name"));
    dataset.setSourceInfo(context.getParameter("sourceInfo"));
    dataset.setSourceUrl(context.getParameter("sourceUrl"));

    // Determine if a file has been replaced or a remote download is requested
    boolean checkForNewFile = (doDownload && StringUtils.isNotBlank(dataset.getSourceUrl())) ||
        (!doDownload && StringUtils.isBlank(dataset.getSourceUrl()));
    if (checkForNewFile) {
      // Get a handle on the previous file
      Dataset previousDataset = DatasetRepository.findById(dataset.getId());
      // Check for new file(s) and validate (@todo if a download, then use a background job)
      if (DatasetFileCommand.handleNewFile(context, dataset, dataset.getFileType())) {
        // Delete old file (not dataset!)
        DeleteDatasetCommand.deleteFile(previousDataset);
      } else {
        context.setWarningMessage("The file was not updated");
        return context;
      }
    }

    if (DatasetFileCommand.type(dataset.getFileType()) == DatasetFileCommand.JSON) {
      // Set the path to the records
      dataset.setRecordsPath(context.getParameter("recordsPath"));

      // Check if the column configuration text has changed
      String columnConfigurationText = context.getParameter("columnConfiguration");
      LOG.debug("columnConfigurationText:\n" + columnConfigurationText);

      if (StringUtils.isBlank(columnConfigurationText)) {
        // Use the record(s) to determine the columns
        columnConfigurationText = DatasetColumnJSONCommand.detectColumnsFromDataset(dataset);
      }

      // Update the dataset column values
      DatasetColumnJSONCommand.populateFromPlainText(dataset, columnConfigurationText);

      // Update the row count
      dataset.setRowCount(LoadJsonCommand.retrieveRowCount(dataset));
    }

    // Save the dataset record
    try {
      dataset = SaveDatasetCommand.updateDataset(dataset);
      if (dataset == null) {
        context.setErrorMessage("An error occurred, the dataset was not saved");
        return context;
      }
      // Recommend next page
      context.addSharedRequestValue("datasetId", String.valueOf(dataset.getId()));
      context.setRedirect("/admin/dataset-configuration?datasetId=" + dataset.getId());
    } catch (DataException de) {
      context.setErrorMessage("An error occurred: " + de.getMessage());
      return context;
    }

    if (checkForNewFile) {
      context.setSuccessMessage("The file was updated");
      context.setRedirect("/admin/dataset-preview?datasetId=" + dataset.getId());
    } else {
      context.setSuccessMessage("The settings were saved successfully");
    }
    return context;
  }
}
