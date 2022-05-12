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

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves collection objects
 *
 * @author matt rajkowski
 * @created 4/18/18 10:15 PM
 */
public class CollectionRepository {

  private static Log LOG = LogFactory.getLog(CollectionRepository.class);

  private static String TABLE_NAME = "collections";
  private static String PRIMARY_KEY[] = new String[]{"collection_id"};

  public static Collection save(Collection record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Collection add(Collection record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("created_by", record.getCreatedBy())
        .add("allows_guests", PrivacyType.isPublic(record.getGuestPrivacyType()))
        .add("guest_privacy_type", record.getGuestPrivacyType())
        .add("has_allowed_groups", record.getCollectionGroupList() != null && !record.getCollectionGroupList().isEmpty())
        .add("listings_link", StringUtils.trimToNull(record.getListingsLink()))
        .add("icon", StringUtils.trimToNull(record.getIcon()))
        .add("show_listings_link", record.getShowListingsLink())
        .add("show_search", record.getShowSearch());

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the access groups
        if (record.getCollectionGroupList() != null && !record.getCollectionGroupList().isEmpty()) {
          CollectionGroupRepository.insertCollectionGroupList(connection, record);
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

  private static Collection update(Collection record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("allows_guests", PrivacyType.isPublic(record.getGuestPrivacyType()))
        .add("guest_privacy_type", record.getGuestPrivacyType())
        .add("has_allowed_groups", record.getCollectionGroupList() != null && !record.getCollectionGroupList().isEmpty())
        .add("listings_link", StringUtils.trimToNull(record.getListingsLink()))
        .add("icon", StringUtils.trimToNull(record.getIcon()))
        .add("show_listings_link", record.getShowListingsLink())
        .add("show_search", record.getShowSearch())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils().add("collection_id = ?", record.getId());
    // Use a transaction
    try (Connection connection = DB.getConnection();
         AutoStartTransaction a = new AutoStartTransaction(connection);
         AutoRollback transaction = new AutoRollback(connection)) {
      // In a transaction (use the existing connection)
      DB.update(connection, TABLE_NAME, updateValues, where);
      // Manage the access groups
      CollectionGroupRepository.removeAll(connection, record);
      CollectionGroupRepository.insertCollectionGroupList(connection, record);
      // Finish the transaction
      transaction.commit();
      // Expire the cache
      CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static Collection updateTheme(Collection record) {
    SqlUtils updateValues = new SqlUtils()
        .add("header_text_color", record.getHeaderTextColor())
        .add("header_bg_color", record.getHeaderBgColor())
        .add("menu_text_color", record.getMenuTextColor())
        .add("menu_bg_color", record.getMenuBgColor())
        .add("menu_border_color", record.getMenuBorderColor())
        .add("menu_active_text_color", record.getMenuActiveTextColor())
        .add("menu_active_bg_color", record.getMenuActiveBgColor())
        .add("menu_active_border_color", record.getMenuActiveBorderColor())
        .add("menu_hover_text_color", record.getMenuHoverTextColor())
        .add("menu_hover_bg_color", record.getMenuHoverBgColor())
        .add("menu_hover_border_color", record.getMenuHoverBorderColor())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils().add("collection_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // Expire the cache
      CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  // Remove
  public static boolean remove(Collection record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        // @note the Item, and its mapping to a Category, is currently not cleaned up until a business decision is made
//        ActivityRepository.removeAll(connection, record);
//        ItemCategoryRepository.removeAll(connection, record);
//        ItemRepository.removeAll(connection, record);
        CollectionRoleRepository.removeAll(connection, record);
        CollectionGroupRepository.removeAll(connection, record);
        CollectionTabRepository.removeAll(connection, record);
        CategoryRepository.removeAll(connection, record);
        CollectionRelationshipRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        // Invalidate the cache
        CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE, record.getUniqueId());
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  private static DataResult query(CollectionSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("collection_id = ?", specification.getId(), -1)
          .addIfExists("unique_id = ?", specification.getUniqueId());
      if (specification.getName() != null) {
        where.add("LOWER(name) = ?", specification.getName().toLowerCase());
      }
      if (specification.getForUserId() != DataConstants.UNDEFINED) {
        if (specification.getForUserId() == UserSession.GUEST_ID) {
          where.add("allows_guests = true");
        } else {
          // For logged out and logged in users
          where.add(
              "(allows_guests = true " +
                  "OR (has_allowed_groups = true " +
                  "AND EXISTS (SELECT 1 FROM collection_groups WHERE collection_id = collections.collection_id " +
                  "AND EXISTS (SELECT 1 FROM user_groups WHERE group_id = collection_groups.group_id AND user_id = ?))))",
              specification.getForUserId());
        }
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, CollectionRepository::buildRecord);
  }

  public static Collection findById(long id) {
    if (id == -1) {
      return null;
    }
    Collection collection = (Collection) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("collection_id = ?", id),
        CollectionRepository::buildRecord);
    populateRelatedData(collection);
    return collection;
  }

  public static Collection findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    Collection collection = (Collection) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("unique_id = ?", uniqueId),
        CollectionRepository::buildRecord);
    populateRelatedData(collection);
    return collection;
  }

  public static Collection findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    Collection collection = (Collection) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase()),
        CollectionRepository::buildRecord);
    populateRelatedData(collection);
    return collection;
  }

  public static List<Collection> findAll() {
    return findAll(null, null);
  }

  public static List<Collection> findAll(CollectionSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints().setUseCount(false);
    }
    constraints.setDefaultColumnToSortBy("name");
    DataResult result = query(specification, constraints);
    List<Collection> collectionList = (List<Collection>) result.getRecords();
    for (Collection collection : collectionList) {
      populateRelatedData(collection);
    }
    return collectionList;
  }

  private static void populateRelatedData(Collection collection) {
    if (collection == null) {
      return;
    }
    if (collection.doAllowedGroupsCheck()) {
      List<CollectionGroup> allowedGroupList = CollectionGroupRepository.findAllByCollectionId(collection.getId());
      collection.setCollectionGroupList(allowedGroupList);
    }
  }


  public static boolean updateCategoryCount(Connection connection, long collectionId, int value) throws SQLException {
    // Increment the count
    try (PreparedStatement pst = createPreparedStatementForCategoryCount(connection, collectionId, value)) {
      return pst.execute();
    }
  }

  private static PreparedStatement createPreparedStatementForCategoryCount(Connection connection, long collectionId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE collections " +
            "SET category_count = category_count + ? " +
            "WHERE collection_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, collectionId);
    return pst;
  }


  public static boolean updateItemCount(Connection connection, long collectionId, int value) {
    try {
      // Increment the count
      try (PreparedStatement pst = createPreparedStatementForItemCount(connection, collectionId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    } finally {
      // Expire the cache
      CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE,
          LoadCollectionCommand.loadCollectionById(collectionId).getUniqueId());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForItemCount(Connection connection, long collectionId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE collections " +
            "SET item_count = item_count + ? " +
            "WHERE collection_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, collectionId);
    return pst;
  }

  public static Collection buildRecord(ResultSet rs) {
    try {
      Collection record = new Collection();
      record.setId(rs.getLong("collection_id"));
      record.setName(rs.getString("name"));
      record.setUniqueId(rs.getString("unique_id"));
      record.setDescription(rs.getString("description"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setCategoryCount(rs.getLong("category_count"));
      record.setItemCount(rs.getLong("item_count"));
      record.setHasAllowedGroups(rs.getBoolean("has_allowed_groups"));
      record.setAllowsGuests(rs.getBoolean("allows_guests"));
      record.setGuestPrivacyType(rs.getInt("guest_privacy_type"));
      record.setListingsLink(rs.getString("listings_link"));
      record.setImageUrl(rs.getString("image_url"));
      record.setHeaderXml(rs.getString("header_xml"));
      record.setIcon(rs.getString("icon"));
      record.setShowListingsLink(rs.getBoolean("show_listings_link"));
      record.setShowSearch(rs.getBoolean("show_search"));
      record.setHeaderTextColor(rs.getString("header_text_color"));
      record.setHeaderBgColor(rs.getString("header_bg_color"));
      record.setMenuTextColor(rs.getString("menu_text_color"));
      record.setMenuBgColor(rs.getString("menu_bg_color"));
      record.setMenuBorderColor(rs.getString("menu_border_color"));
      record.setMenuActiveTextColor(rs.getString("menu_active_text_color"));
      record.setMenuActiveBgColor(rs.getString("menu_active_bg_color"));
      record.setMenuActiveBorderColor(rs.getString("menu_active_border_color"));
      record.setMenuHoverTextColor(rs.getString("menu_hover_text_color"));
      record.setMenuHoverBgColor(rs.getString("menu_hover_bg_color"));
      record.setMenuHoverBorderColor(rs.getString("menu_hover_border_color"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
