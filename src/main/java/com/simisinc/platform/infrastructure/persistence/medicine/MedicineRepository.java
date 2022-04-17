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

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.domain.model.medicine.Prescription;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves medicine objects
 *
 * @author matt rajkowski
 * @created 8/28/18 10:49 AM
 */
public class MedicineRepository {

  private static Log LOG = LogFactory.getLog(MedicineRepository.class);

  private static String TABLE_NAME = "medicines";
  private static String PRIMARY_KEY[] = new String[]{"medicine_id"};


  public static Medicine save(Medicine record) {
    if (record.getId() > -1) {
      return update(record, null, null);
    }
    return add(record, null, null);
  }

  public static Medicine save(Medicine record, MedicineSchedule medicineSchedule, Prescription prescription) {
    if (record.getId() > -1) {
      return update(record, medicineSchedule, prescription);
    }
    return add(record, medicineSchedule, prescription);
  }

  private static Medicine add(Medicine record, MedicineSchedule medicineSchedule, Prescription prescription) {
    SqlUtils insertValues = new SqlUtils()
        .add("individual_id", record.getIndividualId(), -1)
        .add("drug_id", record.getDrugId(), -1)
        .add("drug_name", record.getDrugName())
        .add("dosage", StringUtils.trimToNull(record.getDosage()))
        .add("form_of_medicine", StringUtils.trimToNull(record.getFormOfMedicine()))
        .add("appearance", StringUtils.trimToNull(record.getAppearance()))
        .add("cost", record.getCost())
        .add("pills_left", record.getQuantityOnHand(), 0)
        .add("barcode", StringUtils.trimToNull(record.getBarcode()))
        .add("condition", StringUtils.trimToNull(record.getCondition()))
        .add("comments", StringUtils.trimToNull(record.getComments()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("assigned_to", record.getAssignedTo(), -1)
        .add("suspended", record.getSuspended())
        .add("suspended_by", record.getSuspendedBy(), -1)
        .add("archived", record.getArchived())
        .add("archived_by", record.getArchivedBy(), -1)
        .add("last_taken", record.getLastTaken())
        .add("last_administered_by", record.getLastAdministeredBy(), -1);
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        if (medicineSchedule != null) {
          medicineSchedule.setMedicineId(record.getId());
          MedicineScheduleRepository.save(connection, medicineSchedule);
        }
        if (prescription != null) {
          if (!prescription.isEmpty()) {
            prescription.setMedicineId(record.getId());
            PrescriptionRepository.save(connection, prescription);
          }
        }
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("An id was not set!");
    return null;
  }


  private static Medicine update(Medicine record, MedicineSchedule medicineSchedule, Prescription prescription) {
    SqlUtils updateValues = new SqlUtils()
        .add("dosage", StringUtils.trimToNull(record.getDosage()))
        .add("form_of_medicine", StringUtils.trimToNull(record.getFormOfMedicine()))
        .add("appearance", StringUtils.trimToNull(record.getAppearance()))
        .add("pills_left", record.getQuantityOnHand(), 0)
        .add("cost", record.getCost())
        .add("barcode", StringUtils.trimToNull(record.getBarcode()))
        .add("condition", StringUtils.trimToNull(record.getCondition()))
        .add("comments", StringUtils.trimToNull(record.getComments()))
        .add("modified_by", record.getModifiedBy());
    SqlUtils where = new SqlUtils()
        .add("medicine_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        if (medicineSchedule != null) {
          // Remove the references, but keep the records
          MedicineLogRepository.removeReferences(connection, record);
          // Delete the references
          MedicineReminderRepository.removeAll(connection, record);
          // Update the related data
          MedicineTimeRepository.removeAll(connection, record);
          MedicineScheduleRepository.removeAll(connection, record);
          medicineSchedule.setMedicineId(record.getId());
          MedicineScheduleRepository.save(connection, medicineSchedule);
        }
        if (prescription != null) {
          if (!prescription.isEmpty()) {
            PrescriptionRepository.removeAll(connection, record);
            prescription.setMedicineId(record.getId());
            PrescriptionRepository.save(connection, prescription);
          }
        }
        // Update this record
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Medicine record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        PrescriptionRepository.removeAll(connection, record);
        MedicineLogRepository.removeAll(connection, record);
        MedicineReminderRepository.removeAll(connection, record);
        MedicineTimeRepository.removeAll(connection, record);
        MedicineScheduleRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("medicine_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    // @todo Delete the references
//    PrescriptionRepository.removeAll(connection, item);
//    MedicineLogRepository.removeAll(connection, item);
//    MedicineReminderRepository.removeAll(connection, item);
//    MedicineTimeRepository.removeAll(connection, item);
//    MedicineScheduleRepository.removeAll(connection, item);
    // Delete the records
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("individual_id = ?", item.getId()));
  }

  public static boolean markAsSuspended(Medicine record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Suspend the medicine
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        SqlUtils updateValues = new SqlUtils()
            .add("modified_by", record.getModifiedBy())
            .add("modified", timestamp)
            .add("suspended_by", record.getModifiedBy())
            .add("suspended", timestamp);
        SqlUtils where = new SqlUtils()
            .add("medicine_id = ?", record.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static boolean markAsResumed(Medicine record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Suspend the medicine
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        SqlUtils updateValues = new SqlUtils()
            .add("modified_by", record.getModifiedBy())
            .add("modified", timestamp)
            .add("suspended_by", -1L, -1L)
            .add("suspended", (Timestamp) null);
        SqlUtils where = new SqlUtils()
            .add("medicine_id = ?", record.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static boolean markAsArchived(Medicine record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Archive the medicine
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        SqlUtils updateValues = new SqlUtils()
            .add("modified_by", record.getModifiedBy())
            .add("modified", timestamp)
            .add("archived_by", record.getModifiedBy())
            .add("archived", timestamp);
        SqlUtils where = new SqlUtils()
            .add("medicine_id = ?", record.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static DataResult query(MedicineSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      where
          .addIfExists("medicine_id >= ?", specification.getMinMedicineId(), -1)
          .addIfExists("medicine_id = ?", specification.getId(), -1)
          .addIfExists("individual_id = ?", specification.getIndividualId(), -1)
          .addIfExists("barcode = ?", specification.getBarcode());
      if (specification.getArchivedOnly() == DataConstants.TRUE) {
        where.add("archived IS NOT NULL");
      } else if (specification.getArchivedOnly() == DataConstants.FALSE) {
        where.add("archived IS NULL");
      }
      if (specification.getSuspendedOnly() == DataConstants.TRUE) {
        where.add("suspended IS NOT NULL");
      } else if (specification.getSuspendedOnly() == DataConstants.FALSE) {
        where.add("suspended IS NULL");
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, MedicineRepository::buildRecord);
  }

  public static Medicine findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Medicine) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("medicine_id = ?", id),
        MedicineRepository::buildRecord);
  }

  public static List<Medicine> findAll(MedicineSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("medicine_id");
    DataResult result = query(specification, constraints);
    return (List<Medicine>) result.getRecords();
  }

  private static Medicine buildRecord(ResultSet rs) {
    try {
      Medicine record = new Medicine();
      record.setId(rs.getLong("medicine_id"));
      record.setIndividualId(rs.getLong("individual_id"));
      record.setDrugId(rs.getLong("drug_id"));
      record.setDrugName(rs.getString("drug_name"));
      record.setDosage(rs.getString("dosage"));
      record.setFormOfMedicine(rs.getString("form_of_medicine"));
      record.setAppearance(rs.getString("appearance"));
      record.setCost(rs.getBigDecimal("cost"));
      record.setQuantityOnHand(rs.getInt("pills_left"));
      record.setBarcode(rs.getString("barcode"));
      record.setCondition(rs.getString("condition"));
      record.setComments(rs.getString("comments"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setAssignedTo(rs.getLong("assigned_to"));
      record.setSuspended(rs.getTimestamp("suspended"));
      record.setSuspendedBy(rs.getLong("suspended_by"));
      record.setArchived(rs.getTimestamp("archived"));
      record.setArchivedBy(rs.getLong("archived_by"));
      record.setLastTaken(rs.getTimestamp("last_taken"));
      record.setLastAdministeredBy(rs.getLong("last_administered_by"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
