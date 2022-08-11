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

import com.simisinc.platform.application.admin.LoadTextFileCommand;
import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Widget to preview dataset content
 *
 * @author matt rajkowski
 * @created 5/19/18 3:00 PM
 */
public class DatasetPreviewWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/dataset-preview.jsp";
  private static Log LOG = LogFactory.getLog(DatasetPreviewWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    boolean displayText = "text".equals(context.getPreferences().get("view"));
    int rowsToReturn = Integer.parseInt(context.getPreferences().getOrDefault("rows", "200"));

    // Determine the dataset to display
    long datasetId = context.getParameterAsLong("datasetId");
    Dataset dataset = DatasetRepository.findById(datasetId);
    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      return context;
    }
    context.getRequest().setAttribute("dataset", dataset);

    // Determine the records based on the type of dataset
    try {
      List<String[]> sampleRows = null;
      if (displayText) {
        // Load bytes
        LOG.debug("Showing bytes...");
        sampleRows = LoadTextFileCommand.loadSomeBytes(dataset);
        dataset.setFieldTitles(null);
      } else {
        // Load as data
        sampleRows = DatasetFileCommand.loadRows(dataset, rowsToReturn, true);
        if (sampleRows == null || sampleRows.isEmpty()) {
          // Show the first few lines of the text file
          sampleRows = LoadTextFileCommand.loadSomeBytes(dataset);
          dataset.setFieldTitles(null);
        }
      }
      if (sampleRows == null || sampleRows.isEmpty()) {
        context.setWarningMessage("Dataset is empty");
        return context;
      }
      context.getRequest().setAttribute("sampleRows", sampleRows);
    } catch (Exception e) {
      context.setErrorMessage("File type '" + dataset.getFileType() + "' could not be parsed... " + e.getMessage());
      LOG.warn("File type '" + dataset.getFileType() + "' could not be parsed... " + e.getMessage());
      return context;
    }

    // If a reveal is being used, tell the framework this is embedded content
    if ("reveal".equals(context.getRequest().getParameter("view"))) {
      context.setEmbedded(true);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }
}
