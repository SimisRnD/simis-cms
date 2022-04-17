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

package com.simisinc.platform.infrastructure.persistence.medicine;

import com.simisinc.platform.presentation.controller.DataConstants;

import java.sql.Timestamp;
import java.util.List;

/**
 * Properties for querying objects from the medicine reminder repository
 *
 * @author matt rajkowski
 * @created 9/11/18 3:31 PM
 */
public class MedicineReminderSpecification {

  private long id = -1L;
  private long individualId = -1L;
  private long medicineId = -1L;
  private Timestamp minDate = null;
  private Timestamp maxDate = null;
  private int reminderIsAfterNow = DataConstants.UNDEFINED;
  private int isWithinEndDate = DataConstants.UNDEFINED;
  private int isSuspended = DataConstants.UNDEFINED;
  private int isArchived = DataConstants.UNDEFINED;
  private List<Long> individualsList = null;

  public MedicineReminderSpecification() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
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

  public Timestamp getMinDate() {
    return minDate;
  }

  public void setMinDate(Timestamp minDate) {
    this.minDate = minDate;
  }

  public Timestamp getMaxDate() {
    return maxDate;
  }

  public void setMaxDate(Timestamp maxDate) {
    this.maxDate = maxDate;
  }

  public int getReminderIsAfterNow() {
    return reminderIsAfterNow;
  }

  public void setReminderIsAfterNow(boolean startDateIsBeforeNow) {
    this.reminderIsAfterNow = (startDateIsBeforeNow ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public void setReminderIsAfterNow(int reminderIsAfterNow) {
    this.reminderIsAfterNow = reminderIsAfterNow;
  }

  public int getIsWithinEndDate() {
    return isWithinEndDate;
  }

  public void setIsWithinEndDate(int isWithinEndDate) {
    this.isWithinEndDate = isWithinEndDate;
  }

  public void setIsWithinEndDate(boolean isWithinEndDate) {
    this.isWithinEndDate = (isWithinEndDate ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getIsSuspended() {
    return isSuspended;
  }

  public void setIsSuspended(int isSuspended) {
    this.isSuspended = isSuspended;
  }

  public void setIsSuspended(boolean isSuspended) {
    this.isSuspended = (isSuspended ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getIsArchived() {
    return isArchived;
  }

  public void setIsArchived(boolean isArchived) {
    this.isArchived = (isArchived ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public void setIsArchived(int isArchived) {
    this.isArchived = isArchived;
  }

  public List<Long> getIndividualsList() {
    return individualsList;
  }

  public void setIndividualsList(List<Long> individualsList) {
    this.individualsList = individualsList;
  }
}
