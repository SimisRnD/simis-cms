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

package com.simisinc.platform.presentation.rest.medicine;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.rest.ServiceContext;
import com.simisinc.platform.presentation.rest.ServiceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Returns a list of individuals being cared for
 *
 * @author matt rajkowski
 * @created 8/27/18 4:54 PM
 */
public class IndividualListService {

  private static Log LOG = LogFactory.getLog(IndividualListService.class);

  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  // endpoint: med/individuals
  public ServiceResponse get(ServiceContext context) {

    // Check the caregivers collection for access
    Collection caregiversCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(CAREGIVERS_UNIQUE_ID, context.getUserId());
    Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, context.getUserId());
    if (caregiversCollection == null || individualsCollection == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Collection was not found");
      return response;
    }

    // Get a list of my Caregiver Groups
    ItemSpecification caregiverSpecification = new ItemSpecification();
    caregiverSpecification.setCollectionId(caregiversCollection.getId());
    caregiverSpecification.setForMemberWithUserId(context.getUserId());
    List<Item> caregiverList = ItemRepository.findAll(caregiverSpecification, null);

    if (caregiverList != null) {
      LOG.debug("caregiverList size: " + caregiverList.size());
    }

    // Get a list of any related individuals for those groups
    HashMap<Long, Item> individualsList = new HashMap<>();
    if (caregiverList != null) {
      for (Item caregiver : caregiverList) {
        List<ItemRelationship> itemRelationshipList = ItemRelationshipRepository.findRelatedItemsForItemIdInCollection(caregiver, individualsCollection);
        if (itemRelationshipList != null) {
          LOG.debug("itemRelationshipList size: " + itemRelationshipList.size());
          for (ItemRelationship relationship : itemRelationshipList) {
            if (!individualsList.containsKey(relationship.getRelatedItemId())) {
              Item thisIndividual = LoadItemCommand.loadItemById(relationship.getRelatedItemId());
              individualsList.put(relationship.getRelatedItemId(), thisIndividual);
            }
          }
        }
      }
    }

    LOG.debug("individualsList size: " + individualsList.size());

    // Set the fields to return
    List<IndividualHandler> recordList = new ArrayList<>();
    for (Item item : individualsList.values()) {
      recordList.add(new IndividualHandler(item));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    response.getMeta().put("type", "individual");
    response.getMeta().put("totalRecordCount", individualsList.size());
    response.setData(recordList);
    return response;
  }

}
