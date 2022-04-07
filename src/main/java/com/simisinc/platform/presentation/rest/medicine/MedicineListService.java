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
import com.simisinc.platform.application.medicine.IndividualRelationshipCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.Prescription;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.rest.ServiceContext;
import com.simisinc.platform.presentation.rest.ServiceResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Returns a list of medicines being tracked for an individual
 *
 * @author matt rajkowski
 * @created 8/28/18 10:12 AM
 */
public class MedicineListService {

  private static Log LOG = LogFactory.getLog(MedicineListService.class);

  private static String ADMIN_USER_GROUP = "Program Administrator";
  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  // GET: med/medicines/{individualUniqueId}
  public ServiceResponse get(ServiceContext context) {

    // Determine the individual (must exist and be part of the individuals collection)
    String individualUniqueId = context.getPathParam();
    Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, context.getUserId());
    Item individual = LoadItemCommand.loadItemByUniqueId(individualUniqueId);
    if (individualsCollection == null || individual == null || individual.getCollectionId() != individualsCollection.getId()) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Individual was not found");
      return response;
    }

    // Check the caregivers collection for access (must exist and user must have access to at least one item in the collection)
    Collection caregiversCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(CAREGIVERS_UNIQUE_ID, context.getUserId());
    if (caregiversCollection == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Collection was not found");
      return response;
    }

    // Verify the Individual is in one of my Caregiver groups
    boolean isAuthorizedForUser = IndividualRelationshipCommand.isAuthorizedForUser(individual, caregiversCollection, context.getUserId());
    if (!isAuthorizedForUser) {
      LOG.warn("Individual: " + individualUniqueId + " not authorized for user: " + context.getUserId());
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Individual was not found");
      return response;
    }

    // Check the client's timezone
    String tz = context.getParameter("tz");
    if (StringUtils.isBlank(tz)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Timezone is required");
      return response;
    }
    ZoneId timezone = ZoneId.of(tz);

    // Determine the query specifications
    MedicineSpecification specification = new MedicineSpecification();
    specification.setIndividualId(individual.getId());

    // See if the user has the "Program Administrator" role
    Group group = GroupRepository.findByName(ADMIN_USER_GROUP);
    if (group == null || !context.getUser().hasGroup(group.getId())) {
      // This user can only see available records
      specification.setArchivedOnly(DataConstants.FALSE);
    }

    // Configure the constraints
    DataConstraints constraints = new DataConstraints();
    constraints.setColumnsToSortBy(new String[]{"archived desc", "suspended desc", "medicine_id"});

    List<Medicine> medicineList = MedicineRepository.findAll(specification, constraints);

    // Set the fields to return
    List<MedicineHandler> recordList = new ArrayList<>();
    for (Medicine medicine : medicineList) {
      // Load additional information
      MedicineSchedule medicineSchedule = MedicineScheduleRepository.findByMedicineId(medicine.getId());
      if (medicineSchedule == null) {
        medicineSchedule = new MedicineSchedule();
      } else {
        medicineSchedule.setMedicineTimeList(MedicineTimeRepository.findAllByMedicineId(medicine.getId()));
      }
      Prescription prescription = PrescriptionRepository.findByMedicineId(medicine.getId());
      if (prescription == null) {
        prescription = new Prescription();
      }
      // Just the fields for the API
      recordList.add(new MedicineHandler(medicine, medicineSchedule, prescription, timezone));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    response.getMeta().put("type", "medicine");
    response.getMeta().put("totalRecordCount", medicineList.size());
    response.setData(recordList);
    return response;
  }

}
