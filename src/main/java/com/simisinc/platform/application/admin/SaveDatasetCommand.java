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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handles user posted web form values
 *
 * @author matt rajkowski
 * @created 4/25/18 9:05 AM
 */
public class SaveDatasetCommand {

  private static Log LOG = LogFactory.getLog(SaveDatasetCommand.class);

  public static Dataset saveDataset(Dataset datasetBean) throws DataException {

    // Check if this is an update
    if (datasetBean.getId() > -1) {
      return updateDataset(datasetBean);
    }

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
    if (datasetBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this record was not set");
    }

    LOG.debug("Saving a new record... ");
    Dataset dataset = new Dataset();
    dataset.setName(datasetBean.getName());
    dataset.setFilename(datasetBean.getFilename());
    dataset.setFileServerPath(datasetBean.getFileServerPath());
    dataset.setCreatedBy(datasetBean.getCreatedBy());
    dataset.setModifiedBy(datasetBean.getModifiedBy());
    dataset.setFileLength(datasetBean.getFileLength());
    dataset.setRowCount(datasetBean.getRowCount());
    dataset.setColumnCount(datasetBean.getColumnCount());
    dataset.setFileType(datasetBean.getFileType());
    dataset.setColumnNames(datasetBean.getColumnNames());
    dataset.setSourceUrl(datasetBean.getSourceUrl());
    dataset.setSourceInfo(datasetBean.getSourceInfo());
    dataset.setLastDownload(datasetBean.getLastDownload());

    // @todo See if a collection matches the filename and set it
    // @todo Try to auto-map fields to known column names

    return DatasetRepository.save(dataset);
  }


  public static Dataset updateDataset(Dataset datasetBean) throws DataException {
    if (datasetBean.getId() == -1) {
      LOG.error("The developer needs to set an existing record id");
      throw new DataException("A system id error occurred");
    }
    if (datasetBean.getModifiedBy() == -1) {
      throw new DataException("The user modifying this dataset record was not set");
    }
    LOG.debug("Saving an existing record... ");
    Dataset dataset = DatasetRepository.findById(datasetBean.getId());
    if (dataset == null) {
      throw new DataException("The existing record could not be found");
    }
    dataset.setModifiedBy(datasetBean.getCreatedBy());
    dataset.setCollectionUniqueId(datasetBean.getCollectionUniqueId());
    dataset.setCategoryId(datasetBean.getCategoryId());

    dataset.setName(datasetBean.getName());
    dataset.setFileType(datasetBean.getFileType());
    dataset.setFilename(datasetBean.getFilename());
    dataset.setFileLength(datasetBean.getFileLength());
    dataset.setFileServerPath(datasetBean.getFileServerPath());

    dataset.setRowCount(datasetBean.getRowCount());
    dataset.setColumnCount(datasetBean.getColumnCount());
    dataset.setColumnNames(datasetBean.getColumnNames());
    dataset.setFieldTitles(datasetBean.getFieldTitles());
    dataset.setFieldMappings(datasetBean.getFieldMappings());
    dataset.setFieldOptions(datasetBean.getFieldOptions());
    dataset.setSourceUrl(datasetBean.getSourceUrl());
    dataset.setSourceInfo(datasetBean.getSourceInfo());
    dataset.setLastDownload(datasetBean.getLastDownload());
    dataset.setRecordsPath(datasetBean.getRecordsPath());
    dataset.setScheduleType(datasetBean.getScheduleType());
    dataset.setScheduled(datasetBean.getScheduled());
    dataset.setSkipDuplicateNames(datasetBean.isSkipDuplicateNames());
    return DatasetRepository.save(dataset);
  }

}
