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
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.Prescription;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/18 12:00 PM
 */
public class SaveMedicineCommand {

  private static Log LOG = LogFactory.getLog(SaveMedicineCommand.class);

  private static String DRUG_LIST_UNIQUE_ID = "drug-list";
  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  /**
   * Save the medicine and all of the reminder dependencies
   *
   * @param medicineBean
   * @param medicineScheduleBean
   * @param prescriptionBean
   * @return
   * @throws DataException
   */
  public static Medicine saveMedicine(Medicine medicineBean, MedicineSchedule medicineScheduleBean, Prescription prescriptionBean, long userId) throws DataException {

    // Required dependencies
    if (medicineBean.getIndividualId() == -1) {
      throw new DataException("An individual is required");
    }
    if (medicineBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    } else if (medicineBean.getId() > -1 && medicineBean.getModifiedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }
    if (StringUtils.isNotEmpty(medicineBean.getBarcode())) {
      if (!medicineBean.getBarcode().startsWith("MED-")) {
        throw new DataException("A proper medicine label is required");
      }
    }

    // Check the collections for access
    Collection drugListCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(DRUG_LIST_UNIQUE_ID, userId);
    Collection caregiversCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(CAREGIVERS_UNIQUE_ID, userId);
    Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, userId);
    if (drugListCollection == null || caregiversCollection == null || individualsCollection == null) {
      throw new DataException("The collections could not be found");
    }

    // Load the drug from the drug-list
    if (medicineBean.getDrugId() > -1) {
      Item drug = LoadItemCommand.loadItemByIdWithinCollection(medicineBean.getDrugId(), drugListCollection);
      if (drug == null) {
        throw new DataException("The drug could not be found");
      }
      if (StringUtils.isBlank(medicineBean.getDrugName())) {
        medicineBean.setDrugName(drug.getName());
      }
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(medicineBean.getDrugName())) {
      errorMessages.append("A drug name is required");
    }
    if (StringUtils.isBlank(medicineBean.getDosage())) {
      errorMessages.append("A dosage is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Verify the Individual is in one of my Caregiver groups
    Item individual = LoadItemCommand.loadItemByIdWithinCollection(medicineBean.getIndividualId(), individualsCollection);
    boolean isAuthorizedForUser = IndividualRelationshipCommand.isAuthorizedForUser(individual, caregiversCollection, userId);
    if (!isAuthorizedForUser) {
      throw new DataException("The user is not authorized");
    }

    // Transform the fields and store
    boolean isUpdate = (medicineBean.getId() > -1);
    Medicine medicine;
    if (isUpdate) {
      // Check the
      LOG.debug("Saving an existing record... ");
      medicine = MedicineRepository.findById(medicineBean.getId());
      if (medicine == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      // These values can be set on insert, but not update
      LOG.debug("Saving a new record... ");
      medicine = new Medicine();
      medicine.setCreatedBy(medicineBean.getCreatedBy());
      medicine.setAssignedTo(medicineBean.getAssignedTo());
      medicine.setIndividualId(medicineBean.getIndividualId());
      medicine.setDrugId(medicineBean.getDrugId());
      medicine.setDrugName(medicineBean.getDrugName());
      medicine.setLastTaken(medicineBean.getLastTaken());
      medicine.setLastAdministeredBy(medicineBean.getLastAdministeredBy());
    }
    medicine.setModifiedBy(medicineBean.getModifiedBy());
    medicine.setDosage(medicineBean.getDosage());
    medicine.setFormOfMedicine(medicineBean.getFormOfMedicine());
    medicine.setAppearance(medicineBean.getAppearance());
    medicine.setQuantityOnHand(medicineBean.getQuantityOnHand());
    medicine.setCost(medicineBean.getCost());
    medicine.setBarcode(medicineBean.getBarcode());
    medicine.setCondition(medicineBean.getCondition());
    medicine.setComments(medicineBean.getComments());
    medicine.setSuspended(medicineBean.getSuspended());
    medicine.setSuspendedBy(medicineBean.getSuspendedBy());
    medicine.setArchived(medicineBean.getArchived());
    medicine.setArchivedBy(medicineBean.getArchivedBy());

    // Save the record
    Medicine medicineResult = MedicineRepository.save(medicine, medicineScheduleBean, prescriptionBean);
    if (medicineResult != null) {
      // Create some reminders
      SaveMedicineRemindersCommand.saveMedicineReminders(medicine);
    }
    return medicineResult;
  }
}
