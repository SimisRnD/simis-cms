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
import com.simisinc.platform.application.items.ItemPhoneNumberCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.application.maps.CheckGeoPointCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.*;

/**
 * Converts the dataset row to a collection item
 *
 * @author matt rajkowski
 * @created 5/21/18 12:53 PM
 */
public class SaveDatasetRowCommand {

  private static Log LOG = LogFactory.getLog(SaveDatasetRowCommand.class);

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
        // Options which skip the record
        if (isSkipped(options, value)) {
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
      // Skip empty values
      if (value.length() == 0) {
        continue;
      }
      // See if there is a field mapping
      if (i >= fieldMappings.size()) {
        continue;
      }
      String mapping = fieldMappings.get(i);
      if (StringUtils.isBlank(mapping)) {
        continue;
      }
      // Set the item value
      if ("name".equals(mapping)) {
        if (item.getName() == null) {
          item.setName(value);
        } else if (!item.getName().equals(value)) {
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
        // Build a summary if multiple fields have the same mapping
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
        // @todo
      } else if ("endDate".equals(mapping)) {
        // @todo
      } else if ("expectedDate".equals(mapping)) {
        // @todo
      } else if ("expirationDate".equals(mapping)) {
        // @todo
      } else if ("url".equals(mapping)) {
        item.setUrl(value);
      } else if ("imageUrl".equals(mapping)) {
        item.setImageUrl(value);
      } else if ("barcode".equals(mapping)) {
        item.setBarcode(value);
      } else if ("assignedTo".equals(mapping)) {
        // @todo
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
    // Restrict the item name length
    if (item.getName() != null && item.getName().length() > 250) {
      item.setName(item.getName().substring(0, 250));
    }
    return item;
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
