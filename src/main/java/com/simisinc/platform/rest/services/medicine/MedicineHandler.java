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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simisinc.platform.application.cms.TimeCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.MedicineTime;
import com.simisinc.platform.domain.model.medicine.Prescription;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MedicineHandler {

  Long id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  String individualUniqueId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String drugUniqueId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String drugName;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String barcode;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String dosage;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String formOfMedicine;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String appearance;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer quantityOnHand;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean isSuspended;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean isArchived;
  // Schedule
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String frequency;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer daysToRepeat;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  List<Integer> selectedDayList;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer timesADay;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  List<String> timeList;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  List<Integer> quantityList;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String notes;
  // Pharmacy
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String pharmacyName;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String pharmacyLocation;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String pharmacyPhoneNumber;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String prescriptionNumber;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer prescriptionRefillsLeft;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer dosagesPerRefill;

  public MedicineHandler(Medicine record, MedicineSchedule medicineSchedule, Prescription prescription, ZoneId clientTimezone) {
    id = record.getId();
    if (record.getIndividualId() > -1) {
      Item item = LoadItemCommand.loadItemById(record.getIndividualId());
      individualUniqueId = item.getUniqueId();
    } else {
      individualUniqueId = null;
    }
    if (record.getDrugId() > -1) {
      Item item = LoadItemCommand.loadItemById(record.getDrugId());
      drugUniqueId = item.getUniqueId();
    } else {
      drugUniqueId = null;
    }
    drugName = record.getDrugName();
    barcode = record.getBarcode();
    dosage = record.getDosage();
    formOfMedicine = record.getFormOfMedicine();
    appearance = record.getAppearance();
    if (record.getQuantityOnHand() > 0) {
      quantityOnHand = record.getQuantityOnHand();
    }
    if (record.getSuspended() != null) {
      isSuspended = true;
    }
    if (record.getArchived() != null) {
      isArchived = true;
    }

    // Schedule Items
    if (medicineSchedule.getFrequency() == MedicineSchedule.AS_NEEDED) {
      frequency = "as-needed";
    } else if (medicineSchedule.getFrequency() == MedicineSchedule.EVERY_DAY) {
      frequency = "every-day";
    } else if (medicineSchedule.getFrequency() == MedicineSchedule.EVERY_N_DAYS) {
      frequency = "every-n-days";
    } else if (medicineSchedule.getFrequency() == MedicineSchedule.SPECIFIC_DAYS) {
      frequency = "specific-days";
    } else {
      frequency = null;
    }
    if (medicineSchedule.getDaysToRepeat() > 0) {
      daysToRepeat = medicineSchedule.getDaysToRepeat();
    } else {
      daysToRepeat = null;
    }
    if (medicineSchedule.getFrequency() == MedicineSchedule.SPECIFIC_DAYS) {
      selectedDayList = new ArrayList<>();
      if (medicineSchedule.isOnMonday()) {
        selectedDayList.add(0);
      }
      if (medicineSchedule.isOnTuesday()) {
        selectedDayList.add(1);
      }
      if (medicineSchedule.isOnWednesday()) {
        selectedDayList.add(2);
      }
      if (medicineSchedule.isOnThursday()) {
        selectedDayList.add(3);
      }
      if (medicineSchedule.isOnFriday()) {
        selectedDayList.add(4);
      }
      if (medicineSchedule.isOnSaturday()) {
        selectedDayList.add(5);
      }
      if (medicineSchedule.isOnSunday()) {
        selectedDayList.add(6);
      }
    } else {
      selectedDayList = null;
    }
    if (medicineSchedule.getTimesADay() > 0) {
      timesADay = medicineSchedule.getTimesADay();
    } else {
      timesADay = null;
    }
    notes = medicineSchedule.getNotes();
    // Medicine Times
    if (medicineSchedule.getMedicineTimeList() != null && !medicineSchedule.getMedicineTimeList().isEmpty()) {
      timeList = new ArrayList<>();
      quantityList = new ArrayList<>();
      for (MedicineTime medicineTime : medicineSchedule.getMedicineTimeList()) {
        // Perform server-to-client time change
        int[] adjustedHourMinute = TimeCommand.adjustHoursMinutesServerToClient(medicineTime.getHour(), medicineTime.getMinute(), clientTimezone);
        int hour = adjustedHourMinute[0];
        int minute = adjustedHourMinute[1];
        // Convert from 24-hour time
        String amPMValue = "AM";
        if (hour == 0) {
          hour = 12;
        } else if (hour > 12) {
          hour -= 12;
          amPMValue = "PM";
        }
        timeList.add(hour + ":" + (minute < 10 ? "0" : "") + minute + " " + amPMValue);
        quantityList.add(medicineTime.getQuantity());
      }
    } else {
      timeList = null;
      quantityList = null;
    }

    // Prescription
    pharmacyName = prescription.getPharmacyName();
    pharmacyLocation = prescription.getPharmacyLocation();
    pharmacyPhoneNumber = prescription.getPharmacyPhone();
    prescriptionNumber = prescription.getRxNumber();
    if (prescription.getRefillsLeft() > -1) {
      prescriptionRefillsLeft = prescription.getRefillsLeft();
    } else {
      prescriptionRefillsLeft = null;
    }
    if (prescription.getDosagesPerRefill() > 0) {
      dosagesPerRefill = prescription.getDosagesPerRefill();
    } else {
      dosagesPerRefill = null;
    }
  }

  public Long getId() {
    return id;
  }

  public String getIndividualUniqueId() {
    return individualUniqueId;
  }

  public String getDrugUniqueId() {
    return drugUniqueId;
  }

  public String getDrugName() {
    return drugName;
  }

  public String getBarcode() {
    return barcode;
  }

  public String getDosage() {
    return dosage;
  }

  public String getFormOfMedicine() {
    return formOfMedicine;
  }

  public String getAppearance() {
    return appearance;
  }

  public Integer getQuantityOnHand() {
    return quantityOnHand;
  }

  public Boolean getSuspended() {
    return isSuspended;
  }

  public Boolean getArchived() {
    return isArchived;
  }

  public String getFrequency() {
    return frequency;
  }

  public Integer getDaysToRepeat() {
    return daysToRepeat;
  }

  public List<Integer> getSelectedDayList() {
    return selectedDayList;
  }

  public Integer getTimesADay() {
    return timesADay;
  }

  public List<String> getTimeList() {
    return timeList;
  }

  public List<Integer> getQuantityList() {
    return quantityList;
  }

  public String getNotes() {
    return notes;
  }

  public String getPharmacyName() {
    return pharmacyName;
  }

  public String getPharmacyLocation() {
    return pharmacyLocation;
  }

  public String getPharmacyPhoneNumber() {
    return pharmacyPhoneNumber;
  }

  public String getPrescriptionNumber() {
    return prescriptionNumber;
  }

  public Integer getPrescriptionRefillsLeft() {
    return prescriptionRefillsLeft;
  }

  public Integer getDosagesPerRefill() {
    return dosagesPerRefill;
  }
}
