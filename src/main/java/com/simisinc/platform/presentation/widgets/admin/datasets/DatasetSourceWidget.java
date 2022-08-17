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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.datasets.DatasetDownloadRemoteFileCommand;
import com.simisinc.platform.application.datasets.DatasetUploadFileCommand;
import com.simisinc.platform.application.datasets.SaveDatasetCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to configure dataset remote url and file attachments
 *
 * @author matt rajkowski
 * @created 7/31/2022 7:23 PM
 */
public class DatasetSourceWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(DatasetSourceWidget.class);

  private static String JSP = "/admin/dataset-source.jsp";

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
      long datasetId = context.getParameterAsLong("datasetId");
      dataset = DatasetRepository.findById(datasetId);
    }

    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      context.setRedirect("/admin/datasets");
      return context;
    }
    context.getRequest().setAttribute("dataset", dataset);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Determine the current dataset
    long datasetId = context.getParameterAsLong("datasetId");
    Dataset dataset = DatasetRepository.findById(datasetId);
    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      return context;
    }

    // Recommend a return URL
    context.setRedirect("/admin/dataset-source?datasetId=" + dataset.getId());

    // Populate required field for updates
    dataset.setModifiedBy(context.getUserId());

    // Determine the action
    String command = context.getParameter("command");
    if ("save".equals(command)) {
      String doDownload = context.getParameter("doDownload");
      if (doDownload != null) {
        return downloadRemoteFileAction(context, dataset);
      } else {
        return updateSettings(context, dataset);
      }
    } else if ("uploadFile".equals(command)) {
      return uploadFileAction(context, dataset);
    }
    // Default to nothing
    return null;
  }

  private WidgetContext updateSettings(WidgetContext context, Dataset dataset) {
    // Check form values
    dataset.setSourceUrl(context.getParameter("sourceUrl"));
    dataset.setFileType(context.getParameter("fileType"));

    // Verify there is a data source url
    if (StringUtils.isBlank(dataset.getSourceUrl()) || !UrlCommand.isUrlValid(dataset.getSourceUrl())) {
      context.setErrorMessage("Valid dataset source URL is required");
      context.setRequestObject(dataset);
      return context;
    }

    // Update the file type
    if (StringUtils.isBlank(dataset.getFileType())) {
      context.setErrorMessage("Dataset file type is required");
      context.setRequestObject(dataset);
      return context;
    }

    try {
      Dataset savedDataset = SaveDatasetCommand.saveDataset(dataset);
      if (savedDataset == null) {
        context.setWarningMessage("The settings could not be saved");
        return context;
      }
    } catch (Exception e) {
      context.setWarningMessage("The settings could not be saved");
      return context;
    }

    context.setSuccessMessage("The settings were updated successfully");
    return context;
  }

  private WidgetContext downloadRemoteFileAction(WidgetContext context, Dataset dataset) {
    // Check form values
    dataset.setSourceUrl(context.getParameter("sourceUrl"));
    dataset.setFileType(context.getParameter("fileType"));

    // Verify there is a data source url
    if (StringUtils.isBlank(dataset.getSourceUrl()) || !UrlCommand.isUrlValid(dataset.getSourceUrl())) {
      context.setErrorMessage("Valid dataset source URL is required");
      context.setRequestObject(dataset);
      return context;
    }

    // Update the file type
    if (StringUtils.isBlank(dataset.getFileType())) {
      context.setErrorMessage("Dataset file type is required");
      context.setRequestObject(dataset);
      return context;
    }

    try {
      // Perform the remote download
      if (!DatasetDownloadRemoteFileCommand.handleRemoteFileDownload(dataset, context.getUserId())) {
        context.setWarningMessage("The downloaded file has the same content as the previous file");
        context.setRequestObject(dataset);
        return context;
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(dataset);
      return context;
    }

    context.setSuccessMessage("The remote file was downloaded successfully");
    return context;
  }

  private WidgetContext uploadFileAction(WidgetContext context, Dataset dataset) {
    // Check for an uploaded file and validate
    if (!DatasetUploadFileCommand.handleUpload(context, dataset)) {
      context.setWarningMessage("The file was not updated");
      return context;
    }

    context.setSuccessMessage("The file was uploaded successfully");
    return context;
  }

}
