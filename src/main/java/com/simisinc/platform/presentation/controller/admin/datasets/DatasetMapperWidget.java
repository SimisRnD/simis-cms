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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.DatasetFileCommand;
import com.simisinc.platform.application.admin.ProcessDatasetCommand;
import com.simisinc.platform.application.admin.SaveDatasetCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:05 PM
 */
public class DatasetMapperWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/dataset-schema.jsp";
  private static Log LOG = LogFactory.getLog(DatasetMapperWidget.class);

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

    // Collection drop-down
    List<Collection> collectionList = CollectionRepository.findAll();
    context.getRequest().setAttribute("collectionList", collectionList);
    if (StringUtils.isNotBlank(dataset.getCollectionUniqueId())) {
      Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(dataset.getCollectionUniqueId());
      if (collection != null) {
        List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
        context.getRequest().setAttribute("categoryList", categoryList);
      }
    }

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

  public WidgetContext post(WidgetContext context) {

    // Check form values
    String processValue = context.getParameter("doProcess");
    boolean doProcess = StringUtils.isNotBlank(processValue) && "true".equals(processValue);

    // Determine the current dataset
    long datasetId = context.getParameterAsLong("datasetId");
    Dataset dataset = DatasetRepository.findById(datasetId);
    if (dataset == null) {
      context.setErrorMessage("Dataset was not found");
      return context;
    }
    dataset.setModifiedBy(context.getUserId());

    // Determine if a collection will be used
    String collectionUniqueId = context.getParameter("collectionUniqueId");
    if (StringUtils.isBlank(collectionUniqueId)) {
      dataset.setCollectionUniqueId(null);
    } else {
      // Set collection and category info
      dataset.setCollectionUniqueId(collectionUniqueId);
      Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
      long categoryId = context.getParameterAsLong("categoryId");
      if (categoryId > 0) {
        Category category = CategoryRepository.findById(categoryId);
        if (category != null && category.getCollectionId() == collection.getId()) {
          dataset.setCategoryId(categoryId);
        }
      }
    }

    // Import options
    dataset.setSkipDuplicateNames("true".equals(context.getParameter("skipDuplicateNames")));

    // Look for the column field array
    ArrayList<String> fieldMappings = new ArrayList<>();
    for (int i = 0; i < dataset.getColumnCount(); i++) {
      String mapValue = context.getParameter("columnMapping" + i);
      if (StringUtils.isNotBlank(mapValue)) {
        fieldMappings.add(mapValue);
      } else {
        fieldMappings.add("");
      }
    }
    dataset.setFieldMappings(fieldMappings.toArray(new String[0]));

    // Look for the column options array
    ArrayList<String> fieldOptions = new ArrayList<>();
    for (int i = 0; i < dataset.getColumnCount(); i++) {
      String optionValue = context.getParameter("columnOptions" + i);
      if (StringUtils.isNotBlank(optionValue)) {
        if (optionValue.contains("|")) {
          context.setErrorMessage("Option contained an invalid character");
          return context;
        }
        fieldOptions.add(optionValue);
      } else {
        fieldOptions.add("");
      }
    }
    dataset.setFieldOptions(fieldOptions.toArray(new String[0]));

    // Save the dataset record
    try {
      dataset = SaveDatasetCommand.updateDataset(dataset);
      if (dataset == null) {
        context.setErrorMessage("An error occurred, the dataset was not saved");
        return context;
      }
      // Recommend next page
      context.setRedirect("/admin/dataset-mapper?datasetId=" + dataset.getId());
    } catch (DataException de) {
      context.setErrorMessage("An error occurred: " + de.getMessage());
      return context;
    }

    // Process the data
    if (doProcess) {
      try {
        ProcessDatasetCommand.startProcess(dataset);
      } catch (DataException de) {
        context.setErrorMessage("An error occurred: " + de.getMessage());
        context.setRedirect("/admin/dataset-mapper?datasetId=" + datasetId);
        return context;
      }
      context.setSuccessMessage("Processing started...");
      context.setRedirect("/admin/datasets");
      return context;
    }

    context.setSuccessMessage("The settings were saved successfully");
    return context;
  }
}
