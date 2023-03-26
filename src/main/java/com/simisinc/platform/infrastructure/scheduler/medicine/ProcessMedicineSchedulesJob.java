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

package com.simisinc.platform.infrastructure.scheduler.medicine;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineReminderRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineSpecification;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * This job uses the medicine schedules to extend and create a list of daily reminders
 *
 * @author matt rajkowski
 * @created 10/1/18 11:13 AM
 */
public class ProcessMedicineSchedulesJob {

  private static Log LOG = LogFactory.getLog(ProcessMedicineSchedulesJob.class);

  @Job(name = "Update medicine reminders based on schedules, approx 20 days out")
  public static void execute() {

    // Distributed lock
    String lock = LockManager.lock(SchedulerManager.PROCESS_MEDICINE_SCHEDULES_JOB, Duration.ofHours(1));
    if (lock == null) {
      return;
    }

    // Check all available medicines to see if any need to be extended
    MedicineSpecification medicineSpecification = new MedicineSpecification();
    medicineSpecification.setMinMedicineId(1);
    medicineSpecification.setArchivedOnly(false);

    // Batch a few at a time
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(10);
    constraints.setColumnToSortBy("medicine_id");

    // Check the available medicine list...
    List<Medicine> medicineList = null;
    while (!(medicineList = MedicineRepository.findAll(medicineSpecification, constraints)).isEmpty()) {
      long maxMedicineId = medicineSpecification.getMinMedicineId();
      for (Medicine medicine : medicineList) {
        maxMedicineId = medicine.getId();
        // Update the reminders 20 days out
        LocalDate now = LocalDate.now();
        for (int i = 20; i < 31; i++) {
          LocalDate startDate = now.plusDays(i);
          LocalDate endDate = startDate.plusDays(1);
          // Extend the medicine schedule...
          MedicineReminderRepository.createMedicineReminders(
              medicine.getId(),
              Timestamp.valueOf(startDate.atStartOfDay()),
              Timestamp.valueOf(endDate.atStartOfDay()),
              startDate.getDayOfWeek());
        }
      }
      // Start at the next number
      medicineSpecification.setMinMedicineId(maxMedicineId + 1);
    }
  }
}
