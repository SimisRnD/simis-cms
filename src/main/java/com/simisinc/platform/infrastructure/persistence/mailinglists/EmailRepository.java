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

import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves email objects
 *
 * @author matt rajkowski
 * @created 3/24/19 9:30 PM
 */
public class EmailRepository {

  private static Log LOG = LogFactory.getLog(EmailRepository.class);

  private static String TABLE_NAME = "emails";
  private static String PRIMARY_KEY[] = new String[]{"email_id"};

  private static DataResult query(EmailSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      if (specification.getMailingListId() > -1) {
        where.add("EXISTS (SELECT 1 FROM mailing_list_members WHERE email_id = emails.email_id AND list_id = ?)", specification.getMailingListId());
      }
      if (StringUtils.isNotBlank(specification.getMatchesEmail())) {
        where.add("LOWER(email) = LOWER(?)", specification.getMatchesEmail().trim());
      }
      if (StringUtils.isNotBlank(specification.getMatchesName())) {
        // Like matching on a name
        String likeValue = specification.getMatchesName().trim()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
            .replace("[", "![");
        where.add("LOWER(concat_ws(' ', first_name, last_name)) LIKE LOWER(?) ESCAPE '!'", "%" + likeValue + "%");
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, EmailRepository::buildRecord);
  }

  public static List<Email> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("email_id desc"),
        EmailRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Email>) result.getRecords();
    }
    return null;
  }

  public static Email findById(long emailId) {
    return (Email) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("email_id = ?", emailId),
        EmailRepository::buildRecord);
  }

  public static Email findByEmailAddress(String email) {
    if (StringUtils.isBlank(email)) {
      return null;
    }
    return (Email) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("LOWER(email) = ?", email.trim().toLowerCase()),
        EmailRepository::buildRecord);
  }

  public static List<Email> findAll(EmailSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("email_id desc");
    DataResult result = query(specification, constraints);
    return (List<Email>) result.getRecords();
  }

  public static List<Email> findDailyUniqueLocations(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DISTINCT continent, country, state, city, latitude, longitude " +
            "FROM emails " +
            "WHERE country IS NOT NULL " +
            "AND created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND latitude IS NOT NULL " +
            "ORDER BY continent, country, state, city, latitude, longitude";
    List<Email> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        Email data = new Email();
        data.setContinent(rs.getString("continent"));
        data.setCountry(rs.getString("country"));
        data.setState(rs.getString("state"));
        data.setCity(rs.getString("city"));
        data.setLatitude(rs.getDouble("latitude"));
        data.setLongitude(rs.getDouble("longitude"));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static Email add(Email record) {
    SqlUtils insertValues = new SqlUtils()
        .add("email", record.getEmail().trim().toLowerCase())
        .addIfExists("first_name", record.getFirstName())
        .addIfExists("last_name", record.getLastName())
        .addIfExists("organization", record.getOrganization())
        .addIfExists("source", record.getSource())
        .addIfExists("ip_address", record.getIpAddress())
        .addIfExists("session_id", record.getSessionId())
        .addIfExists("user_agent", StringUtils.abbreviate(record.getUserAgent(), 500))
        .addIfExists("referer", StringUtils.abbreviate(record.getReferer(), 250))
        .addIfExists("continent", record.getContinent())
        .addIfExists("country_iso", record.getCountryIso())
        .addIfExists("country", record.getCountry())
        .addIfExists("city", record.getCity())
        .addIfExists("state_iso", record.getStateIso())
        .addIfExists("state", record.getState())
        .addIfExists("postal_code", record.getPostalCode())
        .addIfExists("timezone", record.getTimezone())
        .addIfExists("latitude", record.getLatitude(), 0)
        .addIfExists("longitude", record.getLongitude(), 0)
        .addIfExists("metro_code", record.getMetroCode(), -1)
        .addIfExists("created_by", record.getCreatedBy(), -1)
        .addIfExists("modified_by", record.getModifiedBy(), -1)
        .addIfExists("last_emailed", record.getLastEmailed())
        .addIfExists("subscribed", record.getSubscribed())
        .addIfExists("unsubscribed", record.getUnsubscribed())
        .addIfExists("unsubscribe_reason", record.getUnsubscribeReason())
        .addIfExists("last_order", record.getLastOrder())
        .add("number_of_orders", record.getNumberOfOrders())
        .add("total_spent", record.getTotalSpent())
        // @todo tags
        ;
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Email update(Email record) {
    SqlUtils updateValues = new SqlUtils()
        .add("modified_by", record.getModifiedBy(), -1)
        .addIfExists("first_name", record.getFirstName())
        .addIfExists("last_name", record.getLastName())
        .addIfExists("organization", record.getOrganization())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("subscribed", record.getSubscribed())
        .add("unsubscribed", record.getUnsubscribed())
        // @todo tags
        ;
    SqlUtils where = new SqlUtils()
        .add("email = ?", record.getEmail().trim().toLowerCase());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void markSynced(Email record) {
    if (record == null || record.getId() == -1) {
      return;
    }
    String set = "sync_date = CURRENT_TIMESTAMP";
    SqlUtils where = new SqlUtils()
        .add("email_id = ?", record.getId());
    DB.update(TABLE_NAME, set, where);
  }

  public static void markNotSynced(Email record) {
    if (record == null || record.getId() == -1) {
      return;
    }
    String set = "sync_date = NULL";
    SqlUtils where = new SqlUtils()
        .add("email_id = ?", record.getId());
    DB.update(TABLE_NAME, set, where);
  }

  private static Email buildRecord(ResultSet rs) {
    try {
      Email record = new Email();
      record.setId(rs.getLong("email_id"));
      record.setEmail(rs.getString("email"));
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setOrganization(rs.getString("organization"));
      record.setSource(rs.getString("source"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setSessionId(rs.getString("session_id"));
      record.setUserAgent(rs.getString("user_agent"));
      record.setReferer(rs.getString("referer"));
      record.setContinent(rs.getString("continent"));
      record.setCountryIso(rs.getString("country_iso"));
      record.setCountry(rs.getString("country"));
      record.setCity(rs.getString("city"));
      record.setStateIso(rs.getString("state_iso"));
      record.setState(rs.getString("state"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setTimezone(rs.getString("timezone"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setMetroCode(rs.getInt("metro_code"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      record.setLastEmailed(rs.getTimestamp("last_emailed"));
      record.setSubscribed(rs.getTimestamp("subscribed"));
      record.setUnsubscribed(rs.getTimestamp("unsubscribed"));
      record.setUnsubscribeReason(rs.getString("unsubscribe_reason"));
      record.setLastOrder(rs.getTimestamp("last_order"));
      record.setNumberOfOrders(rs.getInt("number_of_orders"));
      record.setTotalSpent(rs.getBigDecimal("total_spent"));
      // @todo tags
      record.setSyncDate(rs.getTimestamp("sync_date"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
