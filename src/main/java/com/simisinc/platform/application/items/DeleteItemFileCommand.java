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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.domain.model.items.ItemFileVersion;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileVersionSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class DeleteItemFileCommand {

  private static Log LOG = LogFactory.getLog(DeleteItemFileCommand.class);

  public static boolean deleteFile(ItemFileItem fileBean) throws DataException {

    // Verify the object
    if (fileBean == null || fileBean.getId() == -1) {
      throw new DataException("The file was not specified");
    }

    // Determine the files to delete
    ItemFileVersionSpecification specification = new ItemFileVersionSpecification();
    specification.setFileId(fileBean.getId());
    List<ItemFileVersion> fileVersionList = ItemFileVersionRepository.findAll(specification, null);

    LOG.debug("Version count: " + fileVersionList.size());

    // Remove the file
    if (ItemFileItemRepository.remove(fileBean)) {
      // Delete all the files/versions
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      for (ItemFileVersion fileVersion : fileVersionList) {
        String fileServerPath = fileVersion.getFileServerPath();
        if (StringUtils.isBlank(fileServerPath)) {
          continue;
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
