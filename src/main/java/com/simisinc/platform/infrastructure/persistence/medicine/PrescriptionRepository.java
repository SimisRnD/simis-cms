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

import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.domain.model.medicine.Prescription;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves prescription objects
 *
 * @author matt rajkowski
 * @created 9/10/18 6:02 PM
 */
public class PrescriptionRepository {

  private static Log LOG = LogFactory.getLog(PrescriptionRepository.class);

  private static String TABLE_NAME = "prescriptions";
  private static String PRIMARY_KEY[] = new String[]{"prescription_id"};


  public static Prescription save(Prescription record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Prescription save(Connection connection, Prescription record) throws SQLException {
    if (record.getId() > -1) {
      LOG.error("Not supported...");
      return update(record);
    }
    return add(connection, record);
  }

  private static Prescription add(Prescription record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        add(connection, record);
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


  private static Prescription add(Connection connection, Prescription record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("medicine_id", record.getMedicineId(), -1)
        .add("pharmacy", record.getPharmacyName())
        .add("pharmacy_location", record.getPharmacyLocation())
        .add("pharmacy_phone", record.getPharmacyPhone())
        .add("rx_number", record.getRxNumber())
        .add("refills_left", record.getRefillsLeft())
        .add("pill_total", record.getDosagesPerRefill())
        .add("barcode", record.getBarcode())
        .add("comments", record.getComments())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    return record;
  }


  private static Prescription update(Prescription record) {
    SqlUtils updateValues = new SqlUtils()
        .add("pharmacy", record.getPharmacyName())
        .add("pharmacy_location", record.getPharmacyLocation())
        .add("pharmacy_phone", record.getPharmacyPhone())
        .add("rx_number", record.getRxNumber())
        .add("refills_left", record.getRefillsLeft())
        .add("pill_total", record.getDosagesPerRefill())
        .add("barcode", record.getBarcode())
        .add("comments", record.getComments())
        .add("modified_by", record.getModifiedBy());
    SqlUtils where = new SqlUtils()
        .add("prescription_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Prescription record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("prescription_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void remove(Connection connection, Medicine record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("medicine_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Medicine record) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("medicine_id = ?", record.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static Prescription findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Prescription) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("prescription_id = ?", id),
        PrescriptionRepository::buildRecord);
  }

  public static Prescription findByMedicineId(long medicineId) {
    if (medicineId == -1) {
      return null;
    }
    return (Prescription) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("medicine_id = ?", medicineId),
        PrescriptionRepository::buildRecord);
  }

  public static List<Prescription> findAllByMedicineId(long medicineId) {
    if (medicineId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("medicine_id = ?", medicineId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("prescription_id").setUseCount(false),
        PrescriptionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Prescription>) result.getRecords();
    }
    return null;
  }

  private static Prescription buildRecord(ResultSet rs) {
    try {
      Prescription record = new Prescription();
      record.setId(rs.getLong("prescription_id"));
      record.setMedicineId(rs.getLong("medicine_id"));
      record.setPharmacyName(rs.getString("pharmacy"));
      record.setPharmacyLocation(rs.getString("pharmacy_location"));
      record.setPharmacyPhone(rs.getString("pharmacy_phone"));
      record.setRxNumber(rs.getString("rx_number"));
      record.setRefillsLeft(rs.getInt("refills_left"));
      record.setDosagesPerRefill(rs.getInt("pill_total"));
      record.setBarcode(rs.getString("barcode"));
      record.setComments(rs.getString("comments"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
