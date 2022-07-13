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

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.CustomFieldListJSONCommand;
import com.simisinc.platform.application.maps.ValidateGeoRegion;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCategory;
import com.simisinc.platform.domain.model.items.ItemFileVersion;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Persists and retrieves item objects
 *
 * @author matt rajkowski
 * @created 4/18/18 10:15 PM
 */
public class ItemRepository {

  private static Log LOG = LogFactory.getLog(ItemRepository.class);

  private static String TABLE_NAME = "items";
  private static String PRIMARY_KEY[] = new String[]{"item_id"};


  public static Item save(Item record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Item add(Item record) {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("category_id", record.getCategoryId(), -1)
        .add("dataset_id", record.getDatasetId(), -1)
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("description_text", HtmlCommand.text(StringUtils.trimToNull(record.getDescription())))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("location_name", StringUtils.trimToNull(record.getLocation()))
        .add("street", StringUtils.trimToNull(record.getStreet()))
        .add("address_line_2", StringUtils.trimToNull(record.getAddressLine2()))
        .add("address_line_3", StringUtils.trimToNull(record.getAddressLine3()))
        .add("city", StringUtils.trimToNull(record.getCity()))
        .add("state", StringUtils.trimToNull(record.getState()))
        .add("country", StringUtils.trimToNull(record.getCountry()))
        .add("postal_code", StringUtils.trimToNull(record.getPostalCode()))
        .add("county", StringUtils.trimToNull(record.getCounty()))
        .add("phone_number", StringUtils.trimToNull(record.getPhoneNumber()))
        .add("email", StringUtils.trimToNull(record.getEmail()))
        .add("cost", record.getCost())
        .add("expected_date", record.getExpectedDate())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("expiration_date", record.getExpirationDate())
        .add("url", StringUtils.trimToNull(record.getUrl()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("barcode", StringUtils.trimToNull(record.getBarcode()))
        .add("keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("archived_by", record.getArchivedBy(), -1)
        .add("archived", record.getArchived())
        .add("assigned_to", record.getAssignedTo(), -1)
        .add("assigned", record.getAssigned())
        .add("approved_by", record.getApprovedBy(), -1)
        .add("approved", record.getApproved())
        .add("source", record.getSource());
    if (record.hasGeoPoint()) {
      insertValues.add("latitude", record.getLatitude());
      insertValues.add("longitude", record.getLongitude());
      insertValues.addGeomPoint("geom", record.getLatitude(), record.getLongitude());
    }
    if (record.getCustomFieldList() != null && !record.getCustomFieldList().isEmpty()) {
      insertValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, CustomFieldListJSONCommand.createJSONString(record.getCustomFieldList())));
    }

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the categories
        ItemCategoryRepository.insertItemCategoryList(connection, record);
        // Manage a few related tables
        CollectionRepository.updateItemCount(connection, record.getCollectionId(), 1);
        CategoryRepository.updateItemCount(connection, record.getCategoryId(), 1);
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


  private static Item update(Item record) {
    SqlUtils updateValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("category_id", record.getCategoryId(), -1)
//        .add("dataset_id", record.getDatasetId(), -1)
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("description_text", HtmlCommand.text(StringUtils.trimToNull(record.getDescription())))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("location_name", StringUtils.trimToNull(record.getLocation()))
        .add("street", StringUtils.trimToNull(record.getStreet()))
        .add("address_line_2", StringUtils.trimToNull(record.getAddressLine2()))
        .add("address_line_3", StringUtils.trimToNull(record.getAddressLine3()))
        .add("city", StringUtils.trimToNull(record.getCity()))
        .add("state", StringUtils.trimToNull(record.getState()))
        .add("country", StringUtils.trimToNull(record.getCountry()))
        .add("postal_code", StringUtils.trimToNull(record.getPostalCode()))
        .add("county", StringUtils.trimToNull(record.getCounty()))
        .add("phone_number", StringUtils.trimToNull(record.getPhoneNumber()))
        .add("email", StringUtils.trimToNull(record.getEmail()))
        .add("cost", record.getCost())
        .add("expected_date", record.getExpectedDate())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate())
        .add("expiration_date", record.getExpirationDate())
        .add("url", StringUtils.trimToNull(record.getUrl()))
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("barcode", StringUtils.trimToNull(record.getBarcode()))
        .add("keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("archived_by", record.getArchivedBy(), -1)
        .add("archived", record.getArchived())
        .add("assigned_to", record.getAssignedTo(), -1)
        .add("assigned", record.getAssigned())
        .add("approved_by", record.getApprovedBy(), -1)
        .add("approved", record.getApproved());
    if (record.hasGeoPoint()) {
      updateValues.add("latitude", record.getLatitude());
      updateValues.add("longitude", record.getLongitude());
      updateValues.addGeomPoint("geom", record.getLatitude(), record.getLongitude());
    } else {
      updateValues.add("latitude", 0L, 0L);
      updateValues.add("longitude", 0L, 0L);
      updateValues.addGeomPoint("geom", 0, 0);
    }
    // Handle custom fields
    if (record.getCustomFieldList() != null && !record.getCustomFieldList().isEmpty()) {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, CustomFieldListJSONCommand.createJSONString(record.getCustomFieldList())));
    } else {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, null));
    }
    SqlUtils where = new SqlUtils().add("item_id = ?", record.getId());

    // Use the previous records for updates
    Item previousRecord = ItemRepository.findById(record.getId());
    List<ItemCategory> existingCategoryList = ItemCategoryRepository.findAllByItemId(record.getId());
    List<Long> newCategoryList = Arrays.asList(record.getCategoryIdList());

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);

        // If the master categoryId does not match, then update the category id counts
        if (previousRecord.getCategoryId() != record.getCategoryId()) {
          // This category was removed
          if (previousRecord.getCategoryId() > -1) {
            CategoryRepository.updateItemCount(connection, previousRecord.getCategoryId(), -1);
          }
          // This category was added
          if (record.getCategoryId() > -1) {
            CategoryRepository.updateItemCount(connection, record.getCategoryId(), 1);
          }
        }

        // Compare the existing list and the changed list
        if (existingCategoryList != null) {
          for (ItemCategory existingCategory : existingCategoryList) {
            if (!newCategoryList.contains(existingCategory.getCategoryId())) {
              // Remove from database
              ItemCategoryRepository.removeItemCategoryId(connection, record, existingCategory.getCategoryId());
            }
          }
        }

        for (Long newCategoryId : newCategoryList) {
          boolean hasCategory = false;
          if (existingCategoryList != null) {
            for (ItemCategory existingCategory : existingCategoryList) {
              if (existingCategory.getCategoryId() == newCategoryId) {
                hasCategory = true;
                break;
              }
            }
          }
          if (!hasCategory) {
            // Add to database
            ItemCategoryRepository.insertItemCategoryId(connection, record, newCategoryId);
          }
        }

        // Finish the transaction
        transaction.commit();
        // Expire the cache
//        CacheManager.invalidateKey(CacheManager.ITEM_UNIQUE_ID_CACHE, record.getUniqueId());
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static boolean remove(Item record) {
    try {
      // Determine the files to delete
      ItemFileVersionSpecification specification = new ItemFileVersionSpecification();
      specification.setItemId(record.getId());
      List<ItemFileVersion> fileVersionList = ItemFileVersionRepository.findAll(specification, null);
      // Delete the database entries
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ActivityRepository.removeAll(connection, record);
        ItemCategoryRepository.removeAll(connection, record);
        MemberRoleRepository.removeAll(connection, record);
        MemberRepository.removeAll(connection, record);
        ItemRelationshipRepository.removeAll(connection, record);
        ItemFileVersionRepository.removeAll(connection, record);
        ItemFileItemRepository.removeAll(connection, record);
        ItemSubFolderRepository.removeAll(connection, record);
        ItemFolderCategoryRepository.removeAll(connection, record);
        ItemFolderGroupRepository.removeAll(connection, record);
        ItemFolderRepository.removeAll(connection, record);
        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        MedicineRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
      }
      // Cleanup the files
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      for (ItemFileVersion fileVersion : fileVersionList) {
        String fileServerPath = fileVersion.getFileServerPath();
        if (StringUtils.isBlank(fileServerPath)) {
          continue;
        }
        File file = new File(serverRootPath + fileServerPath);
        if (file.exists() && file.isFile()) {
          file.delete();
        }
      }
      return true;
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void approve(Item record, User user) {
    SqlUtils updateValues = new SqlUtils()
        .add("approved", new Timestamp(System.currentTimeMillis()))
        .add("approved_by", user.getId());
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  public static void removeItemApproval(Item record, User user) {
    SqlUtils updateValues = new SqlUtils()
        .add("approved", (Timestamp) null)
        .add("approved_by", -1, -1);
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  public static void removeAll(Connection connection, Collection record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", record.getId()));
  }

  private static DataResult query(ItemSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlJoins joins = new SqlJoins();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {

      joins.add("LEFT JOIN collections ON (items.collection_id = collections.collection_id)");

      where
          .addIfExists("item_id = ?", specification.getId(), -1)
          .addIfExists("item_id <> ?", specification.getExcludeId(), -1)
          .addIfExists("items.unique_id = ?", specification.getUniqueId())
          .addIfExists("collections.collection_id = ?", specification.getCollectionId(), -1)
          .addIfExists("barcode = ?", specification.getBarcode());

      if (specification.getApprovedOnly()) {
        where.add("approved is not null");
      } else if (specification.getUnapprovedOnly()) {
        where.add("approved is null");
      }
      if (specification.getName() != null) {
        where.add("LOWER(items.name) = ?", specification.getName().trim().toLowerCase());
      }
      if (specification.getMatchesName() != null) {
        String likeValue = specification.getMatchesName().trim()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
            .replace("[", "![");
        where.add("LOWER(items.name) LIKE LOWER(?) ESCAPE '!'", likeValue + "%");
      }
      if (specification.getCategoryId() > -1) {
        //where.add("category_id = ?", specification.getCategoryId(), -1);
        where.add("EXISTS (SELECT 1 FROM item_categories WHERE item_id = items.item_id AND category_id = ?)", specification.getCategoryId());
      }

      // For user id
      // User must be in a user group with collection access, or be a member of the item
      // AND (user_id EXISTS in user group for related collection ... OR user_id EXISTS in members...) (item_id) (collection_id...)
      if (specification.getForUserId() != DataConstants.UNDEFINED) {
        if (specification.getForUserId() == UserSession.GUEST_ID) {
          // For logged out users
          where.add("collections.allows_guests = true");
        } else {
          // For logged out and logged in users
          where.add(
              "(collections.allows_guests = true " +
                  "OR (has_allowed_groups = true " +
                  "AND EXISTS (SELECT 1 FROM collection_groups WHERE collection_groups.collection_id = collections.collection_id AND view_all = true " +
                  "AND EXISTS (SELECT 1 FROM user_groups WHERE user_groups.group_id = collection_groups.group_id AND user_id = ?))" +
                  ") " +
                  "OR EXISTS (SELECT 1 FROM members WHERE items.item_id = members.item_id AND user_id = ? AND approved IS NOT NULL)" +
                  ")",
              new Long[]{specification.getForUserId(), specification.getForUserId()});
        }
      }

      // User must be a member of the item
      if (specification.getForMemberWithUserId() != DataConstants.UNDEFINED) {
        // For logged in users
        where.add("EXISTS (SELECT 1 FROM members WHERE items.item_id = members.item_id AND user_id = ? AND approved IS NOT NULL)", specification.getForMemberWithUserId());
      }

      // Use the location geo data
      if (StringUtils.isNotBlank(specification.getSearchLocation())) {
        // Skip the SELECT COUNT(*), it causes slowdowns due to coordinate issue
        constraints.setUseCount(false);
        // Skip items without a location
        where.add("geom IS NOT NULL");
        // Determine if there is a region to search within
        String value = specification.getSearchLocation();
        if (StringUtils.isNumeric(value) && value.length() == 5) {
          // This is a zip code
          if (specification.getWithinMeters() > 0) {
//            where.add("ST_DWithin(geom::geography, (SELECT geom::geography FROM zip_codes WHERE code = ?), " + specification.getWithinMeters() + ")", value);
          }
          // Override the order by for closest first
          orderBy.add("geom <-> (SELECT geom FROM zip_codes WHERE code = ?)", value);
        } else {
          // Treat this as a city with a possible region
          String city = null;
          String region = null;
          int cityIdx = value.indexOf(",");
          if (cityIdx > -1) {
            city = value.substring(0, cityIdx).trim().toLowerCase();
            region = value.substring(cityIdx + 1).trim().toUpperCase();
          } else {
            city = value.trim().toLowerCase();
          }
          if (ValidateGeoRegion.isValidWorldCitiesRegion(region)) {
            // Use the region
            // @todo the region is validated because a prepared statement is needed
            if (specification.getWithinMeters() > 0) {
//              where.add("ST_DWithin(geom::geography, (SELECT geom::geography FROM world_cities WHERE city = ? AND region = '" + region + "' ORDER BY population DESC LIMIT 1), " + specification.getWithinMeters() + ")", city);
            }
            // Override the order by for closest first
            orderBy.add("geom <-> (SELECT geom FROM world_cities WHERE city = ? AND region = '" + region + "' ORDER BY population DESC LIMIT 1)", city);
          } else {
            // Just use the city
            if (specification.getWithinMeters() > 0) {
//              where.add("ST_DWithin(geom::geography, (SELECT geom::geography FROM world_cities WHERE city = ? ORDER BY population DESC LIMIT 1), " + specification.getWithinMeters() + ")", city);
            }
            // Override the order by for closest first
            orderBy.add("geom <-> (SELECT geom FROM world_cities WHERE city = ? ORDER BY population DESC LIMIT 1)", city);
          }
        }
      }

      // Use the search engine
      if (StringUtils.isNotBlank(specification.getSearchName())) {
        select.add("ts_headline('english', items.name || ' ' || coalesce(keywords,'') || ' ' || coalesce(summary,''), PLAINTO_TSQUERY('title_stem', ?), 'StartSel=${b}, StopSel=${/b}, MaxWords=30, MinWords=15, ShortWord=3, HighlightAll=FALSE, MaxFragments=2, FragmentDelimiter=\" ... \"') AS highlight", specification.getSearchName().trim());
        select.add("TS_RANK_CD(tsv, PLAINTO_TSQUERY('title_stem', ?)) AS rank", specification.getSearchName().trim());
        where.add("tsv @@ PLAINTO_TSQUERY('title_stem', ?)", specification.getSearchName().trim());
        // Override the order by for rank first
        orderBy.add("rank DESC, item_id");
      }

      // Find items nearby
      if (specification.getNearItemId() > DataConstants.UNDEFINED) {
        constraints.setUseCount(false);
        where.add("geom IS NOT NULL");
        if (specification.getWithinMeters() > 0) {
          // @note currently slow
//          where.add("ST_DWithin(geom::geography, (SELECT geom::geography FROM items WHERE item_id = ?), " + specification.getWithinMeters() + ")", specification.getNearItemId());
        }
        // Override the order by for closest first
        orderBy.add("geom <-> (SELECT geom FROM items WHERE item_id = ?)", specification.getNearItemId());
      }

      if (specification.hasGeoPoint()) {
        constraints.setUseCount(false);
        where.add("geom IS NOT NULL");
        if (specification.getWithinMeters() > 0) {
          where.add("ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(" + specification.getLatitude() + "," + specification.getLongitude() + "), 4326)::geography, " + specification.getWithinMeters() + ")");
        }
        orderBy.add("geom <-> ST_SetSRID(ST_MakePoint(" + specification.getLatitude() + "," + specification.getLongitude() + "), 4326)");
      }

      if (specification.getHasCoordinates() != DataConstants.UNDEFINED) {
        if (specification.getHasCoordinates() == DataConstants.TRUE) {
          where.add("latitude <> 0 AND longitude <> 0");
        } else {
          where.add("latitude = 0 AND longitude = 0");
        }
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, joins, where, orderBy, constraints, ItemRepository::buildRecord);
  }

  public static Item findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Item) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("item_id = ?", id),
        ItemRepository::buildRecord);
  }

  public static Item findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (Item) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("unique_id = ?", uniqueId),
        ItemRepository::buildRecord);
  }

  public static Item findByIdWithinCollection(long itemId, long collectionId) {
    if (itemId == -1) {
      LOG.warn("findByIdWithinCollection item ID is -1");
      return null;
    }
    if (collectionId == -1) {
      LOG.warn("findByNameWithinCollection collection ID is -1");
      return null;
    }
    return (Item) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils()
            .add("item_id = ?", itemId)
            .add("collection_id = ?", collectionId),
        ItemRepository::buildRecord);
  }

  public static Item findByUniqueIdWithinCollection(String uniqueId, long collectionId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    if (collectionId == -1) {
      LOG.warn("findByNameWithinCollection collection ID is -1");
      return null;
    }
    return (Item) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils()
            .add("unique_id = ?", uniqueId)
            .add("collection_id = ?", collectionId),
        ItemRepository::buildRecord);
  }

  public static Item findByNameWithinCollection(String name, long collectionId) {
    if (StringUtils.isBlank(name)) {
      LOG.warn("findByNameWithinCollection name is blank");
      return null;
    }
    if (collectionId == -1) {
      LOG.warn("findByNameWithinCollection collection ID is -1");
      return null;
    }
    return (Item) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils()
            .add("LOWER(name) = ?", name.trim().toLowerCase())
            .add("collection_id = ?", collectionId),
        ItemRepository::buildRecord);
  }

  public static List<Item> findAll(ItemSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("LOWER(items.name)");
    DataResult result = query(specification, constraints);
    return (List<Item>) result.getRecords();
  }

  private static Item buildRecord(ResultSet rs) {
    try {
      Item record = new Item();
      record.setId(rs.getLong("item_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setUniqueId(rs.getString("unique_id"));
      record.setName(rs.getString("name"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setArchived(rs.getTimestamp("archived"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setLocation(rs.getString("location_name"));
      record.setStreet(rs.getString("street"));
      record.setAddressLine2(rs.getString("address_line_2"));
      record.setAddressLine3(rs.getString("address_line_3"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setCountry(rs.getString("country"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setCounty(rs.getString("county"));
      record.setPhoneNumber(rs.getString("phone_number"));
      record.setEmail(rs.getString("email"));
      record.setCost(rs.getBigDecimal("cost"));
      record.setExpectedDate(rs.getTimestamp("expected_date"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      record.setExpirationDate(rs.getTimestamp("expiration_date"));
      record.setUrl(rs.getString("url"));
      record.setBarcode(rs.getString("barcode"));
      record.setKeywords(rs.getString("keywords"));
      record.setAssignedTo(DB.getLong(rs, "assigned_to", -1));
      record.setAssigned(rs.getTimestamp("assigned"));
      record.setImageUrl(rs.getString("image_url"));
      record.setCategoryId(DB.getLong(rs, "category_id", -1));
      record.setCustomFieldList(CustomFieldListJSONCommand.populateFromJSONString(rs.getString("field_values")));
      record.setArchivedBy(DB.getLong(rs, "archived_by", -1));
      record.setApprovedBy(DB.getLong(rs, "approved_by", -1));
      record.setApproved(rs.getTimestamp("approved"));
      record.setSource(rs.getString("source"));
      record.setDescription(rs.getString("description"));
      if (DB.hasColumn(rs, "highlight")) {
        record.setHighlight(rs.getString("highlight"));
      }
      // Populate categoryIdList
      List<ItemCategory> categoryList = ItemCategoryRepository.findAllByItemId(record.getId());
      if (categoryList != null) {
        List<Long> categoryIdList = new ArrayList<>();
        for (ItemCategory itemCategory : categoryList) {
          categoryIdList.add(itemCategory.getCategoryId());
        }
        record.setCategoryIdList(categoryIdList.toArray(new Long[0]));
      }
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
