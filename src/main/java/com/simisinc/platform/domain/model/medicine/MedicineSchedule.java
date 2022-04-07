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
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/23/18 9:31 AM
 */
public class MedicineSchedule extends Entity {

  public static int UNASSIGNED = -1;
  public static int AS_NEEDED = 1;
  public static int EVERY_DAY = 2;
  public static int EVERY_N_DAYS = 3;
  public static int SPECIFIC_DAYS = 4;

  private Long id = -1L;
  private long medicineId = -1;
  private int frequency = UNASSIGNED;
  private int daysToRepeat = 0;
  private boolean onMonday = false;
  private boolean onTuesday = false;
  private boolean onWednesday = false;
  private boolean onThursday = false;
  private boolean onFriday = false;
  private boolean onSaturday = false;
  private boolean onSunday = false;
  private String notes = null;
  private int timesADay = 0;
  private Timestamp startDate = null;
  private Timestamp endDate = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;

  private List<MedicineTime> medicineTimeList = null;

  public MedicineSchedule() {
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

  public int getFrequency() {
    return frequency;
  }

  public void setFrequency(int frequency) {
    this.frequency = frequency;
  }

  public int getDaysToRepeat() {
    return daysToRepeat;
  }

  public void setDaysToRepeat(int daysToRepeat) {
    this.daysToRepeat = daysToRepeat;
  }

  public boolean isOnMonday() {
    return onMonday;
  }

  public void setOnMonday(boolean onMonday) {
    this.onMonday = onMonday;
  }

  public boolean isOnTuesday() {
    return onTuesday;
  }

  public void setOnTuesday(boolean onTuesday) {
    this.onTuesday = onTuesday;
  }

  public boolean isOnWednesday() {
    return onWednesday;
  }

  public void setOnWednesday(boolean onWednesday) {
    this.onWednesday = onWednesday;
  }

  public boolean isOnThursday() {
    return onThursday;
  }

  public void setOnThursday(boolean onThursday) {
    this.onThursday = onThursday;
  }

  public boolean isOnFriday() {
    return onFriday;
  }

  public void setOnFriday(boolean onFriday) {
    this.onFriday = onFriday;
  }

  public boolean isOnSaturday() {
    return onSaturday;
  }

  public void setOnSaturday(boolean onSaturday) {
    this.onSaturday = onSaturday;
  }

  public boolean isOnSunday() {
    return onSunday;
  }

  public void setOnSunday(boolean onSunday) {
    this.onSunday = onSunday;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public int getTimesADay() {
    return timesADay;
  }

  public void setTimesADay(int timesADay) {
    this.timesADay = timesADay;
  }

  public Timestamp getStartDate() {
    return startDate;
  }

  public void setStartDate(Timestamp startDate) {
    this.startDate = startDate;
  }

  public Timestamp getEndDate() {
    return endDate;
  }

  public void setEndDate(Timestamp endDate) {
    this.endDate = endDate;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public List<MedicineTime> getMedicineTimeList() {
    return medicineTimeList;
  }

  public void setMedicineTimeList(List<MedicineTime> medicineTimeList) {
    this.medicineTimeList = medicineTimeList;
  }
}
