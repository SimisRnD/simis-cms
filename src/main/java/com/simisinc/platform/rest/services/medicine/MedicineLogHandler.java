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
import com.simisinc.platform.application.medicine.FormatMedicineLogTextCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineLog;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MedicineLogHandler {

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
  String comments;
  Long administeredTimestamp;
  String administeredDateText;
  String administeredTimeText;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String administeredText;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean wasTaken;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Boolean wasSkipped;

  public MedicineLogHandler(MedicineLog log, ZoneId timezone) {

    // Name
    // Medicine
    // 200 mg, take 1
    // Day
    // Hour/Minute
    // Taken at 9:09 AM
    Item individual = LoadItemCommand.loadItemById(log.getIndividualId());
    Medicine medicine = MedicineRepository.findById(log.getMedicineId());
    Item drug = LoadItemCommand.loadItemById(medicine.getDrugId());

    id = log.getId();
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
    if (log.getQuantityGiven() > 0) {
      dosageQuantity = log.getQuantityGiven();
    } else {
      dosageQuantity = null;
    }
    if (StringUtils.isNotBlank(log.getComments())) {
      comments = log.getComments();
    } else {
      comments = null;
    }

//    reminderTimestamp = reminder.getReminder().toInstant().atZone(timezone).toInstant().toEpochMilli();
    administeredTimestamp = log.getAdministered().getTime();
    administeredDateText = FormatMedicineLogTextCommand.formatDayText(log, timezone);
    administeredTimeText = FormatMedicineLogTextCommand.formatTimeText(log, timezone);

    if (log.getAdministered() != null) {
      administeredTimestamp = log.getAdministered().getTime();
      administeredText = FormatMedicineLogTextCommand.formatAdministeredText(log, timezone);
    } else {
      administeredTimestamp = null;
      administeredText = null;
    }

    if (log.getWasTaken()) {
      wasTaken = true;
    } else {
      wasTaken = null;
    }
    if (log.getWasSkipped()) {
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

  public String getComments() {
    return comments;
  }

  public Long getAdministeredTimestamp() {
    return administeredTimestamp;
  }

  public String getAdministeredText() {
    return administeredText;
  }

  public String getAdministeredDateText() {
    return administeredDateText;
  }

  public String getAdministeredTimeText() {
    return administeredTimeText;
  }

  public Boolean getWasTaken() {
    return wasTaken;
  }

  public Boolean getWasSkipped() {
    return wasSkipped;
  }
}
