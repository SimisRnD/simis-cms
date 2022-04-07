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

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/23/18 9:28 AM
 */
public class Medicine extends Entity {

  private Long id = -1L;

  private long individualId = -1;
  private long drugId = -1;
  private String drugName = null;
  private String dosage = null;
  private String formOfMedicine = null;
  private String appearance = null;
  private int quantityOnHand = -1;
  private BigDecimal cost = null;
  private String barcode = null;
  private String condition = null;
  private String comments = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;
  private long assignedTo = -1;
  private Timestamp suspended = null;
  private long suspendedBy = -1;
  private Timestamp archived = null;
  private long archivedBy = -1;
  private Timestamp lastTaken = null;
  private long lastAdministeredBy = -1;

  public Medicine() {
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

  public String getAppearance() {
    return appearance;
  }

  public void setAppearance(String appearance) {
    this.appearance = appearance;
  }

  public int getQuantityOnHand() {
    return quantityOnHand;
  }

  public void setQuantityOnHand(int quantityOnHand) {
    this.quantityOnHand = quantityOnHand;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal cost) {
    this.cost = cost;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
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

  public long getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(long assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Timestamp getSuspended() {
    return suspended;
  }

  public void setSuspended(Timestamp suspended) {
    this.suspended = suspended;
  }

  public long getSuspendedBy() {
    return suspendedBy;
  }

  public void setSuspendedBy(long suspendedBy) {
    this.suspendedBy = suspendedBy;
  }

  public Timestamp getArchived() {
    return archived;
  }

  public void setArchived(Timestamp archived) {
    this.archived = archived;
  }

  public long getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(long archivedBy) {
    this.archivedBy = archivedBy;
  }

  public Timestamp getLastTaken() {
    return lastTaken;
  }

  public void setLastTaken(Timestamp lastTaken) {
    this.lastTaken = lastTaken;
  }

  public long getLastAdministeredBy() {
    return lastAdministeredBy;
  }

  public void setLastAdministeredBy(long lastAdministeredBy) {
    this.lastAdministeredBy = lastAdministeredBy;
  }
}
