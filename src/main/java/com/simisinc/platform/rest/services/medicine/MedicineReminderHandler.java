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
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.medicine.FormatMedicineReminderTextCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineReminder;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.MedicineTime;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineScheduleRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineTimeRepository;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MedicineReminderHandler {

  Long id;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String individualName;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String drugName;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String barcode;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String dosage;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer dosageQuantity;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String notes;
  Long reminderTimestamp;
  String reminderDateText;
  String reminderTimeText;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Long loggedTimestamp;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String loggedText;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean wasTaken;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean wasSkipped;

  public MedicineReminderHandler(MedicineReminder reminder, ZoneId timezone) {

    // Name
    // Medicine
    // 200 mg, take 1
    // Day
    // Hour/Minute
    // Taken at 9:09 AM
    Item individual = LoadItemCommand.loadItemById(reminder.getIndividualId());
    Medicine medicine = MedicineRepository.findById(reminder.getMedicineId());
    Item drug = LoadItemCommand.loadItemById(medicine.getDrugId());
    MedicineSchedule medicineSchedule = MedicineScheduleRepository.findById(reminder.getScheduleId());
    MedicineTime medicineTime = MedicineTimeRepository.findById(reminder.getTimeId());

    id = reminder.getId();
    individualName = individual.getName();
    drugName = drug.getName();
    barcode = medicine.getBarcode();
    if (StringUtils.isEmpty(medicine.getDosage()) && StringUtils.isEmpty(medicine.getFormOfMedicine())) {
      dosage = null;
    } else {
      StringBuilder sb = new StringBuilder();

      if (StringUtils.isNotEmpty(medicine.getDosage())) {
        sb.append(medicine.getDosage());
      }
      if (StringUtils.isNotEmpty(medicine.getFormOfMedicine())) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(medicine.getFormOfMedicine());
      }
      dosage = sb.toString();
    }
    if (medicineTime.getQuantity() > 0) {
      dosageQuantity = medicineTime.getQuantity();
    } else {
      dosageQuantity = null;
    }
    if (StringUtils.isNotBlank(medicineSchedule.getNotes())) {
      notes = medicineSchedule.getNotes();
    } else {
      notes = null;
    }

//    reminderTimestamp = reminder.getReminder().toInstant().atZone(timezone).toInstant().toEpochMilli();
    reminderTimestamp = reminder.getReminder().getTime();
    reminderDateText = FormatMedicineReminderTextCommand.formatDayText(reminder, timezone);
    reminderTimeText = FormatMedicineReminderTextCommand.formatTimeText(reminder, timezone);

    if (reminder.getLogged() != null) {
      loggedTimestamp = reminder.getLogged().getTime();
      loggedText = FormatMedicineReminderTextCommand.formatLoggedText(reminder, timezone);
    } else {
      loggedTimestamp = null;
      loggedText = null;
    }

    if (reminder.getWasTaken()) {
      wasTaken = true;
    } else {
      wasTaken = null;
    }
    if (reminder.getWasSkipped()) {
      wasSkipped = true;
    } else {
      wasSkipped = null;
    }
  }

  public Long getId() {
    return id;
  }

  public String getIndividualName() {
    return individualName;
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

  public Integer getDosageQuantity() {
    return dosageQuantity;
  }

  public String getNotes() {
    return notes;
  }

  public Long getReminderTimestamp() {
    return reminderTimestamp;
  }

  public String getReminderDateText() {
    return reminderDateText;
  }

  public String getReminderTimeText() {
    return reminderTimeText;
  }

  public Long getLoggedTimestamp() {
    return loggedTimestamp;
  }

  public String getLoggedText() {
    return loggedText;
  }

  public Boolean getWasTaken() {
    return wasTaken;
  }

  public Boolean getWasSkipped() {
    return wasSkipped;
  }
}
