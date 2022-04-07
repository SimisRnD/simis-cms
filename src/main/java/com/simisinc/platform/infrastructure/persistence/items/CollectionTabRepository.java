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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionTab;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/13/21 12:00 PM
 */
public class CollectionTabRepository {

  private static Log LOG = LogFactory.getLog(CollectionTabRepository.class);

  private static String TABLE_NAME = "collection_tabs";
  private static String PRIMARY_KEY[] = new String[]{"tab_id"};

  public static List<CollectionTab> findAllByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("collection_id = ?", collectionId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("tab_order,name").setUseCount(false),
        CollectionTabRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionTab>) result.getRecords();
    }
    return new ArrayList<>();
  }

  public static boolean save(List<CollectionTab> collectionTabList) {
    // Use a transaction
    try (Connection connection = DB.getConnection();
         AutoStartTransaction a = new AutoStartTransaction(connection);
         AutoRollback transaction = new AutoRollback(connection)) {
      for (CollectionTab tab : collectionTabList) {
        save(connection, tab);
      }
      // Finish the transaction
      transaction.commit();
      return true;
    } catch (Exception e) {
      LOG.error("Tabs not saved", e);
    }
    return false;
  }

  private static CollectionTab save(Connection connection, CollectionTab record) throws SQLException {
    // Check for existing record
    if (record.getId() > -1) {
      if (StringUtils.isBlank(record.getName())) {
        // No name, so remove it
        remove(connection, record);
        return null;
      }
      // Update it
      return update(connection, record);
    }
    // Add it
    return add(connection, record);
  }

  private static CollectionTab add(Connection connection, CollectionTab record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("tab_order", record.getTabOrder())
        .add("name", record.getName())
        .add("link", record.getLink())
        .add("page_title", record.getPageTitle())
        .add("page_keywords", record.getPageKeywords())
        .add("page_description", record.getPageDescription())
        .add("draft", record.getDraft())
        .add("enabled", record.getEnabled())
        .add("page_xml", record.getPageXml())
        .add("role_id_list", record.getRoleIdList());
    // In a transaction (use the existing connection)
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static CollectionTab update(Connection connection, CollectionTab record) throws SQLException {
    SqlUtils updateValues = new SqlUtils()
        .add("tab_order", record.getTabOrder())
        .add("name", record.getName())
        .add("link", record.getLink())
        .add("page_title", record.getPageTitle())
        .add("page_keywords", record.getPageKeywords())
        .add("page_description", record.getPageDescription())
        .add("draft", record.getDraft())
        .add("enabled", record.getEnabled())
        .add("page_xml", record.getPageXml())
        .add("role_id_list", record.getRoleIdList());
    SqlUtils where = new SqlUtils().add("tab_id = ?", record.getId());
    // In a transaction (use the existing connection)
    if (DB.update(connection, TABLE_NAME, updateValues, where)) {
      return record;
    }
    return null;
  }

  private static void remove(Connection connection, CollectionTab record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("tab_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", collection.getId()));
  }

  private static CollectionTab buildRecord(ResultSet rs) {
    try {
      CollectionTab record = new CollectionTab();
      record.setId(rs.getLong("tab_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setTabOrder(DB.getInt(rs, "tab_order", 0));
      record.setName(rs.getString("name"));
      record.setLink(rs.getString("link"));
      record.setPageTitle(rs.getString("page_title"));
      record.setPageKeywords(rs.getString("page_keywords"));
      record.setPageDescription(rs.getString("page_description"));
      record.setDraft(rs.getBoolean("draft"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setPageXml(rs.getString("page_xml"));
      record.setRoleIdList(rs.getString("role_id_list"));
      record.setPageImageUrl(rs.getString("page_image_url"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
