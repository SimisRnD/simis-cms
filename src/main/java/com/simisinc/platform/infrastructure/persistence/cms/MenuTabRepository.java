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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves menu tab objects
 *
 * @author matt rajkowski
 * @created 4/30/18 3:56 PM
 */
public class MenuTabRepository {

  private static final String PRIMARY_KEY[] = new String[]{"menu_tab_id"};
  private static String TABLE_NAME = "menu_tabs";

  private static Log LOG = LogFactory.getLog(MenuTabRepository.class);

  public static MenuTab findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MenuTab) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("menu_tab_id = ?", id),
        MenuTabRepository::buildRecord);
  }

  public static MenuTab findByLink(String link) {
    if (link == null) {
      return null;
    }
    return (MenuTab) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("link = ?", link),
        MenuTabRepository::buildRecord);
  }

  public static List<MenuTab> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("tab_order, menu_tab_id"),
        MenuTabRepository::buildRecord);
    return (List<MenuTab>) result.getRecords();
  }

  public static List<MenuTab> findAllActive() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("draft = ?", false)
            .add("enabled = ?", true),
        new DataConstraints().setDefaultColumnToSortBy("tab_order").setUseCount(false),
        MenuTabRepository::buildRecord);
    return (List<MenuTab>) result.getRecords();
  }

  public static MenuTab save(MenuTab record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static MenuTab add(MenuTab record) {
    SqlUtils insertValues = new SqlUtils()
        .add("tab_order", record.getTabOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("link", StringUtils.trimToNull(record.getLink()))
        .add("page_title", StringUtils.trimToNull(record.getPageTitle()))
        .add("page_keywords", StringUtils.trimToNull(record.getPageKeywords()))
        .add("page_description", StringUtils.trimToNull(record.getPageDescription()))
        .add("draft", record.isDraft())
        .add("enabled", record.isEnabled())
        .add("comments", StringUtils.trimToNull(record.getComments()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static MenuTab update(MenuTab record) {
    SqlUtils updateValues = new SqlUtils()
        .add("tab_order", record.getTabOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("link", StringUtils.trimToNull(record.getLink()))
        .add("page_title", StringUtils.trimToNull(record.getPageTitle()))
        .add("page_keywords", StringUtils.trimToNull(record.getPageKeywords()))
        .add("page_description", StringUtils.trimToNull(record.getPageDescription()))
        .add("draft", record.isDraft())
        .add("enabled", record.isEnabled())
        .add("comments", StringUtils.trimToNull(record.getComments()));
    SqlUtils where = new SqlUtils()
        .add("menu_tab_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(MenuTab record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        MenuItemRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("menu_tab_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static int getNextTabOrder() {
    String SQL_QUERY =
        "SELECT max(tab_order) " +
            "FROM menu_tabs ";
    int maxId = 0;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery();) {
      if (rs.next()) {
        maxId = rs.getInt(1) + 1;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return maxId;
  }

  private static MenuTab buildRecord(ResultSet rs) {
    try {
      MenuTab record = new MenuTab();
      record.setId(rs.getLong("menu_tab_id"));
      record.setTabOrder(rs.getInt("tab_order"));
      record.setName(rs.getString("name"));
      record.setLink(rs.getString("link"));
      record.setPageTitle(rs.getString("page_title"));
      record.setPageKeywords(rs.getString("page_keywords"));
      record.setPageDescription(rs.getString("page_description"));
      record.setDraft(rs.getBoolean("draft"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setComments(rs.getString("comments"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
