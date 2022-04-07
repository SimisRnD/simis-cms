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

/**
 * Encapsulates the records being returned and the record count for paging
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MedicineSpecification {

  private long id = -1L;
  private long individualId = -1L;
  private int archivedOnly = DataConstants.UNDEFINED;
  private int suspendedOnly = DataConstants.UNDEFINED;
  private String barcode = null;
  private long minMedicineId = -1L;

  public MedicineSpecification() {
  }

  public MedicineSpecification(long id) {
    this.id = id;
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

  public int getArchivedOnly() {
    return archivedOnly;
  }

  public void setArchivedOnly(int archivedOnly) {
    this.archivedOnly = archivedOnly;
  }

  public void setArchivedOnly(boolean archivedOnly) {
    this.archivedOnly = (archivedOnly ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getSuspendedOnly() {
    return suspendedOnly;
  }

  public void setSuspendedOnly(int suspendedOnly) {
    this.suspendedOnly = suspendedOnly;
  }

  public void setSuspendedOnly(boolean suspendedOnly) {
    this.suspendedOnly = (suspendedOnly ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public long getMinMedicineId() {
    return minMedicineId;
  }

  public void setMinMedicineId(long minMedicineId) {
    this.minMedicineId = minMedicineId;
  }
}
