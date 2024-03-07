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

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;

/**
 * Deletes dataset files from the database and filesystem
 *
 * @author matt rajkowski
 * @created 4/25/18 12:33 PM
 */
public class DeleteDatasetCommand {

  private static Log LOG = LogFactory.getLog(DeleteDatasetCommand.class);

  public static boolean delete(Dataset datasetBean) {
    // Remove the record
    boolean deleted = DatasetRepository.remove(datasetBean);
    if (deleted) {
      // Remove its file
      deleteFile(datasetBean);
      return true;
    }
    return false;
  }

  public static boolean deleteFile(Dataset datasetBean) {
    // Get a file handle
    File serverFile = FileSystemCommand.getFileServerRootPath(datasetBean.getFileServerPath());
    if (serverFile.isFile()) {
      LOG.debug("Deleting file: " + serverFile.getPath());
      serverFile.delete();
    }
    return true;
  }

}
