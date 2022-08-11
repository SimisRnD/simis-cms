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

package com.simisinc.platform.application.datasets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;

/**
 * Handles user posted web form values
 *
 * @author matt rajkowski
 * @created 4/25/18 9:05 AM
 */
public class SaveDatasetCommand {

  private static Log LOG = LogFactory.getLog(SaveDatasetCommand.class);

  public static Dataset saveDataset(Dataset datasetBean) throws DataException {

    // Validate the required fields
    if (StringUtils.isBlank(datasetBean.getName())) {
      throw new DataException("A name is required, please check the fields and try again");
    }
    if (StringUtils.isBlank(datasetBean.getFilename())) {
      throw new DataException("A file name is required, please check the fields and try again");
    }
    if (StringUtils.isBlank(datasetBean.getFileServerPath())) {
      LOG.error("The developer needs to set a path");
      throw new DataException("A system path error occurred");
    }

    // Check if this is an update
    Dataset dataset = null;
    if (datasetBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      if (datasetBean.getModifiedBy() == -1) {
        throw new DataException("The user modifying this dataset record was not set");
      }
      dataset = DatasetRepository.findById(datasetBean.getId());
      dataset.setModifiedBy(datasetBean.getModifiedBy());
    } else {
      LOG.debug("Saving a new record... ");
      if (datasetBean.getCreatedBy() == -1) {
        throw new DataException("The user creating this record was not set");
      }
      dataset = new Dataset();
      dataset.setCreatedBy(datasetBean.getCreatedBy());
      dataset.setModifiedBy(datasetBean.getModifiedBy());
      dataset.setName(datasetBean.getName());
    }

    dataset.setFilename(datasetBean.getFilename());
    dataset.setFileServerPath(datasetBean.getFileServerPath());
    dataset.setFileType(datasetBean.getFileType());
    dataset.setFileLength(datasetBean.getFileLength());
    dataset.setRowCount(datasetBean.getRowCount());
    dataset.setColumnCount(datasetBean.getColumnCount());
    dataset.setColumnNames(datasetBean.getColumnNames());
    // @todo Try to auto-map fields to known column names
    dataset.setFieldTitles(datasetBean.getFieldTitles());
    dataset.setFieldMappings(datasetBean.getFieldMappings());
    dataset.setFieldOptions(datasetBean.getFieldOptions());
    dataset.setSourceUrl(datasetBean.getSourceUrl());
    dataset.setSourceInfo(datasetBean.getSourceInfo());
    dataset.setLastDownload(datasetBean.getLastDownload());
    dataset.setRecordsPath(datasetBean.getRecordsPath());
    return DatasetRepository.save(dataset);
  }
}
