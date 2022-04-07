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
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineReminderRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/18 12:00 PM
 */
public class SaveMedicineRemindersCommand {

  private static Log LOG = LogFactory.getLog(SaveMedicineRemindersCommand.class);

//  private static String DRUG_LIST_UNIQUE_ID = "drug-list";
//  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
//  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  public static void saveMedicineReminders(Medicine medicine) throws DataException {
    // Go forward several days...
    LocalDate now = LocalDate.now();
    for (int i = 0; i < 31; i++) {
      LocalDate startDate = now.plusDays(i);
      LocalDate endDate = startDate.plusDays(1);
      // Load all the reminders for the range... could be hundreds of people
      MedicineReminderRepository.createMedicineReminders(
          medicine.getId(),
          (i == 0 ? new Timestamp(System.currentTimeMillis()) : Timestamp.valueOf(startDate.atStartOfDay())),
          Timestamp.valueOf(endDate.atStartOfDay()),
          startDate.getDayOfWeek());
    }
  }
}
