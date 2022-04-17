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

import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves menu item objects
 *
 * @author matt rajkowski
 * @created 5/1/18 11:43 AM
 */
public class MenuItemRepository {

  private static final String PRIMARY_KEY[] = new String[]{"menu_item_id"};
  private static String TABLE_NAME = "menu_items";

  private static Log LOG = LogFactory.getLog(MenuItemRepository.class);

  public static MenuItem findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MenuItem) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("menu_item_id = ?", id),
        MenuItemRepository::buildRecord);
  }

  public static MenuItem findByLink(String link) {
    if (link == null) {
      return null;
    }
    return (MenuItem) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("link = ?", link),
        MenuItemRepository::buildRecord);
  }

  public static List<MenuItem> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("menu_tab_id, item_order, menu_item_id"),
        MenuItemRepository::buildRecord);
    return (List<MenuItem>) result.getRecords();
  }

  public static List<MenuItem> findAllByMenuTab(MenuTab menuTab) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("menu_tab_id = ?", menuTab.getId()),
        new DataConstraints().setDefaultColumnToSortBy("item_order, menu_item_id").setUseCount(false),
        MenuItemRepository::buildRecord);
    return (List<MenuItem>) result.getRecords();
  }

  private static PreparedStatement createPreparedStatementAllActiveByMenuTab(Connection connection, MenuTab menuTab) throws SQLException {
    String SQL_QUERY =
        "SELECT * " +
            "FROM menu_items " +
            "WHERE draft = ? AND enabled = ? " +
            "AND menu_tab_id = ? " +
            "ORDER BY item_order, menu_item_id";
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setBoolean(1, false);
    pst.setBoolean(2, true);
    pst.setLong(3, menuTab.getId());
    return pst;
  }

  public static List<MenuItem> findAllActiveByMenuTab(MenuTab menuTab) {
    List<MenuItem> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = createPreparedStatementAllActiveByMenuTab(connection, menuTab);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        records.add(buildRecord(rs));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static MenuItem save(MenuItem record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static MenuItem add(MenuItem record) {
    SqlUtils insertValues = new SqlUtils()
        .add("menu_tab_id", record.getMenuTabId())
        .add("item_order", record.getItemOrder())
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

  public static MenuItem update(MenuItem record) {
    SqlUtils updateValues = new SqlUtils()
        .add("menu_tab_id", record.getMenuTabId())
        .add("item_order", record.getItemOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("link", StringUtils.trimToNull(record.getLink()))
        .add("page_title", StringUtils.trimToNull(record.getPageTitle()))
        .add("page_keywords", StringUtils.trimToNull(record.getPageKeywords()))
        .add("page_description", StringUtils.trimToNull(record.getPageDescription()))
        .add("draft", record.isDraft())
        .add("enabled", record.isEnabled())
        .add("comments", StringUtils.trimToNull(record.getComments()));
    SqlUtils where = new SqlUtils()
        .add("menu_item_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(MenuItem record) {
    return (DB.deleteFrom(TABLE_NAME, new SqlUtils().add("menu_item_id = ?", record.getId())) > 0);
  }

  public static void removeAll(Connection connection, MenuTab record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("menu_tab_id = ?", record.getId()));
  }

  private static PreparedStatement createPreparedStatementNextTabOrder(Connection connection, MenuTab menuTab) throws SQLException {
    String SQL_QUERY =
        "SELECT max(item_order) " +
            "FROM menu_items " +
            "WHERE menu_tab_id = ?";
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(1, menuTab.getId());
    return pst;
  }

  public static int getNextTabOrder(MenuTab menuTab) {
    int maxId = 0;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = createPreparedStatementNextTabOrder(connection, menuTab);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        maxId = rs.getInt(1) + 1;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return maxId;
  }

  private static MenuItem buildRecord(ResultSet rs) {
    try {
      MenuItem record = new MenuItem();
      record.setId(rs.getLong("menu_item_id"));
      record.setMenuTabId(rs.getLong("menu_tab_id"));
      record.setItemOrder(rs.getInt("item_order"));
      record.setName(rs.getString("name"));
      record.setLink(rs.getString("link"));
      record.setPageTitle(rs.getString("page_title"));
      record.setPageKeywords(rs.getString("page_keywords"));
      record.setPageDescription(rs.getString("page_description"));
      record.setDraft(rs.getBoolean("draft"));
      record.setEnabled(rs.getBoolean("enabled"));
//    record.setRoleIdList(rs.getString("role_id_list"));
      record.setComments(rs.getString("comments"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
