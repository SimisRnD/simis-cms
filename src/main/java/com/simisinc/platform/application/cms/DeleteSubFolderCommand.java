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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.List;

/**
 * Deletes sub-folders
 *
 * @author matt rajkowski
 * @created 8/27/19 4:46 PM
 */
public class DeleteSubFolderCommand {

  private static Log LOG = LogFactory.getLog(DeleteSubFolderCommand.class);

  public static boolean deleteSubFolder(SubFolder subFolderBean) throws DataException {

    // Verify the object
    if (subFolderBean == null || subFolderBean.getId() == -1) {
      throw new DataException("The sub-folder was not specified");
    }

    // Determine the files to delete
    FileVersionSpecification specification = new FileVersionSpecification();
    specification.setSubFolderId(subFolderBean.getId());
    List<FileVersion> fileVersionList = FileVersionRepository.findAll(specification, null);

    // Remove the folder
    if (SubFolderRepository.remove(subFolderBean)) {
      // Delete all the files/versions
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      for (FileVersion fileVersion : fileVersionList) {
        String fileServerPath = fileVersion.getFileServerPath();
        if (StringUtils.isBlank(fileServerPath)) {
          return false;
        }
        File file = new File(serverRootPath + fileServerPath);
        if (file.exists() && file.isFile()) {
          file.delete();
        }
      }
      return true;
    }
    return false;
  }

}
