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

package com.simisinc.platform.domain.model.medicine;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/17/18 2:36 PM
 */
public class MedicineLog extends Entity {

  public static final int REASON_INDIVIDUAL_UNAVAILABLE = 100;
  public static final int REASON_CAREGIVER_UNAVAILABLE = 200;
  public static final int REASON_MEDICINE_UNAVAILABLE = 300;
  public static final int REASON_REFUSED = 400;
  public static final int REASON_HEALTH_CONCERNS = 500;
  public static final int REASON_RAN_OUT = 600;
  public static final int REASON_DOSE_NOT_NEEDED = 700;
  public static final int REASON_OTHER = 800;

  private Long id = -1L;

  private long medicineId = -1;
  private long individualId = -1;
  private long reminderId = -1;
  private Timestamp reminderDate = null;
  private long drugId = -1;
  private String drugName = null;
  private String dosage = null;
  private String formOfMedicine = null;
  private int quantityGiven = -1;
  private String comments = null;
  private long administeredBy = -1;
  private Timestamp administered = null;
  private int pillsLeft = -1;
  private boolean wasTaken = false;
  private boolean takenOnTime = true;
  private boolean wasSkipped = false;
  private int reasonCode = -1;
  private String reasonComments = null;
  private Timestamp created = null;

  public MedicineLog() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getMedicineId() {
    return medicineId;
  }

  public void setMedicineId(long medicineId) {
    this.medicineId = medicineId;
  }

  public long getIndividualId() {
    return individualId;
  }

  public void setIndividualId(long individualId) {
    this.individualId = individualId;
  }

  public long getReminderId() {
    return reminderId;
  }

  public void setReminderId(long reminderId) {
    this.reminderId = reminderId;
  }

  public Timestamp getReminderDate() {
    return reminderDate;
  }

  public void setReminderDate(Timestamp reminderDate) {
    this.reminderDate = reminderDate;
  }

  public long getDrugId() {
    return drugId;
  }

  public void setDrugId(long drugId) {
    this.drugId = drugId;
  }

  public String getDrugName() {
    return drugName;
  }

  public void setDrugName(String drugName) {
    this.drugName = drugName;
  }

  public String getDosage() {
    return dosage;
  }

  public void setDosage(String dosage) {
    this.dosage = dosage;
  }

  public String getFormOfMedicine() {
    return formOfMedicine;
  }

  public void setFormOfMedicine(String formOfMedicine) {
    this.formOfMedicine = formOfMedicine;
  }

  public int getQuantityGiven() {
    return quantityGiven;
  }

  public void setQuantityGiven(int quantityGiven) {
    this.quantityGiven = quantityGiven;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public long getAdministeredBy() {
    return administeredBy;
  }

  public void setAdministeredBy(long administeredBy) {
    this.administeredBy = administeredBy;
  }

  public Timestamp getAdministered() {
    return administered;
  }

  public void setAdministered(Timestamp administered) {
    this.administered = administered;
  }

  public int getPillsLeft() {
    return pillsLeft;
  }

  public void setPillsLeft(int pillsLeft) {
    this.pillsLeft = pillsLeft;
  }

  public boolean getWasTaken() {
    return wasTaken;
  }

  public void setWasTaken(boolean wasTaken) {
    this.wasTaken = wasTaken;
  }

  public boolean getWasSkipped() {
    return wasSkipped;
  }

  public void setWasSkipped(boolean wasSkipped) {
    this.wasSkipped = wasSkipped;
  }

  public int getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(int reasonCode) {
    this.reasonCode = reasonCode;
  }

  public String getReasonComments() {
    return reasonComments;
  }

  public void setReasonComments(String reasonComments) {
    this.reasonComments = reasonComments;
  }

  public boolean getTakenOnTime() {
    return takenOnTime;
  }

  public void setTakenOnTime(boolean takenOnTime) {
    this.takenOnTime = takenOnTime;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
