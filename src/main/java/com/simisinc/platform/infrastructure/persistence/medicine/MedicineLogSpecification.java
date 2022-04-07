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

import java.sql.Timestamp;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 11/5/18 8:50 AM
 */
public class MedicineLogSpecification {

  private long id = -1L;
  private long individualId = -1L;
  private long medicineId = -1L;
  private Timestamp minDate = null;
  private Timestamp maxDate = null;
  private List<Long> individualsList = null;

  public MedicineLogSpecification() {
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

  public List<Long> getIndividualsList() {
    return individualsList;
  }

  public void setIndividualsList(List<Long> individualsList) {
    this.individualsList = individualsList;
  }
}
