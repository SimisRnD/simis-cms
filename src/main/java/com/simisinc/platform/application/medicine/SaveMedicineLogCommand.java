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

package com.simisinc.platform.application.medicine;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.MedicineLog;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineLogRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/17/18 2:44 PM
 */
public class SaveMedicineLogCommand {

  private static Log LOG = LogFactory.getLog(SaveMedicineLogCommand.class);

  private static String DRUG_LIST_UNIQUE_ID = "drug-list";
  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  /**
   * Save the medicine log event
   *
   * @param medicineLogBean
   * @return
   * @throws DataException
   */
  public static MedicineLog saveMedicineLog(MedicineLog medicineLogBean) throws DataException {

    // Required dependencies
    if (medicineLogBean.getIndividualId() == -1) {
      throw new DataException("An individual is required");
    }
    if (medicineLogBean.getAdministeredBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }

    // Check the collections for access
    Collection drugListCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(DRUG_LIST_UNIQUE_ID, medicineLogBean.getAdministeredBy());
    Collection caregiversCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(CAREGIVERS_UNIQUE_ID, medicineLogBean.getAdministeredBy());
    Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, medicineLogBean.getAdministeredBy());
    if (drugListCollection == null || caregiversCollection == null || individualsCollection == null) {
      throw new DataException("The collections could not be found");
    }

    // Load the drug from the drug-list
    if (medicineLogBean.getDrugId() > -1) {
      Item drug = LoadItemCommand.loadItemByIdWithinCollection(medicineLogBean.getDrugId(), drugListCollection);
      if (drug == null) {
        throw new DataException("The drug could not be found");
      }
      if (StringUtils.isBlank(medicineLogBean.getDrugName())) {
        medicineLogBean.setDrugName(drug.getName());
      }
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(medicineLogBean.getDrugName())) {
      errorMessages.append("A drug name is required");
    }
    if (StringUtils.isBlank(medicineLogBean.getDosage())) {
      errorMessages.append("A dosage is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Verify the Individual is in one of my Caregiver groups
    Item individual = LoadItemCommand.loadItemByIdWithinCollection(medicineLogBean.getIndividualId(), individualsCollection);
    boolean isAuthorizedForUser = IndividualRelationshipCommand.isAuthorizedForUser(individual, caregiversCollection, medicineLogBean.getAdministeredBy());
    if (!isAuthorizedForUser) {
      throw new DataException("The user is not authorized");
    }

    // Transform the fields and store
    MedicineLog medicineLog = new MedicineLog();
    medicineLog.setMedicineId(medicineLogBean.getMedicineId());
    medicineLog.setIndividualId(medicineLogBean.getIndividualId());
    medicineLog.setReminderId(medicineLogBean.getReminderId());
    medicineLog.setReminderDate(medicineLogBean.getReminderDate());
    medicineLog.setDrugId(medicineLogBean.getDrugId());
    medicineLog.setDrugName(medicineLogBean.getDrugName());
    medicineLog.setDosage(medicineLogBean.getDosage());
    medicineLog.setFormOfMedicine(medicineLogBean.getFormOfMedicine());
    medicineLog.setQuantityGiven(medicineLogBean.getQuantityGiven());
    medicineLog.setComments(medicineLogBean.getComments());
    medicineLog.setAdministeredBy(medicineLogBean.getAdministeredBy());
    medicineLog.setAdministered(medicineLogBean.getAdministered());
    medicineLog.setPillsLeft(medicineLogBean.getPillsLeft());
    medicineLog.setWasTaken(medicineLogBean.getWasTaken());
    medicineLog.setTakenOnTime(medicineLogBean.getTakenOnTime());
    medicineLog.setWasSkipped(medicineLogBean.getWasSkipped());
    medicineLog.setReasonCode(medicineLogBean.getReasonCode());
    medicineLog.setReasonComments(medicineLogBean.getReasonComments());

    // Save the record
    return MedicineLogRepository.save(medicineLog);
  }
}
