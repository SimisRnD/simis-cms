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
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/27/18 3:47 PM
 */
public class SaveItemRelationshipCommand {

  private static Log LOG = LogFactory.getLog(SaveItemRelationshipCommand.class);

  public static ItemRelationship saveRelationship(ItemRelationship relationshipBean) throws DataException {

    // Validate the required fields
    if (relationshipBean.getItemId() == -1) {
      throw new DataException("A parent item is required");
    }
    if (relationshipBean.getCollectionId() == -1) {
      throw new DataException("A parent collection is required");
    }
    if (relationshipBean.getRelatedCollectionId() == -1) {
      throw new DataException("A related collection is required");
    }
    if (relationshipBean.getRelatedItemId() == -1) {
      throw new DataException("A related item is required");
    }
    if (relationshipBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this relationship was not set");
    }

    // Transform the fields and store...
    ItemRelationship relationship;
    if (relationshipBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      relationship = ItemRelationshipRepository.findById(relationshipBean.getId());
      if (relationship == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      relationship = new ItemRelationship();
    }
    relationship.setItemId(relationshipBean.getItemId());
    relationship.setCollectionId(relationshipBean.getCollectionId());
    relationship.setRelatedCollectionId(relationshipBean.getRelatedCollectionId());
    relationship.setRelatedItemId(relationshipBean.getRelatedItemId());
    relationship.setCreatedBy(relationshipBean.getCreatedBy());
    relationship.setModifiedBy(relationshipBean.getModifiedBy());

    return ItemRelationshipRepository.save(relationship);
  }

}
