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

package com.simisinc.platform.application.datasets;

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.items.GenerateCategoryUniqueIdCommand;
import com.simisinc.platform.application.items.ItemPhoneNumberCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.application.maps.CheckGeoPointCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.*;

/**
 * Converts the dataset row to a collection item
 *
 * @author matt rajkowski
 * @created 5/21/18 12:53 PM
 */
public class SaveDatasetRowCommand {

  // @column items.name
  private static final int MAX_ITEM_NAME_LENGTH = 255;

  private static Log LOG = LogFactory.getLog(SaveDatasetRowCommand.class);

  // Bug fix: a real "Save & Sync" run streams one row at a time into saveRecord()/constructItem()
  // (from a CSV/TSV parser callback, or a per-row loop over already-loaded JSON/RSS/GeoJSON
  // records), so nothing here previously carried a value forward from one row's isSkipped() call
  // to the next. That's why the "skipDuplicates" field option -- which only works by remembering
  // values already seen -- was a no-op during a real sync, even though Preview (LoadCSVRowsCommand,
  // LoadJsonCommand, LoadTSVRowsCommand) already builds exactly this kind of map and correctly
  // reports duplicates would be dropped. This map plays the same role for a real sync, just
  // spanning the whole file (one entry per in-progress dataset id) instead of a single load call.
  // DatasetFileCommand.convertFileToCollection() clears a dataset's entry via
  // clearDuplicateTracking() once the whole file has been converted, so a later run starts clean.
  private static final Map<Long, Map<String, String>> uniqueColumnValueMapByDatasetId = new ConcurrentHashMap<>();

  private static Map<String, String> duplicateTrackingMapFor(Dataset dataset) {
    // Must be a thread-safe map: two syncs of the same dataset id (e.g. a manual "Save & Sync"
    // racing an already-running scheduled sync) share this same instance via computeIfAbsent, and
    // concurrent structural modification of a plain HashMap is undefined behavior -- including a
    // documented failure mode of an infinite loop / CPU-pegged worker thread during a concurrent
    // resize. A ConcurrentHashMap can't corrupt itself that way.
    return uniqueColumnValueMapByDatasetId.computeIfAbsent(dataset.getId(), id -> new ConcurrentHashMap<>());
  }

  /**
   * Clears the running skipDuplicates tracking state built up for this dataset over the course
   * of one real sync run. Must be called once the dataset's file has been fully converted
   * (success or failure) so a later sync starts from a clean slate rather than treating a value
   * from a previous run as a repeat.
   */
  public static void clearDuplicateTracking(Dataset dataset) {
    uniqueColumnValueMapByDatasetId.remove(dataset.getId());
  }

  public static boolean saveRecord(String[] row, Dataset dataset, Collection collection) {

    Item item = null;
    Item previousItem = null;

    // Compute these...
    List<String> columnNames = dataset.getColumnNamesList();
    List<String> fieldTitles = dataset.getFieldTitlesList();
    List<String> fieldMappings = dataset.getFieldMappingsList();
    List<String> fieldOptions = dataset.getFieldOptionsList();

    // If the dataset specifies a unique column name, then find the value
    String datasetKeyValue = null;
    if (StringUtils.isNotBlank(dataset.getUniqueColumnName())) {
      // Scan the columns for the unique name, then retrieve the row value
      for (int i = 0; i < columnNames.size(); i++) {
        String columnName = columnNames.get(i);
        if (columnName.equals(dataset.getUniqueColumnName())) {
          datasetKeyValue = row[i];
          // Now try to load the previous item
          item = ItemRepository.findByDatasetKeyValue(datasetKeyValue, dataset.getId());
          if (item != null) {
            previousItem = ItemRepository.findById(item.getId());
          }
          break;
        }
      }
    }
    if (item == null) {
      item = new Item();
      item.setDatasetKeyValue(datasetKeyValue);
      // Issue #815 follow-up: this path (a brand new item, not matched to an existing one by the
      // dataset's unique column) calls SaveItemCommand.saveBatchItem -> ItemRepository.save()
      // directly, bypassing saveItem()'s insert-only itemOrder copy. Without this, every new row
      // a dataset sync inserts would fall back to the Item domain model's static default (100),
      // colliding with (or sorting ahead of) real, already-ordered items.
      item.setItemOrder(ItemRepository.getNextItemOrder(collection.getId()));
    }
    item = constructItem(item, row, dataset, collection, columnNames, fieldTitles, fieldMappings, fieldOptions);
    if (item != null) {
      updateGeoPoint(item);
      return SaveItemCommand.saveBatchItem(previousItem, item);
    }
    // It was skipped on purpose
    return true;
  }

  public static Item constructItem(Item item, String[] row, Dataset dataset, Collection collection,
      List<String> columnNames, List<String> fieldTitles, List<String> fieldMappings, List<String> fieldOptions) {

    // Values from the dataset
    item.setDatasetId(dataset.getId());
    item.setDatasetSyncDate(dataset.getSyncDate());
    item.setCollectionId(collection.getId());
    item.setCreatedBy(dataset.getModifiedBy());
    item.setModifiedBy(dataset.getModifiedBy());

    List<String> foundFields = new ArrayList<>();
    List<Long> categoryIdList = new ArrayList<>();
    boolean hasSplitOption = false;
    String splitValue = null;
    // Shared across every row in this sync run, so skipDuplicates recognizes a value repeated
    // across separate saveRecord() calls, not just within this one row (see field comment above)
    Map<String, String> uniqueColumnValueMap = duplicateTrackingMapFor(dataset);

    for (int i = 0; i < fieldMappings.size(); i++) {
      if (row.length == i) {
        continue;
      }
      // Simplify the value
      String value = row[i];
      if (value == null || value.equalsIgnoreCase("null")) {
        value = "";
      } else {
        value = value.trim();
      }
      // Apply options to the field's value
      if (i < fieldOptions.size()) {
        String options = fieldOptions.get(i);
        // Options which skip the record -- pass the dataset's running unique-value map (rather
        // than the no-arg overload, which always checks with null/-1 and can never detect a
        // duplicate) so skipDuplicates is honored the same way Preview already honors it
        if (isSkipped(options, value, uniqueColumnValueMap, i)) {
          // Skip the record
          return null;
        }
        // Options which update the value
        value = applyOptionsToField(options, value);
        hasSplitOption = options.contains("split(");
        if (hasSplitOption) {
          splitValue = extractValue(options, "split");
        }
      }
      // See if there is a field mapping
      if (i >= fieldMappings.size()) {
        continue;
      }
      String mapping = fieldMappings.get(i);
      if (StringUtils.isBlank(mapping)) {
        continue;
      }
      // Skip empty values, but allow custom method to manage empty values
      if (value.length() == 0 && !"custom".equals(mapping)) {
        continue;
      }
      // Set the item value
      if ("name".equals(mapping)) {
        // Append value if multiple fields have the same mapping
        if (!foundFields.contains("name")) {
          foundFields.add("name");
          item.setName(value);
        } else {
          item.setName(item.getName() + " " + value);
        }
      } else if ("category".equals(mapping)) {
        String[] categories = new String[] { value };
        if (hasSplitOption) {
          if (splitValue != null) {
            categories = value.split(Pattern.quote(splitValue));
          } else if (value.contains(";")) {
            categories = value.split(Pattern.quote(";"));
          } else if (value.contains(",")) {
            categories = value.split(Pattern.quote(","));
          }
        }
        for (String categoryText : categories) {
          if (StringUtils.isBlank(categoryText)) {
            continue;
          }
          // Make sure the category exists
          Category category = CategoryRepository.findByNameWithinCollection(categoryText.trim(), collection.getId());
          if (category == null) {
            category = new Category();
            category.setCollectionId(collection.getId());
            category.setName(categoryText.trim());
            // @note set the uniqueId after setting the name since it's based on the name
            category.setUniqueId(GenerateCategoryUniqueIdCommand.generateUniqueId(category, category));
            category.setCreatedBy(dataset.getModifiedBy());
            category = CategoryRepository.save(category);
          }
          // Set the primary category
          if (item.getCategoryId() == -1) {
            item.setCategoryId(category.getId());
          }
          if (!categoryIdList.contains(category.getId())) {
            categoryIdList.add(category.getId());
          }
        }
      } else if ("summary".equals(mapping)) {
        // Append value if multiple fields have the same mapping
        if (!foundFields.contains("summary")) {
          foundFields.add("summary");
          item.setSummary(value);
        } else {
          item.setSummary(item.getSummary() + ", " + value);
        }
      } else if ("description".equals(mapping)) {
        // Clean the content
        String cleanedContent = HtmlCommand.cleanContent(value);
        item.setDescription(cleanedContent);
      } else if ("textDescription".equals(mapping)) {
        item.setDescription(value);
      } else if ("geopoint".equals(mapping)) {
        // [lon, lat]
        if (value.contains(",")) {
          String longitude = value.substring(0, value.indexOf(",")).trim();
          if (longitude.startsWith("[")) {
            longitude = longitude.substring(1);
          }
          String latitude = value.substring(value.indexOf(",") + 1).trim();
          if (latitude.endsWith("]")) {
            latitude = latitude.substring(0, latitude.indexOf("]"));
          }
          if (longitude.length() > 0 && latitude.length() > 0) {
            item.setLatitude(Double.valueOf(latitude));
            item.setLongitude(Double.valueOf(longitude));
          }
        }
      } else if ("keywords".equals(mapping)) {
        item.setKeywords(value);
      } else if ("latitude".equals(mapping)) {
        item.setLatitude(Double.valueOf(value));
      } else if ("longitude".equals(mapping)) {
        item.setLongitude(Double.valueOf(value));
      } else if ("location".equals(mapping)) {
        item.setLocation(value);
      } else if ("street".equals(mapping)) {
        item.setStreet(value);
      } else if ("addressLine2".equals(mapping)) {
        item.setAddressLine2(value);
      } else if ("addressLine3".equals(mapping)) {
        item.setAddressLine3(value);
      } else if ("city".equals(mapping)) {
        item.setCity(value);
      } else if ("state".equals(mapping)) {
        item.setState(value);
      } else if ("postalCode".equals(mapping)) {
        if (value.length() > 1) {
          while (value.length() < 5) {
            value = "0" + value;
          }
        }
        item.setPostalCode(value);
      } else if ("country".equals(mapping)) {
        item.setCountry(value);
      } else if ("county".equals(mapping)) {
        item.setCounty(value);
      } else if ("phoneNumber".equals(mapping)) {
        item.setPhoneNumber(ItemPhoneNumberCommand.format(value));
      } else if ("email".equals(mapping)) {
        item.setEmail(value);
      } else if ("cost".equals(mapping)) {
        item.setCost(new BigDecimal(value));
      } else if ("startDate".equals(mapping)) {
        Timestamp startDate = parseTimestamp(value);
        if (startDate != null) {
          item.setStartDate(startDate);
        }
      } else if ("endDate".equals(mapping)) {
        Timestamp endDate = parseTimestamp(value);
        if (endDate != null) {
          item.setEndDate(endDate);
        }
      } else if ("expectedDate".equals(mapping)) {
        Timestamp expectedDate = parseTimestamp(value);
        if (expectedDate != null) {
          item.setExpectedDate(expectedDate);
        }
      } else if ("expirationDate".equals(mapping)) {
        Timestamp expirationDate = parseTimestamp(value);
        if (expirationDate != null) {
          item.setExpirationDate(expirationDate);
        }
      } else if ("url".equals(mapping)) {
        item.setUrl(value);
      } else if ("imageUrl".equals(mapping)) {
        item.setImageUrl(value);
      } else if ("barcode".equals(mapping)) {
        item.setBarcode(value);
      } else if ("assignedTo".equals(mapping)) {
        long assignedToUserId = resolveAssignedToUserId(value);
        if (assignedToUserId > -1) {
          item.setAssignedTo(assignedToUserId);
        }
      } else if ("custom".equals(mapping)) {
        String columnName = columnNames.get(i);
        String title = fieldTitles.get(i);
        if (StringUtils.isNotBlank(title)) {
          columnName = title;
        }
        CustomField customField = new CustomField(columnName, columnName, value);
        item.addCustomField(customField);
      }
    }
    item.setCategoryIdList(categoryIdList.toArray(new Long[0]));
    // Restrict the item name length. Truncating is right here and only here: this is a dataset
    // import with no user at the keyboard to tell, so refusing the row would lose more than
    // shortening the name does. It was truncating to 250 against a VARCHAR(255) column, though,
    // discarding five characters it never needed to (issue #1740).
    if (item.getName() != null && item.getName().length() > MAX_ITEM_NAME_LENGTH) {
      item.setName(item.getName().substring(0, MAX_ITEM_NAME_LENGTH));
    }
    return item;
  }

  /**
   * Parses a dataset cell into a Timestamp, trying the date/time formats that dataset
   * exports commonly use. Datasets carry no declared source format, so a set of patterns
   * is attempted strictly (to avoid silently accepting a nonsense date). A value matching
   * none is logged and skipped rather than stored wrong -- importing a wrong date is worse
   * than importing none, which is the silent data loss this replaces. Parsing is naive
   * (any zone designator is treated as a literal); fidelity beyond the stored value is out
   * of scope here.
   *
   * @param value the trimmed cell value (never blank here -- blanks are skipped earlier)
   * @return the parsed Timestamp, or null if the value could not be parsed
   */
  protected static Timestamp parseTimestamp(String value) {
    try {
      java.util.Date date = DateUtils.parseDateStrictly(value,
          "yyyy-MM-dd'T'HH:mm:ss'Z'",
          "yyyy-MM-dd'T'HH:mm:ssXXX",
          "yyyy-MM-dd'T'HH:mm:ss",
          "yyyy-MM-dd HH:mm:ss",
          "yyyy-MM-dd",
          "MM/dd/yyyy HH:mm:ss",
          "MM/dd/yyyy",
          "yyyy/MM/dd");
      return new Timestamp(date.getTime());
    } catch (ParseException e) {
      LOG.warn("Dataset date value could not be parsed, skipping: '" + value + "'");
      return null;
    }
  }

  /**
   * Resolves a dataset cell to a user id for an "assignedTo" mapping. The item form labels
   * this field a user name, so the value is looked up as a username. An unrecognized user
   * is logged and skipped (returns -1) rather than guessed at or stored as a bogus id --
   * assigning an item to the wrong user is worse than leaving it unassigned. (Resolving by
   * email or numeric id could be added if a dataset needs it; username is the existing
   * convention.)
   *
   * @param value the trimmed cell value (never blank here -- blanks are skipped earlier)
   * @return the matching user id, or -1 if no user matches
   */
  protected static long resolveAssignedToUserId(String value) {
    User user = UserRepository.findByUsername(value);
    if (user == null) {
      LOG.warn("Dataset assignedTo value did not match a username, skipping: '" + value + "'");
      return -1;
    }
    return user.getId();
  }

  private static void updateGeoPoint(Item item) {
    // Before saving, consider using the World Cities geocode
    if (item.hasGeoPoint()) {
      return;
    }
    // Use a geocoder
    CheckGeoPointCommand.updateGeoPoint(item);
    // In addition to CheckGeoPoint, consider using the Zip/World Cities geocode
    if (!item.hasGeoPoint()) {
      CheckGeoPointCommand.updateGeoPointByRelativeLocation(item);
    }
  }
}
