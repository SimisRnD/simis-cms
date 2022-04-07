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
import com.simisinc.platform.application.cms.TimeCommand;
import com.simisinc.platform.application.medicine.SaveMedicineLogCommand;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineLog;
import com.simisinc.platform.domain.model.medicine.MedicineReminder;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineReminderRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.presentation.rest.ServiceContext;
import com.simisinc.platform.presentation.rest.ServiceResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * An endpoint for recording medicine
 *
 * @author matt rajkowski
 * @created 9/17/18 1:33 PM
 */
public class MedicineLogService {

  private static Log LOG = LogFactory.getLog(MedicineLogService.class);

  private static String DRUG_LIST_UNIQUE_ID = "drug-list";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  // POST: med/medicineLog/?reminderId={reminderId}
  // POST: med/medicineLog/?medicineId={medicineId}
  public ServiceResponse post(ServiceContext context) {

    // Check required values
    long reminderId = context.getParameterAsLong("reminderId");
    long medicineId = context.getParameterAsLong("medicineId");

    if (reminderId == -1 && medicineId == -1) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "A reminder or medicine is required");
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

    // Determine the record
    MedicineReminder reminder = null;
    Medicine medicine = null;
    Date currentDate = new Date();
    if (reminderId > -1) {
      // Logging a specific reminder
      reminder = MedicineReminderRepository.findById(reminderId);
      if (reminder == null) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "The reminder could not be found");
        return response;
      }
      if (reminder.getReminder().after(DateUtils.addHours(currentDate, 3))) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "It's too soon for this scheduled reminder");
        return response;
      }
      medicine = MedicineRepository.findById(reminder.getMedicineId());
    } else if (medicineId > -1) {
      // Just logged medicine (as needed, etc.)
      medicine = MedicineRepository.findById(medicineId);
    }
    if (medicine == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "The medicine could not be found");
      return response;
    }

    // Determine the values
    String action = context.getParameter("action");
    String when = context.getParameter("when");
    String time = context.getParameter("time");
    String comments = context.getParameter("comments");
    int quantityGiven = context.getParameterAsInt("quantityGiven", 0);
    int pillsLeft = context.getParameterAsInt("pillsLeft");
    String reason = context.getParameter("reason");
    String reasonComments = context.getParameter("reasonComments");

    // Prepare the entity
    MedicineLog medicineLogBean = new MedicineLog();
    medicineLogBean.setAdministeredBy(context.getUserId());
    medicineLogBean.setIndividualId(medicine.getIndividualId());
    medicineLogBean.setMedicineId(medicine.getId());
    if (reminder != null) {
      medicineLogBean.setReminderId(reminder.getId());
      medicineLogBean.setReminderDate(reminder.getReminder());
    }
    medicineLogBean.setDrugId(medicine.getDrugId());
    medicineLogBean.setDrugName(medicine.getDrugName());
    medicineLogBean.setDosage(medicine.getDosage());
    medicineLogBean.setFormOfMedicine(medicine.getFormOfMedicine());
    medicineLogBean.setComments(comments);
    medicineLogBean.setPillsLeft(pillsLeft);

    if ("taken".equals(action)) {
      medicineLogBean.setWasTaken(true);
      medicineLogBean.setQuantityGiven(quantityGiven);
      if ("as-scheduled".equals(when)) {
        medicineLogBean.setTakenOnTime(true);
        medicineLogBean.setAdministered(reminder.getReminder());
      } else if ("now".equals(when)) {
        // Before, or within an hour, then taken on time
        if (currentDate.before(reminder.getReminder()) ||
            DateUtils.addHours(currentDate, -1).before(reminder.getReminder())) {
          medicineLogBean.setTakenOnTime(true);
        }
        medicineLogBean.setAdministered(new Timestamp(currentDate.getTime()));
      } else if ("specified".equals(when)) {
        // WebPlatformAPI Sending form data: {"reminderId":470,"action":"taken","when":"specified","time":"8:10 AM","tz":"America/New_York"}
        // Convert client to server time
        int[] adjustedHourMinute = TimeCommand.adjustHoursMinutesClientToServer(time, timezone);
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, adjustedHourMinute[0]);
        calendar.set(Calendar.MINUTE, adjustedHourMinute[1]);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date setDate = new Date(calendar.getTimeInMillis());
        if (setDate.before(reminder.getReminder()) ||
            DateUtils.addHours(setDate, -1).before(reminder.getReminder())) {
          medicineLogBean.setTakenOnTime(true);
        }
        medicineLogBean.setAdministered(new Timestamp(calendar.getTimeInMillis()));
      }
    } else if ("skipped".equals(action)) {
      medicineLogBean.setWasSkipped(true);
      if ("individual-unavailable".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_INDIVIDUAL_UNAVAILABLE);
      } else if ("caregiver-unavailable".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_CAREGIVER_UNAVAILABLE);
      } else if ("medicine-unavailable".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_MEDICINE_UNAVAILABLE);
      } else if ("refused".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_REFUSED);
      } else if ("health-concerns".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_HEALTH_CONCERNS);
      } else if ("ran-out".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_RAN_OUT);
      } else if ("dose-not-needed".equals(reason)) {
        medicineLogBean.setReasonCode(MedicineLog.REASON_DOSE_NOT_NEEDED);
      } else {
        medicineLogBean.setReasonCode(MedicineLog.REASON_OTHER);
      }
      medicineLogBean.setReasonComments(reasonComments);
    }

    // Verify a few things
    if (medicineLogBean.getIndividualId() == -1) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "An individual is required");
      return response;
    }
    if (medicineLogBean.getDrugId() == -1) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "A drug is required");
      return response;
    }

    try {
      // Verify and Save the medicine log event
      MedicineLog medicineLog = SaveMedicineLogCommand.saveMedicineLog(medicineLogBean);
      if (medicineLog == null) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "The medicine event could not be saved");
        return response;
      }
      // Prepare the response
      ServiceResponse response = new ServiceResponse(200);
      response.getMeta().put("type", "medicineLog");
      response.getMeta().put("id", medicineLog.getId());
      return response;
    } catch (DataException e) {
      LOG.error("saveError", e);
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
  }
}
