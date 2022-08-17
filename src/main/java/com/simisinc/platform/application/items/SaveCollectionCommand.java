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
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves a collection object
 *
 * @author matt rajkowski
 * @created 4/19/18 1:37 PM
 */
public class SaveCollectionCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveCollectionCommand.class);

  public static Collection saveCollection(Collection collectionBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(collectionBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (collectionBean.getCreatedBy() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The user saving this collection was not set");
    }

    if (collectionBean.getId() == -1 && CollectionRepository.findByName(collectionBean.getName()) != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A unique name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Collection collection;
    if (collectionBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      collection = CollectionRepository.findById(collectionBean.getId());
      if (collection == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      collection = new Collection();
    }
    // @note set the uniqueId before setting the name
    collection.setUniqueId(generateUniqueId(collection, collectionBean));
    collection.setName(collectionBean.getName());
    collection.setDescription(collectionBean.getDescription());
    collection.setCreatedBy(collectionBean.getCreatedBy());
    collection.setGuestPrivacyType(collectionBean.getGuestPrivacyType());
    collection.setCollectionGroupList(collectionBean.getCollectionGroupList());
    collection.setListingsLink(collectionBean.getListingsLink());
    collection.setIcon(collectionBean.getIcon());
    collection.setShowListingsLink(collectionBean.getShowListingsLink());
    collection.setShowSearch(collectionBean.getShowSearch());
    collection.setItemUrlText(collectionBean.getItemUrlText());
    return CollectionRepository.save(collection);
  }

  private static String generateUniqueId(Collection previousItem, Collection item) {

    // Use an existing uniqueId
    if (previousItem.getUniqueId() != null) {
      // See if the name changed
      if (previousItem.getName().equals(item.getName())) {
        return previousItem.getUniqueId();
      }
    }

    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = item.getName().toLowerCase();
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
    while (CollectionRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
