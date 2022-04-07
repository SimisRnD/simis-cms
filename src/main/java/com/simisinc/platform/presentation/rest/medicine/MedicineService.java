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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.medicine.DeleteMedicineCommand;
import com.simisinc.platform.application.medicine.SaveMedicineCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.MedicineTime;
import com.simisinc.platform.domain.model.medicine.Prescription;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.presentation.rest.ServiceContext;
import com.simisinc.platform.presentation.rest.ServiceResponse;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * An endpoint for saving medicine for an individual
 *
 * @author matt rajkowski
 * @created 8/28/18 10:12 AM
 */
public class MedicineService {

  private static Log LOG = LogFactory.getLog(MedicineService.class);

  private static String DRUG_LIST_UNIQUE_ID = "drug-list";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  // PUT: med/medicine[/medicineId]
  public ServiceResponse put(ServiceContext context) {
    return post(context);
  }

  // POST: med/medicine
  public ServiceResponse post(ServiceContext context) {
    String medicineId = context.getPathParam();
    if (StringUtils.isNumeric(medicineId)) {
      // This is an update (@todo more to PUT once app is updated)
      Medicine medicineBean = MedicineRepository.findById(Long.parseLong(medicineId));
      if (medicineBean == null) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "Medicine could not be found");
        return response;
      }

      return save(context, medicineBean);
    } else {
      // This is an insert
      Medicine medicineBean = new Medicine();
      // Handle the individualUniqueId
      String individualUniqueId = context.getParameter("individualUniqueId");
      if (!StringUtils.isBlank(individualUniqueId)) {
        Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, context.getUserId());
        Item item = LoadItemCommand.loadItemByUniqueIdWithinCollection(individualUniqueId, individualsCollection);
        if (item != null) {
          medicineBean.setIndividualId(item.getId());
        }
      }
      // Handle the drugUniqueId
      String drugUniqueId = context.getParameter("drugUniqueId");
      if (!StringUtils.isBlank(drugUniqueId)) {
        Collection drugListCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(DRUG_LIST_UNIQUE_ID, context.getUserId());
        Item item = LoadItemCommand.loadItemByUniqueIdWithinCollection(drugUniqueId, drugListCollection);
        if (item != null) {
          medicineBean.setDrugId(item.getId());
        }
      }
      return save(context, medicineBean);
    }
  }

  private ServiceResponse save(ServiceContext context, Medicine medicineBean) {

    // Check the client's timezone
    String tz = context.getParameter("tz");
    if (StringUtils.isBlank(tz)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Timezone is required");
      return response;
    }
    ZoneId timezone = ZoneId.of(tz);

    try {
      // Populate the record
      BeanUtils.populate(medicineBean, context.getParameterMap());
    } catch (Exception e) {
      LOG.error("populateError", e);
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
    medicineBean.setCreatedBy(context.getUserId());
    medicineBean.setModifiedBy(context.getUserId());

    // Verify a few things
    if (medicineBean.getIndividualId() == -1) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "An individual is required");
      return response;
    }
    if (medicineBean.getDrugId() == -1) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "A drug is required");
      return response;
    }
    if (StringUtils.isNotEmpty(medicineBean.getBarcode())) {
      if (!medicineBean.getBarcode().startsWith("MED-")) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "A proper medicine label is required");
        return response;
      }
    }

    // Medicine Schedule
    MedicineSchedule medicineScheduleBean = new MedicineSchedule();
    medicineScheduleBean.setCreatedBy(context.getUserId());
    medicineScheduleBean.setModifiedBy(context.getUserId());
    medicineScheduleBean.setStartDate(new Timestamp(System.currentTimeMillis()));
    String frequency = context.getParameter("frequency");
    if ("as-needed".equals(frequency)) {
      medicineScheduleBean.setFrequency(MedicineSchedule.AS_NEEDED);
    } else if ("every-day".equals(frequency)) {
      medicineScheduleBean.setFrequency(MedicineSchedule.EVERY_DAY);
    } else if ("every-n-days".equals(frequency)) {
      medicineScheduleBean.setFrequency(MedicineSchedule.EVERY_N_DAYS);
      int daysToRepeat = context.getParameterAsInt("daysToRepeat");
      medicineScheduleBean.setDaysToRepeat(daysToRepeat);
    } else if ("specific-days".equals(frequency)) {
      medicineScheduleBean.setFrequency(MedicineSchedule.SPECIFIC_DAYS);
      for (int day = 0; day < 7; day++) {
        String selectedDay = context.getParameter("selectedDay[" + day + "]");
        LOG.debug("selectedDay: " + selectedDay);
        if ("0".equals(selectedDay)) {
          medicineScheduleBean.setOnMonday(true);
        } else if ("1".equals(selectedDay)) {
          medicineScheduleBean.setOnTuesday(true);
        } else if ("2".equals(selectedDay)) {
          medicineScheduleBean.setOnWednesday(true);
        } else if ("3".equals(selectedDay)) {
          medicineScheduleBean.setOnThursday(true);
        } else if ("4".equals(selectedDay)) {
          medicineScheduleBean.setOnFriday(true);
        } else if ("5".equals(selectedDay)) {
          medicineScheduleBean.setOnSaturday(true);
        } else if ("6".equals(selectedDay)) {
          medicineScheduleBean.setOnSunday(true);
        }
      }
    }
    medicineScheduleBean.setNotes(context.getParameter("notes"));

    // Reminder Times and Quantity
    String time;
    int timeCount = 0;
    List<MedicineTime> medicineTimeList = new ArrayList<>();
    while ((time = context.getParameter("time[" + timeCount + "]")) != null) {
      // Determine the quantity
      int quantity = context.getParameterAsInt("quantity[" + timeCount + "]");
      if (quantity == -1) {
        LOG.warn("No quantity");
        break;
      }
      MedicineTime medicineTime = new MedicineTime(time, quantity, timezone);
      LOG.debug("Time Qty: " + time + " / " + quantity);
      medicineTimeList.add(medicineTime);
      ++timeCount;
    }
    medicineScheduleBean.setMedicineTimeList(medicineTimeList);
    medicineScheduleBean.setTimesADay(medicineTimeList.size());

    // Prescription Details
    Prescription prescriptionBean = new Prescription();
    prescriptionBean.setCreatedBy(context.getUserId());
    prescriptionBean.setModifiedBy(context.getUserId());
    prescriptionBean.setPharmacyName(context.getParameter("pharmacyName"));
    prescriptionBean.setPharmacyLocation(context.getParameter("pharmacyLocation"));
    prescriptionBean.setPharmacyPhone(context.getParameter("pharmacyPhoneNumber"));
    prescriptionBean.setRxNumber(context.getParameter("prescriptionNumber"));
    prescriptionBean.setRefillsLeft(context.getParameterAsInt("prescriptionRefillsLeft"));
    prescriptionBean.setDosagesPerRefill(context.getParameterAsInt("dosagesPerRefill"));

    try {
      // Save the medicine
      Medicine medicine = SaveMedicineCommand.saveMedicine(medicineBean, medicineScheduleBean, prescriptionBean, context.getUserId());
      if (medicine == null) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "The medicine could not be saved");
        return response;
      }
      // Prepare the response
      ServiceResponse response = new ServiceResponse(200);
      response.getMeta().put("type", "medicine");
      response.getMeta().put("id", medicine.getId());
      return response;
    } catch (DataException e) {
      LOG.error("saveError", e);
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
  }

  // DELETE: med/medicine/{id}
  public ServiceResponse delete(ServiceContext context) {

    String medicineId = context.getPathParam();
    if (!StringUtils.isNumeric(medicineId)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Medicine Id was not found in path param");
      return response;
    }
    Medicine medicine = MedicineRepository.findById(Long.parseLong(medicineId));
    if (medicine == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Medicine could not be found");
      return response;
    }

    try {
      // Delete the medicine
      boolean result = DeleteMedicineCommand.deleteMedicine(medicine, context.getUserId());
      if (!result) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "The medicine could not be deleted");
        return response;
      }
      // Prepare the response
      ServiceResponse response = new ServiceResponse(200);
      response.getMeta().put("type", "medicine");
      response.getMeta().put("id", medicine.getId());
      return response;
    } catch (DataException e) {
      LOG.error("deleteError", e);
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
  }
}
