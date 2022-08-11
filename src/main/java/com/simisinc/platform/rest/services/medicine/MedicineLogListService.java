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

package com.simisinc.platform.rest.services.medicine;

import static com.simisinc.platform.application.medicine.MedicineConstants.COLLECTION_CAREGIVERS_UNIQUE_ID;
import static com.simisinc.platform.application.medicine.MedicineConstants.COLLECTION_INDIVIDUALS_UNIQUE_ID;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.domain.model.medicine.MedicineLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineLogRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineLogSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a list of medicine action history
 *
 * @author matt rajkowski
 * @created 11/5/18 8:36 AM
 */
public class MedicineLogListService {

  private static Log LOG = LogFactory.getLog(MedicineLogListService.class);

  // endpoint: med/medicineLogList/{uniqueId}
  public ServiceResponse get(ServiceContext context) {

    // Check the client's timezone
    String tz = context.getParameter("tz");
    if (StringUtils.isBlank(tz)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Timezone is required");
      return response;
    }
    ZoneId timezone = ZoneId.of(tz);

    // Check the caregivers collection for access
    Collection caregiversCollection = LoadCollectionCommand
        .loadCollectionByUniqueIdForAuthorizedUser(COLLECTION_CAREGIVERS_UNIQUE_ID, context.getUserId());
    Collection individualsCollection = LoadCollectionCommand
        .loadCollectionByUniqueIdForAuthorizedUser(COLLECTION_INDIVIDUALS_UNIQUE_ID, context.getUserId());
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

    // Determine if a single individual is requested
    long individualIdToMatch = -1;
    String individualUniqueId = context.getPathParam();
    if (individualUniqueId != null) {
      Item individual = LoadItemCommand.loadItemByUniqueId(individualUniqueId);
      if (individual == null || individual.getCollectionId() != individualsCollection.getId()) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "Individual was not found");
        return response;
      }
      individualIdToMatch = individual.getId();
    }

    // Get a list of the specified individual or any related individuals for those groups
    List<Long> individualsList = new ArrayList<>();
    if (caregiverList != null) {
      for (Item caregiver : caregiverList) {
        List<ItemRelationship> itemRelationshipList = ItemRelationshipRepository
            .findRelatedItemsForItemIdInCollection(caregiver, individualsCollection);
        if (itemRelationshipList != null) {
          LOG.debug("itemRelationshipList size: " + itemRelationshipList.size());
          for (ItemRelationship relationship : itemRelationshipList) {
            // Skip if it's not a match
            if (individualIdToMatch > -1 && relationship.getRelatedItemId() != individualIdToMatch) {
              continue;
            }
            // Store unique values
            if (!individualsList.contains(relationship.getRelatedItemId())) {
              individualsList.add(relationship.getRelatedItemId());
            }
          }
        }
      }
    }
    LOG.debug("individualsList size: " + individualsList.size());

    if (individualsList.isEmpty()) {
      // Prepare an empty response
      ServiceResponse response = new ServiceResponse(200);
      ServiceResponseCommand.addMeta(response, "medicineLog", individualsList, null);
      response.setData(new ArrayList<>());
      return response;
    }

    // Find the medicine action history for the found individuals
    LocalDate now = LocalDate.now();
    LocalDate startDate = now.minusDays(7);

    MedicineLogSpecification medicineLogSpecification = new MedicineLogSpecification();
    medicineLogSpecification.setIndividualsList(individualsList);
    medicineLogSpecification.setMinDate(Timestamp.valueOf(startDate.atStartOfDay()));
    medicineLogSpecification.setMaxDate(Timestamp.valueOf(LocalDateTime.now()));

    DataConstraints constraints = new DataConstraints();
    constraints.setColumnToSortBy("administered", "desc");

    List<MedicineLog> medicineHistoryList = MedicineLogRepository.findAll(medicineLogSpecification, constraints);

    // Set the fields to return
    List<MedicineLogHandler> recordList = new ArrayList<>();
    for (MedicineLog medicineLog : medicineHistoryList) {
      recordList.add(new MedicineLogHandler(medicineLog, timezone));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "medicineLog", recordList, constraints);
    response.setData(recordList);
    return response;
  }

}
