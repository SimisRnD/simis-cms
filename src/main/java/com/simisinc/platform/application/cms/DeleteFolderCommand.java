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

package com.simisinc.platform.application.cms;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;

/**
 * Deletes folders
 *
 * @author matt rajkowski
 * @created 12/12/18 4:38 PM
 */
public class DeleteFolderCommand {

  private static Log LOG = LogFactory.getLog(DeleteFolderCommand.class);

  public static boolean deleteFolder(Folder folderBean) throws DataException {

    // Verify the object
    if (folderBean == null || folderBean.getId() == -1) {
      throw new DataException("The folder was not specified");
    }

    // Determine the files to delete
    FileVersionSpecification specification = new FileVersionSpecification();
    specification.setFolderId(folderBean.getId());
    List<FileVersion> fileVersionList = FileVersionRepository.findAll(specification, null);

    // Remove the folder
    if (FolderRepository.remove(folderBean)) {
      // Delete all the files/versions
      for (FileVersion fileVersion : fileVersionList) {
        String fileServerPath = fileVersion.getFileServerPath();
        if (StringUtils.isBlank(fileServerPath)) {
          return false;
        }
        File file = FileSystemCommand.getFileServerRootPath(fileServerPath);
        if (file.exists() && file.isFile()) {
          file.delete();
        }
      }
      return true;
    }
    return false;
  }

}
