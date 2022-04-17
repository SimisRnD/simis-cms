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
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves sub-folder objects
 *
 * @author matt rajkowski
 * @created 8/27/19 5:22 PM
 */
public class SaveSubFolderCommand {

  private static Log LOG = LogFactory.getLog(SaveSubFolderCommand.class);

  public static SubFolder saveSubFolder(SubFolder subFolderBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(subFolderBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (subFolderBean.getFolderId() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A folder must be set");
    }

    if (subFolderBean.getCreatedBy() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The user saving this sub-folder was not set");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    SubFolder subFolder;
    if (subFolderBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      subFolder = SubFolderRepository.findById(subFolderBean.getId());
      if (subFolder == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      subFolder = new SubFolder();
    }
    // @note set the uniqueId before setting the name
    subFolder.setFolderId(subFolderBean.getFolderId());
    subFolder.setName(subFolderBean.getName());
    subFolder.setSummary(subFolderBean.getSummary());
    subFolder.setCreatedBy(subFolderBean.getCreatedBy());
    subFolder.setModifiedBy(subFolderBean.getModifiedBy());
    subFolder.setStartDate(subFolderBean.getStartDate());
    return SubFolderRepository.save(subFolder);
  }
}
