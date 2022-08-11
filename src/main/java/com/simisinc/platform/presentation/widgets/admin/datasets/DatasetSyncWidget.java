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
import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.datasets.DeleteDatasetItemsCommand;
import com.simisinc.platform.application.datasets.ProcessDatasetCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.datasets.DatasetScheduleFrequencyOptions;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Widget to configure dataset download schedules and sync options
 *
 * @author matt rajkowski
 * @created 7/31/2022 7:23 PM
 */
public class DatasetSyncWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(DatasetSyncWidget.class);

  private static String JSP = "/admin/dataset-sync.jsp";

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
      if (dataset == null) {
        context.setErrorMessage("Dataset was not found");
        context.setRedirect("/admin/datasets");
        return context;
      }
      context.getRequest().setAttribute("dataset", dataset);
    }

    // Collection
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(dataset.getCollectionUniqueId());
    if (collection != null) {
      context.getRequest().setAttribute("collection", collection);
      if (collection != null) {
        List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
        context.getRequest().setAttribute("categoryList", categoryList);
      }
    }

    // New Collection

    // Specific category

    // Show the number of sync'd records
    DataConstraints constraints = new DataConstraints(1, 1, "item_id");
    ItemSpecification specification = new ItemSpecification();
    specification.setDatasetId(dataset.getId());
    ItemRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("syncCount", String.valueOf(constraints.getTotalRecordCount()));

    // Show the Schedule Options
    context.getRequest().setAttribute("scheduleOptionsMap", DatasetScheduleFrequencyOptions.map);

    // Column mapping comparisons
    ArrayList<String> fieldMappingsList = new ArrayList<>();
    if (dataset.getFieldMappings() != null) {
      Collections.addAll(fieldMappingsList, dataset.getFieldMappings());
    }
    while (fieldMappingsList.size() < dataset.getColumnCount()) {
      LOG.debug("...added a blank field mapping");
      fieldMappingsList.add("");
    }
    context.getRequest().setAttribute("fieldMappingsList", fieldMappingsList);

    // Column options
    ArrayList<String> fieldOptionsList = new ArrayList<>();
    if (dataset.getFieldOptions() != null) {
      Collections.addAll(fieldOptionsList, dataset.getFieldOptions());
    }
    while (fieldOptionsList.size() < dataset.getColumnCount()) {
      LOG.debug("...added a blank option");
      fieldOptionsList.add("");
    }
    context.getRequest().setAttribute("fieldOptionsList", fieldOptionsList);

    // Retrieve the dataset's first record
    List<String[]> sampleRows = null;
    try {
      sampleRows = DatasetFileCommand.loadRows(dataset, 1, false);
    } catch (Exception e) {
      context.setErrorMessage("File could not be read... " + e.getMessage());
    }

    // Validate the data
    if (sampleRows != null && sampleRows.size() == 1) {
      // Equalize the data to column count for display
      List<String> sampleRow = new ArrayList<>(Arrays.asList(sampleRows.get(0)));
      while (sampleRow.size() < dataset.getColumnCount()) {
        sampleRow.add("");
      }
      context.getRequest().setAttribute("sampleRow", sampleRow);
    } else {
      context.setWarningMessage("File content was not found");
    }

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
    context.setRedirect("/admin/dataset-sync?datasetId=" + dataset.getId());

    // Process the data
    String removeAll = context.getParameter("removeAll");
    if (removeAll != null) {
      return removeAll(context, dataset);
    } else {
      return saveForm(context, dataset);
    }

  }

  private static WidgetContext removeAll(WidgetContext context, Dataset dataset) {
    try {
      long deleteCount = DeleteDatasetItemsCommand.deleteItemsForDataset(dataset, null);
      if (deleteCount == 0) {
        context.setSuccessMessage("There were no items to be removed");
      } else if (deleteCount == 1) {
        context.setSuccessMessage("Successfully removed 1 item");
      } else {
        context.setSuccessMessage("Successfully removed " + deleteCount + " items");
      }
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(dataset);
      return context;
    }
    return context;
  }

  private static WidgetContext saveForm(WidgetContext context, Dataset dataset)
      throws InvocationTargetException, IllegalAccessException {
    // Handle the form post
    Dataset datasetBean = new Dataset();
    BeanUtils.populate(datasetBean, context.getParameterMap());

    // Set the allowed values
    dataset.setScheduleEnabled(datasetBean.getScheduleEnabled());
    dataset.setScheduleFrequency(datasetBean.getScheduleFrequency());
    dataset.setSyncEnabled(datasetBean.getSyncEnabled());
    dataset.setSyncMergeType(datasetBean.getSyncMergeType());
    dataset.setModifiedBy(context.getUserId());

    // Save the dataset record sync values
    dataset = DatasetRepository.updateScheduleAndSyncDetails(dataset);
    if (dataset == null) {
      context.setErrorMessage("An error occurred, the dataset was not saved");
      context.setRequestObject(dataset);
      return context;
    }

    // Process the data
    String doProcess = context.getParameter("process");
    if (doProcess != null) {
      try {
        ProcessDatasetCommand.startProcess(dataset);
      } catch (DataException de) {
        context.setErrorMessage("An error occurred: " + de.getMessage());
        context.setRequestObject(dataset);
        return context;
      }
      context.setSuccessMessage("Processing started...");
      return context;
    }

    context.setSuccessMessage("The settings were updated successfully");
    return context;
  }

}
