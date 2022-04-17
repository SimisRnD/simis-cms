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
import com.simisinc.platform.domain.model.medicine.MedicineLog;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves medicine log objects
 *
 * @author matt rajkowski
 * @created 9/18/18 9:44 AM
 */
public class MedicineLogRepository {

  private static Log LOG = LogFactory.getLog(MedicineLogRepository.class);

  private static String TABLE_NAME = "medicine_log";
  private static String PRIMARY_KEY[] = new String[]{"log_id"};

  private static DataResult query(MedicineLogSpecification specification, DataConstraints constraints) {
    SqlJoins joins = new SqlJoins();
    SqlUtils where = null;
    if (specification != null) {

      joins.add("LEFT JOIN medicines medicines ON (medicine_log.medicine_id = medicines.medicine_id)");

      where = new SqlUtils()
          .addIfExists("log_id = ?", specification.getId(), -1)
          .addIfExists("medicine_log.individual_id = ?", specification.getIndividualId(), -1)
          .addIfExists("medicine_log.medicine_id = ?", specification.getMedicineId(), -1);
      if (specification.getMinDate() != null) {
        where.add("administered >= ?", specification.getMinDate());
      }
      if (specification.getMaxDate() != null) {
        where.add("administered < ?", specification.getMaxDate());
      }
      if (specification.getIndividualsList() != null && !specification.getIndividualsList().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (Long id : specification.getIndividualsList()) {
          if (sb.length() > 0) {
            sb.append(",");
          }
          sb.append(id);
        }
        where.add("medicine_log.individual_id IN (" + sb.toString() + ")");
      }
    }
    return DB.selectAllFrom(TABLE_NAME, joins, where, constraints, MedicineLogRepository::buildRecord);
  }

  public static List<MedicineLog> findAll(MedicineLogSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("reminder_id");
    DataResult result = query(specification, constraints);
    return (List<MedicineLog>) result.getRecords();
  }

  public static MedicineLog findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MedicineLog) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("log_id = ?", id),
        MedicineLogRepository::buildRecord);
  }

  public static MedicineLog save(MedicineLog record) {
    if (record.getId() > -1) {
      // Not supported
      return null;
    }
    return add(record);
  }

  private static MedicineLog add(MedicineLog record) {
    SqlUtils insertValues = new SqlUtils()
        .add("medicine_id", record.getMedicineId())
        .add("individual_id", record.getIndividualId(), -1)
        .add("reminder_id", record.getReminderId(), -1)
        .add("reminder_date", record.getReminderDate())
        .add("drug_id", record.getDrugId(), -1)
        .add("drug_name", record.getDrugName())
        .add("dosage", StringUtils.trimToNull(record.getDosage()))
        .add("form_of_medicine", StringUtils.trimToNull(record.getFormOfMedicine()))
        .add("quantity", record.getQuantityGiven())
        .add("comments", StringUtils.trimToNull(record.getComments()))
        .add("pills_left", record.getPillsLeft(), -1)
        .add("administered_by", record.getAdministeredBy())
        .add("administered", record.getAdministered())
        .add("was_taken", record.getWasTaken())
        .add("taken_on_time", record.getTakenOnTime())
        .add("was_skipped", record.getWasSkipped())
        .add("reason_comments", StringUtils.trimToNull(record.getReasonComments()));
    // Check the reason code
    if (record.getReasonCode() == MedicineLog.REASON_INDIVIDUAL_UNAVAILABLE) {
      insertValues.add("reason_individual", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_CAREGIVER_UNAVAILABLE) {
      insertValues.add("reason_caregiver", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_MEDICINE_UNAVAILABLE) {
      insertValues.add("reason_medicine", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_REFUSED) {
      insertValues.add("reason_refused", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_HEALTH_CONCERNS) {
      insertValues.add("reason_health_concerns", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_RAN_OUT) {
      insertValues.add("reason_med_ran_out", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_DOSE_NOT_NEEDED) {
      insertValues.add("reason_dose_not_needed", true);
    } else if (record.getReasonCode() == MedicineLog.REASON_OTHER) {
      insertValues.add("reason_other_concern", true);
    }

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        if (record.getReminderId() > -1) {
          if (record.getWasTaken()) {
            MedicineReminderRepository.markAsTaken(connection, record.getReminderId(), record.getAdministered());
          } else if (record.getWasSkipped()) {
            MedicineReminderRepository.markAsSkipped(connection, record.getReminderId());
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

  public static void removeAll(Connection connection, Medicine record) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("medicine_id = ?", record.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeReferences(Connection connection, Medicine record) throws SQLException {
    SqlUtils update = new SqlUtils().add("reminder_id", -1L, -1L);
    SqlUtils where = new SqlUtils().add("medicine_id = ?", record.getId());
    DB.update(connection, TABLE_NAME, update, where);
  }

  public static boolean remove(MedicineLog record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("log_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static MedicineLog buildRecord(ResultSet rs) {
    try {
      MedicineLog record = new MedicineLog();
      record.setId(rs.getLong("log_id"));
      record.setMedicineId(rs.getLong("medicine_id"));
      record.setIndividualId(rs.getLong("individual_id"));
      record.setReminderId(rs.getLong("reminder_id"));
      record.setReminderDate(rs.getTimestamp("reminder_date"));
      record.setDrugId(rs.getLong("drug_id"));
      record.setDrugName(rs.getString("drug_name"));
      record.setDosage(rs.getString("dosage"));
      record.setFormOfMedicine(rs.getString("form_of_medicine"));
      record.setQuantityGiven(rs.getInt("quantity"));
      record.setComments(rs.getString("comments"));
      record.setQuantityGiven(rs.getInt("pills_left"));
      record.setAdministeredBy(rs.getLong("administered_by"));
      record.setAdministered(rs.getTimestamp("administered"));
      record.setWasTaken(rs.getBoolean("was_taken"));
      record.setWasSkipped(rs.getBoolean("was_skipped"));
      record.setTakenOnTime(rs.getBoolean("taken_on_time"));
      if (rs.getBoolean("reason_refused")) {
        record.setReasonCode(MedicineLog.REASON_REFUSED);
      } else if (rs.getBoolean("reason_individual")) {
        record.setReasonCode(MedicineLog.REASON_INDIVIDUAL_UNAVAILABLE);
      } else if (rs.getBoolean("reason_caregiver")) {
        record.setReasonCode(MedicineLog.REASON_CAREGIVER_UNAVAILABLE);
      } else if (rs.getBoolean("reason_medicine")) {
        record.setReasonCode(MedicineLog.REASON_MEDICINE_UNAVAILABLE);
      } else if (rs.getBoolean("reason_med_ran_out")) {
        record.setReasonCode(MedicineLog.REASON_RAN_OUT);
      } else if (rs.getBoolean("reason_dose_not_needed")) {
        record.setReasonCode(MedicineLog.REASON_DOSE_NOT_NEEDED);
      } else if (rs.getBoolean("reason_health_concerns")) {
        record.setReasonCode(MedicineLog.REASON_HEALTH_CONCERNS);
      } else if (rs.getBoolean("reason_other_concern")) {
        record.setReasonCode(MedicineLog.REASON_OTHER);
      }
      record.setReasonComments(rs.getString("reason_comments"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
