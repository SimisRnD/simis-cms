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
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/23/18 9:31 AM
 */
public class Prescription extends Entity {

  private Long id = -1L;

  private long medicineId = -1;
  private String pharmacyName = null;
  private String pharmacyLocation = null;
  private String pharmacyPhone = null;
  private String rxNumber = null;
  private int refillsLeft = 0;
  private int dosagesPerRefill = 0;
  private String barcode = null;
  private String comments = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;

  public Prescription() {
  }

  public boolean isEmpty() {
    return (
        StringUtils.isEmpty(pharmacyName) &&
            StringUtils.isEmpty(pharmacyLocation) &&
            StringUtils.isEmpty(pharmacyPhone) &&
            StringUtils.isEmpty(rxNumber) &&
            refillsLeft == 0 &&
            dosagesPerRefill == 0 &&
            StringUtils.isEmpty(barcode) &&
            StringUtils.isEmpty(comments)
    );
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

  public String getPharmacyName() {
    return pharmacyName;
  }

  public void setPharmacyName(String pharmacyName) {
    this.pharmacyName = pharmacyName;
  }

  public String getPharmacyLocation() {
    return pharmacyLocation;
  }

  public void setPharmacyLocation(String pharmacyLocation) {
    this.pharmacyLocation = pharmacyLocation;
  }

  public String getPharmacyPhone() {
    return pharmacyPhone;
  }

  public void setPharmacyPhone(String pharmacyPhone) {
    this.pharmacyPhone = pharmacyPhone;
  }

  public String getRxNumber() {
    return rxNumber;
  }

  public void setRxNumber(String rxNumber) {
    this.rxNumber = rxNumber;
  }

  public int getRefillsLeft() {
    return refillsLeft;
  }

  public void setRefillsLeft(int refillsLeft) {
    this.refillsLeft = refillsLeft;
  }

  public int getDosagesPerRefill() {
    return dosagesPerRefill;
  }

  public void setDosagesPerRefill(int dosagesPerRefill) {
    this.dosagesPerRefill = dosagesPerRefill;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
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
}
