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
import com.simisinc.platform.domain.model.medicine.MedicineSchedule;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves medicine schedule objects
 *
 * @author matt rajkowski
 * @created 9/10/18 4:51 PM
 */
public class MedicineScheduleRepository {

  private static Log LOG = LogFactory.getLog(MedicineScheduleRepository.class);

  private static String TABLE_NAME = "medicine_schedule";
  private static String PRIMARY_KEY[] = new String[]{"schedule_id"};


  public static MedicineSchedule save(MedicineSchedule record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static MedicineSchedule save(Connection connection, MedicineSchedule record) throws SQLException {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(connection, record);
  }

  private static MedicineSchedule add(MedicineSchedule record) {
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


  private static MedicineSchedule add(Connection connection, MedicineSchedule record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("medicine_id", record.getMedicineId(), -1)
        .add("as_needed", record.getFrequency() == MedicineSchedule.AS_NEEDED)
        .add("every_day", record.getFrequency() == MedicineSchedule.EVERY_DAY)
        .add("every_x_days", record.getDaysToRepeat())
        .add("on_monday", record.isOnMonday())
        .add("on_tuesday", record.isOnTuesday())
        .add("on_wednesday", record.isOnWednesday())
        .add("on_thursday", record.isOnThursday())
        .add("on_friday", record.isOnFriday())
        .add("on_saturday", record.isOnSaturday())
        .add("on_sunday", record.isOnSunday())
        .add("times_a_day", record.getMedicineTimeList() != null ? record.getMedicineTimeList().size() : 0)
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("comments", record.getNotes())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getMedicineTimeList() != null) {
      MedicineTimeRepository.insertMedicineTimeList(connection, record);
    }
    return record;
  }


  private static MedicineSchedule update(MedicineSchedule record) {
    SqlUtils updateValues = new SqlUtils()
        .add("as_needed", record.getFrequency() == MedicineSchedule.AS_NEEDED)
        .add("every_day", record.getFrequency() == MedicineSchedule.EVERY_DAY)
        .add("every_x_days", record.getDaysToRepeat())
        .add("on_monday", record.isOnMonday())
        .add("on_tuesday", record.isOnTuesday())
        .add("on_wednesday", record.isOnWednesday())
        .add("on_thursday", record.isOnThursday())
        .add("on_friday", record.isOnFriday())
        .add("on_saturday", record.isOnSaturday())
        .add("on_sunday", record.isOnSunday())
        .add("times_a_day", record.getMedicineTimeList() != null ? record.getMedicineTimeList().size() : 0)
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("comments", record.getNotes())
        .add("modified_by", record.getModifiedBy());
    SqlUtils where = new SqlUtils()
        .add("schedule_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(MedicineSchedule record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("schedule_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Medicine record) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("medicine_id = ?", record.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static MedicineSchedule findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MedicineSchedule) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("schedule_id = ?", id),
        MedicineScheduleRepository::buildRecord);
  }

  public static MedicineSchedule findByMedicineId(long medicineId) {
    if (medicineId == -1) {
      return null;
    }
    return (MedicineSchedule) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("medicine_id = ?", medicineId),
        MedicineScheduleRepository::buildRecord);
  }

  public static List<MedicineSchedule> findAllByMedicineId(long medicineId) {
    if (medicineId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("medicine_id = ?", medicineId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("schedule_id").setUseCount(false),
        MedicineScheduleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<MedicineSchedule>) result.getRecords();
    }
    return null;
  }

  private static MedicineSchedule buildRecord(ResultSet rs) {
    try {
      MedicineSchedule record = new MedicineSchedule();
      record.setId(rs.getLong("schedule_id"));
      record.setMedicineId(rs.getLong("medicine_id"));
      boolean asNeeded = rs.getBoolean("as_needed");
      boolean everyDay = rs.getBoolean("every_day");
      int everyNDays = rs.getInt("every_x_days");
      record.setOnMonday(rs.getBoolean("on_monday"));
      record.setOnTuesday(rs.getBoolean("on_tuesday"));
      record.setOnWednesday(rs.getBoolean("on_wednesday"));
      record.setOnThursday(rs.getBoolean("on_thursday"));
      record.setOnFriday(rs.getBoolean("on_friday"));
      record.setOnSaturday(rs.getBoolean("on_saturday"));
      record.setOnSunday(rs.getBoolean("on_sunday"));
      if (asNeeded) {
        record.setFrequency(MedicineSchedule.AS_NEEDED);
      } else if (everyDay) {
        record.setFrequency(MedicineSchedule.EVERY_DAY);
      } else if (everyNDays > 0) {
        record.setFrequency(MedicineSchedule.EVERY_N_DAYS);
        record.setDaysToRepeat(everyNDays);
      } else {
        record.setFrequency(MedicineSchedule.SPECIFIC_DAYS);
      }
      record.setTimesADay(rs.getInt("times_a_day"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      record.setNotes(rs.getString("comments"));
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
