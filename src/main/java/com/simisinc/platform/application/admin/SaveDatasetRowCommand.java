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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.items.ItemPhoneNumberCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.application.maps.CheckGeoPointCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCustomField;
import com.simisinc.platform.domain.model.maps.WorldCity;
import com.simisinc.platform.domain.model.maps.ZipCode;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.maps.WorldCityRepository;
import com.simisinc.platform.infrastructure.persistence.maps.ZipCodeRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.WordUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/21/18 12:53 PM
 */
public class SaveDatasetRowCommand {

  private static Log LOG = LogFactory.getLog(SaveDatasetRowCommand.class);

  public static boolean saveRecord(String[] row, Dataset dataset, Collection collection, List<String> fieldMappings, List<String> columnNames, List<String> fieldOptions) {
    Item item = new Item();
    item.setDatasetId(dataset.getId());
    item.setCollectionId(collection.getId());
    item.setCreatedBy(dataset.getModifiedBy());
    item.setModifiedBy(dataset.getModifiedBy());
    List<Long> categoryIdList = new ArrayList<>();
    boolean hasSplitOption = false;
    String splitValue = null;

    for (int i = 0; i < fieldMappings.size(); i++) {
      if (row.length == i) {
        continue;
      }
      String value = row[i];
      if (value == null) {
        // Skip this field
        continue;
      }
      value = value.trim();
      if (value.length() == 0 || "null".equalsIgnoreCase(value)) {
        // Skip this field
        continue;
      }
      // Check for any options
      if (i < fieldOptions.size()) {
        String options = fieldOptions.get(i);
        if (StringUtils.isNotBlank(options)) {
          // Check for an equals("") value (value must equal this value to be valid)
          String equalsValue = extractValue(options, "equals");
          if (equalsValue != null && !value.equalsIgnoreCase(equalsValue)) {
            // Skip the record
            return true;
          }
          // Check for a contains("") value (value must contain this value to be valid)
          String containsValue = extractValue(options, "contains");
          if (containsValue != null && !value.toLowerCase().contains(containsValue.toLowerCase())) {
            // Skip the record
            return true;
          }
          // Other checks
          if (options.contains("caps") || options.contains("capitalize")) {
            value = WordUtils.capitalizeFully(value, ' ', '.', '-', '/', '\'');
            value = value.replaceAll(" And ", " and ");
          } else if (options.contains("uppercase")) {
            value = StringUtils.upperCase(value);
          } else if (options.contains("lowercase")) {
            value = StringUtils.lowerCase(value);
          }
          hasSplitOption = options.contains("split(");
          if (hasSplitOption) {
            splitValue = extractValue(options, "split");
          }
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
      if ("name".equals(mapping)) {
        if (item.getName() == null) {
          item.setName(value);
        } else if (!item.getName().equals(value)) {
          item.setName(item.getName() + " " + value);
        }
      } else if ("category".equals(mapping)) {
        String[] categories = new String[]{value};
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
        if (StringUtils.isBlank(item.getSummary())) {
          item.setSummary(value);
        } else {
          item.setSummary(item.getSummary() + ", " + value);
        }
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
        ItemCustomField customField = new ItemCustomField(columnName, columnName, value);
        item.addCustomField(customField);
      }
    }
    item.setCategoryIdList(categoryIdList.toArray(new Long[0]));
    // Restrict the item name length
    if (item.getName().length() > 250) {
      item.setName(item.getName().substring(0, 250));
    }
    // Duplicate check if requested
    if (dataset.isSkipDuplicateNames()) {
      Item possibleDuplicate = ItemRepository.findByNameWithinCollection(item.getName(), collection.getId());
      if (possibleDuplicate != null) {
        LOG.debug("Found duplicate: " + item.getName());
        return true;
      }
    }
    // Before saving, consider using the World Cities geocode
    if (!item.hasGeoPoint()) {
      // Use a geocoder
      if (StringUtils.isNotBlank(item.getStreet()) && StringUtils.isNotBlank(item.getCity()) && StringUtils.isNotBlank(item.getState())) {
        Item geoPoint = CheckGeoPointCommand.updateGeoPoint(item);
        if (geoPoint.hasGeoPoint()) {
          item.setLatitude(geoPoint.getLatitude());
          item.setLongitude(geoPoint.getLongitude());
        }
      }
      // Consider using the World Cities geocode
      if (!item.hasGeoPoint() && StringUtils.isNotBlank(item.getPostalCode())) {
        // Find a zipcode geopoint, to estimate the lat/long
        ZipCode zipCode = ZipCodeRepository.findByCode(item.getPostalCode());
        if (zipCode != null && zipCode.hasGeoPoint()) {
          item.setLatitude(zipCode.getLatitude());
          item.setLongitude(zipCode.getLongitude());
        }
      }
      if (!item.hasGeoPoint() && StringUtils.isNotBlank(item.getCity()) && StringUtils.isNotBlank(item.getState())) {
        String region = item.getState();
        String country = "us";
        if (item.getCountry() != null && !"united states".equalsIgnoreCase(item.getCountry())) {
          // Leave blank or convert to 2-digit value
          region = null;
          country = null;
        }
        // Find a world cities geopoint based on country (US for now), to estimate the lat/long
        WorldCity worldCity = WorldCityRepository.findByCityRegionCountry(item.getCity(), region, country);
        if (worldCity != null) {
          item.setLatitude(worldCity.getLatitude());
          item.setLongitude(worldCity.getLongitude());
        }
      }
    }
    return SaveItemCommand.saveBatchItem(item);
  }

  public static boolean hasOption(String options, String term) {
    return options.contains(term);
  }

  public static String extractValue(String options, String term) {
    int startIdx = options.indexOf(term + "(");
    if (startIdx == -1) {
      return null;
    }
    int endIdx = options.indexOf(")", startIdx);
    if (endIdx == -1) {
      return null;
    }
    String extractedValue = options.substring(startIdx + term.length() + 1, endIdx);
    if (extractedValue.startsWith("\"") && extractedValue.indexOf("\"") != extractedValue.lastIndexOf("\"")) {
      return extractedValue.substring(1, extractedValue.length() - 1);
    } else {
      return null;
    }
  }
}
