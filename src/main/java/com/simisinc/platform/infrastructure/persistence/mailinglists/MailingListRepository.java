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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves mailing list objects
 *
 * @author matt rajkowski
 * @created 3/24/19 9:46 PM
 */
public class MailingListRepository {

  private static Log LOG = LogFactory.getLog(MailingListRepository.class);

  private static String TABLE_NAME = "mailing_lists";
  private static String PRIMARY_KEY[] = new String[]{"list_id"};

  public static List<MailingList> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("list_order, name"),
        MailingListRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<MailingList>) result.getRecords();
    }
    return null;
  }

  public static List<MailingList> findOnlineLists() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("show_online = true")
            .add("enabled = true"),
        new DataConstraints().setDefaultColumnToSortBy("list_order, name"),
        MailingListRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<MailingList>) result.getRecords();
    }
    return null;
  }

  public static List<MailingList> findOnlineListsForEmail(long emailId) {
    if (emailId <= 0) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("show_online = true")
        .add("enabled = true")
        .add("EXISTS (SELECT 1 FROM mailing_list_members WHERE list_id = mailing_lists.list_id AND email_id = ? AND is_valid = true)", emailId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        null,
        MailingListRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<MailingList>) result.getRecords();
    }
    return null;
  }

  public static MailingList findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MailingList) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("list_id = ?", id),
        MailingListRepository::buildRecord);
  }

  public static MailingList findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (MailingList) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase().trim()),
        MailingListRepository::buildRecord);
  }

  public static long countTotalMembers() {
    long count = -1;
    String SQL_QUERY =
        "SELECT SUM(member_count) AS member_count " +
            "FROM mailing_lists ";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("member_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static MailingList save(MailingList record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static MailingList add(MailingList record) {
    SqlUtils insertValues = new SqlUtils()
        .add("list_order", record.getOrder())
        .add("name", record.getName().trim())
        .add("title", record.getTitle().trim())
        .addIfExists("description", record.getDescription())
        .add("member_count", record.getMemberCount())
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1)
        .add("last_emailed", record.getLastEmailed())
        .add("show_online", record.getShowOnline())
        .add("enabled", record.getEnabled());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static MailingList update(MailingList record) {
    SqlUtils updateValues = new SqlUtils()
        .add("list_order", record.getOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("show_online", record.getShowOnline())
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("list_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(MailingList record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        MailingListMemberRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("list_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static MailingList buildRecord(ResultSet rs) {
    try {
      MailingList record = new MailingList();
      record.setId(rs.getLong("list_id"));
      record.setOrder(rs.getInt("list_order"));
      record.setName(rs.getString("name"));
      record.setTitle(rs.getString("title"));
      record.setDescription(rs.getString("description"));
      record.setMemberCount(rs.getInt("member_count"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setLastEmailed(rs.getTimestamp("last_emailed"));
      record.setShowOnline(rs.getBoolean("show_online"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
