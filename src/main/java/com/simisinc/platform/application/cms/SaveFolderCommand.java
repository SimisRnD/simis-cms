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
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves folder objects
 *
 * @author matt rajkowski
 * @created 12/12/18 3:44 PM
 */
public class SaveFolderCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static Log LOG = LogFactory.getLog(SaveFolderCommand.class);

  public static Folder saveFolder(Folder folderBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(folderBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (folderBean.getCreatedBy() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The user saving this folder was not set");
    }

    if (folderBean.getId() == -1 && FolderRepository.findByName(folderBean.getName()) != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A unique name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Folder folder;
    if (folderBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      folder = FolderRepository.findById(folderBean.getId());
      if (folder == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      folder = new Folder();
    }
    // @note set the uniqueId before setting the name
    folder.setUniqueId(generateUniqueId(folder, folderBean));
    folder.setName(folderBean.getName());
    folder.setSummary(folderBean.getSummary());
    folder.setCreatedBy(folderBean.getCreatedBy());
    folder.setModifiedBy(folderBean.getModifiedBy());
    folder.setGuestPrivacyType(folderBean.getGuestPrivacyType());
    folder.setFolderGroupList(folderBean.getFolderGroupList());
    folder.setFolderCategoryList(folderBean.getFolderCategoryList());
    return FolderRepository.save(folder);
  }

  private static String generateUniqueId(Folder previousFolder, Folder folder) {

    // Use an existing uniqueId
    if (previousFolder.getUniqueId() != null) {
      // See if the name changed
      if (previousFolder.getName().equals(folder.getName())) {
        return previousFolder.getUniqueId();
      }
    }

    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = folder.getName().toLowerCase();
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
      } else if (c == ' ') {
        sb.append("-");
      }
    }

    // Find the next available unique instance
    int count = 1;
    String originalUniqueId = sb.toString();
    String uniqueId = sb.toString();
    while (FolderRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
