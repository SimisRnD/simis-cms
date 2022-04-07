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
 * @created 9/11/18 2:12 PM
 */
public class MedicineReminder extends Entity {

  private Long id = -1L;

  private long individualId = -1;
  private long medicineId = -1;
  private long scheduleId = -1;
  private long timeId = -1;
  private Timestamp reminder = null;
  private Timestamp processed = null;
  private Timestamp logged = null;
  private boolean wasTaken = false;
  private boolean wasSkipped = false;

  public MedicineReminder() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getIndividualId() {
    return individualId;
  }

  public void setIndividualId(long individualId) {
    this.individualId = individualId;
  }

  public long getMedicineId() {
    return medicineId;
  }

  public void setMedicineId(long medicineId) {
    this.medicineId = medicineId;
  }

  public long getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(long scheduleId) {
    this.scheduleId = scheduleId;
  }

  public long getTimeId() {
    return timeId;
  }

  public void setTimeId(long timeId) {
    this.timeId = timeId;
  }

  public Timestamp getReminder() {
    return reminder;
  }

  public void setReminder(Timestamp reminder) {
    this.reminder = reminder;
  }

  public Timestamp getProcessed() {
    return processed;
  }

  public void setProcessed(Timestamp processed) {
    this.processed = processed;
  }

  public Timestamp getLogged() {
    return logged;
  }

  public void setLogged(Timestamp logged) {
    this.logged = logged;
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
}
