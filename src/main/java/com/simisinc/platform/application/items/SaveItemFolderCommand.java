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
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.infrastructure.persistence.items.ItemFolderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class SaveItemFolderCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static Log LOG = LogFactory.getLog(SaveItemFolderCommand.class);

  public static ItemFolder saveFolder(ItemFolder folderBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (folderBean.getItemId() == -1) {
      errorMessages.append("An item is required");
    }

    if (StringUtils.isBlank(folderBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (folderBean.getCreatedBy() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The user saving this folder was not set");
    }

    if (folderBean.getId() == -1 && ItemFolderRepository.findByName(folderBean.getName(), folderBean.getItemId()) != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A unique name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    ItemFolder folder;
    if (folderBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      folder = ItemFolderRepository.findById(folderBean.getId());
      if (folder == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      folder = new ItemFolder();
    }
    // @note set the uniqueId before setting the name
    folder.setUniqueId(generateUniqueId(folder, folderBean));
    folder.setItemId(folderBean.getItemId());
    folder.setName(folderBean.getName());
    folder.setSummary(folderBean.getSummary());
    folder.setCreatedBy(folderBean.getCreatedBy());
    folder.setModifiedBy(folderBean.getModifiedBy());
    folder.setGuestPrivacyType(folderBean.getGuestPrivacyType());
    folder.setFolderGroupList(folderBean.getFolderGroupList());
    folder.setFolderCategoryList(folderBean.getFolderCategoryList());
    return ItemFolderRepository.save(folder);
  }

  private static String generateUniqueId(ItemFolder previousFolder, ItemFolder folder) {

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
    while (ItemFolderRepository.findByUniqueId(uniqueId, folder.getItemId()) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
