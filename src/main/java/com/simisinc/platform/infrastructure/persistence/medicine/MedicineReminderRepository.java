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
import com.simisinc.platform.domain.model.medicine.MedicineReminder;
import com.simisinc.platform.domain.model.medicine.MedicineReminderRawData;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/11/18 1:54 PM
 */
public class MedicineReminderRepository {

  private static Log LOG = LogFactory.getLog(MedicineReminderRepository.class);

  private static String TABLE_NAME = "medicine_reminders";
  private static String PRIMARY_KEY[] = new String[]{"reminder_id"};


  private static DataResult query(MedicineReminderSpecification specification, DataConstraints constraints) {
    SqlJoins joins = new SqlJoins();
    SqlUtils where = null;
    if (specification != null) {

      joins.add("LEFT JOIN medicines medicines ON (medicine_reminders.medicine_id = medicines.medicine_id)");
      joins.add("LEFT JOIN medicine_schedule sched ON (medicine_reminders.schedule_id = sched.schedule_id)");

      where = new SqlUtils()
          .addIfExists("reminder_id = ?", specification.getId(), -1)
          .addIfExists("medicine_reminders.individual_id = ?", specification.getIndividualId(), -1)
          .addIfExists("medicine_reminders.medicine_id = ?", specification.getMedicineId(), -1);
      if (specification.getMinDate() != null) {
        where.add("reminder_date >= ?", specification.getMinDate());
      }
      if (specification.getMaxDate() != null) {
        where.add("reminder_date < ?", specification.getMaxDate());
      }
      if (specification.getReminderIsAfterNow() != DataConstants.UNDEFINED) {
        if (specification.getReminderIsAfterNow() == DataConstants.TRUE) {
          // Show the ones which are active
          where.add("reminder_date >= NOW()");
        }
      }
      if (specification.getIsWithinEndDate() != DataConstants.UNDEFINED) {
        if (specification.getIsWithinEndDate() == DataConstants.TRUE) {
          // Show the non-expiring and unexpired
          where.add("(sched.end_date IS NULL OR sched.end_date >= NOW())");
        }
      }
      if (specification.getIsSuspended() != DataConstants.UNDEFINED) {
        if (specification.getIsSuspended() == DataConstants.TRUE) {
          // Show suspended only
          where.add("medicines.suspended IS NOT NULL");
        } else {
          // Show the non-suspended only
          where.add("medicines.suspended IS NULL");
        }
      }
      if (specification.getIsArchived() != DataConstants.UNDEFINED) {
        if (specification.getIsArchived() == DataConstants.TRUE) {
          // Show archived only
          where.add("medicines.archived IS NOT NULL");
        } else {
          // Show the active only
          where.add("medicines.archived IS NULL");
        }
      }
      if (specification.getIndividualsList() != null && !specification.getIndividualsList().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (Long id : specification.getIndividualsList()) {
          if (sb.length() > 0) {
            sb.append(",");
          }
          sb.append(id);
        }
        where.add("medicine_reminders.individual_id IN (" + sb.toString() + ")");
      }
    }
    return DB.selectAllFrom(TABLE_NAME, joins, where, constraints, MedicineReminderRepository::buildRecord);
  }

  public static List<MedicineReminder> findAll(MedicineReminderSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("reminder_id");
    DataResult result = query(specification, constraints);
    return (List<MedicineReminder>) result.getRecords();
  }

  public static MedicineReminder findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MedicineReminder) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("reminder_id = ?", id),
        MedicineReminderRepository::buildRecord);
  }

  private static PreparedStatement createPreparedStatementForDailyReminders(Connection connection, Timestamp startDate, Timestamp endDate, DayOfWeek dayOfWeek, long medicineId) throws SQLException {
    StringBuilder currentDay = new StringBuilder();
    switch (dayOfWeek) {
      case MONDAY:
        currentDay.append("on_monday");
        break;
      case TUESDAY:
        currentDay.append("on_tuesday");
        break;
      case WEDNESDAY:
        currentDay.append("on_wednesday");
        break;
      case THURSDAY:
        currentDay.append("on_thursday");
        break;
      case FRIDAY:
        currentDay.append("on_friday");
        break;
      case SATURDAY:
        currentDay.append("on_saturday");
        break;
      case SUNDAY:
        currentDay.append("on_sunday");
        break;
      default:
        break;
    }

    String startDateValue = new SimpleDateFormat("yyyy-MM-dd").format(startDate);
//    String endDateValue = new SimpleDateFormat("yyyy-MM-dd").format(endDate);

    String SQL_QUERY =
        "SELECT ind.item_id AS individual_id, m.medicine_id, sched.schedule_id, mt.time_id, mt.hour, mt.minute " +
            "FROM medicines m " +
            "LEFT JOIN items ind ON (individual_id = ind.item_id) " +
            "LEFT JOIN items drug ON (drug_id = drug.item_id) " +
            "LEFT JOIN medicine_schedule sched ON (m.medicine_id = sched.medicine_id) " +
            "LEFT JOIN medicine_times mt ON (sched.schedule_id = mt.schedule_id) " +
            "WHERE " +
            "m.archived IS NULL " +
            (medicineId > -1 ? "AND m.medicine_id = ? " : "") +
            "AND sched.start_date <= ? " +
            "AND (sched.end_date IS NULL OR sched.end_date < ?) " +
            "AND (" +
            "every_day = TRUE " +
            "OR " + currentDay.toString() + " = TRUE " +
            "OR (every_x_days IS NOT NULL AND every_x_days > 0 AND MOD(DATE_PART('day', '" + startDateValue + " 00:00:00'::date - sched.start_date)::NUMERIC, every_x_days) = 0) " +
            ") " +
            "ORDER BY mt.hour, mt.minute";

    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    if (medicineId > -1) {
      pst.setLong(++i, medicineId);
    }
    pst.setTimestamp(++i, startDate);
    pst.setTimestamp(++i, endDate);
    return pst;
  }

  public static void createMedicineReminders(long medicineId, Timestamp startDate, Timestamp endDate, DayOfWeek dayOfWeek) {
    // Load the rules to determine the daily reminders
    List<MedicineReminderRawData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = createPreparedStatementForDailyReminders(connection, startDate, endDate, dayOfWeek, medicineId);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        records.add(buildRawDataRecord(rs));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      LOG.error(se);
    }
    if (records == null || records.isEmpty()) {
      return;
    }
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Remove all reminders for the day
        removeMedicineReminders(connection, medicineId, startDate, endDate);
        // Add the specified reminders
        for (MedicineReminderRawData rawData : records) {
          Calendar calendar = Calendar.getInstance();
          calendar.setTimeInMillis(startDate.getTime());
          calendar.set(Calendar.HOUR_OF_DAY, rawData.getHour());
          calendar.set(Calendar.MINUTE, rawData.getMinute());
          calendar.set(Calendar.SECOND, 0);
          calendar.set(Calendar.MILLISECOND, 0);
          Timestamp reminder = new Timestamp(calendar.getTimeInMillis());
          SqlUtils insertValues = new SqlUtils()
              .add("individual_id", rawData.getIndividualId())
              .add("medicine_id", rawData.getMedicineId())
              .add("schedule_id", rawData.getScheduleId())
              .add("time_id", rawData.getTimeId())
              .add("reminder_date", reminder);
          DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
        }
        // Finish the transaction
        transaction.commit();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
  }

  private static void removeMedicineReminders(Connection connection, long medicineId, Timestamp startDate, Timestamp endDate) throws SQLException {
    SqlUtils deleteWhere = new SqlUtils()
        .add("medicine_id = ?", medicineId)
        .add("reminder_date >= ?", startDate)
        .add("reminder_date < ?", endDate);
    DB.deleteFrom(connection, TABLE_NAME, deleteWhere);
  }

  public static void removeAll(Connection connection, Medicine record) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("medicine_id = ?", record.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void markAsTaken(Connection connection, long reminderId, Timestamp takenTimestamp) throws SQLException {
    SqlUtils updateValues = new SqlUtils()
        .add("was_taken", true)
        .add("logged", takenTimestamp);
    SqlUtils where = new SqlUtils()
        .add("reminder_id = ?", reminderId);
    DB.update(connection, TABLE_NAME, updateValues, where);
  }

  public static void markAsSkipped(Connection connection, long reminderId) throws SQLException {
    SqlUtils updateValues = new SqlUtils()
        .add("was_skipped", true);
    SqlUtils where = new SqlUtils()
        .add("reminder_id = ?", reminderId);
    DB.update(connection, TABLE_NAME, updateValues, where);
  }

  private static MedicineReminder buildRecord(ResultSet rs) {
    try {
      MedicineReminder record = new MedicineReminder();
      record.setId(rs.getLong("reminder_id"));
      record.setIndividualId(rs.getLong("individual_id"));
      record.setMedicineId(rs.getLong("medicine_id"));
      record.setScheduleId(rs.getLong("schedule_id"));
      record.setTimeId(rs.getLong("time_id"));
      record.setReminder(rs.getTimestamp("reminder_date"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setLogged(rs.getTimestamp("logged"));
      record.setWasTaken(rs.getBoolean("was_taken"));
      record.setWasSkipped(rs.getBoolean("was_skipped"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  private static MedicineReminderRawData buildRawDataRecord(ResultSet rs) {
    try {
      MedicineReminderRawData record = new MedicineReminderRawData();
      record.setIndividualId(rs.getLong("individual_id"));
      record.setMedicineId(rs.getLong("medicine_id"));
      record.setScheduleId(rs.getLong("schedule_id"));
      record.setTimeId(rs.getLong("time_id"));
      record.setHour(rs.getInt("hour"));
      record.setMinute(rs.getInt("minute"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
