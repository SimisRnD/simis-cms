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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.maps.CheckGeoPointCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;

import java.sql.Timestamp;

import static com.simisinc.platform.application.items.GenerateItemUniqueIdCommand.generateUniqueId;

/**
 * Validates and saves an item object
 *
 * @author matt rajkowski
 * @created 4/19/18 2:47 PM
 */
public class SaveItemCommand {

  public static final String allowedChars = "1234567890abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveItemCommand.class);

  public static Item saveItem(Item itemBean) throws DataException {

    Timestamp now = new Timestamp(System.currentTimeMillis());

    // Required dependencies
    if (itemBean.getCollectionId() == -1) {
      throw new DataException("A collection is required");
    }
    Collection collection = CollectionRepository.findById(itemBean.getCollectionId());
    if (collection == null) {
      throw new DataException("A collection is required");
    }
    if (itemBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(itemBean.getName())) {
      errorMessages.append("A name is required");
    }

    /*
    List<String> allowedPrivacyTypes = collection.getPrivacyTypesList();
    if (itemBean.getPrivacyType() == -1) {
      if (allowedPrivacyTypes.size() == 1) {
        // Set a default
        itemBean.setPrivacyType(PrivacyType.getTypeIdFromString(allowedPrivacyTypes.get(0)));
      } else {
        if (errorMessages.length() > 0) {
          errorMessages.append("\n");
        }
        errorMessages.append("A privacy option is required");
      }
    } else {
      if (!allowedPrivacyTypes.contains(PrivacyType.getStringFromTypeId(itemBean.getPrivacyType()))) {
        if (errorMessages.length() > 0) {
          errorMessages.append("\n");
        }
        errorMessages.append("A valid privacy option is required");
      }
    }
    */

    if (StringUtils.isNotBlank(itemBean.getUrl())) {
      // Format the URL
      String url = itemBean.getUrl().trim();
      if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "http://" + url;
      }
      // Validate the URL
      if (!UrlCommand.isUrlValid(url)) {
        if (errorMessages.length() > 0) {
          errorMessages.append("\n");
        }
        errorMessages.append("The URL does not look valid");
      } else {
        itemBean.setUrl(url);
      }
    }
    if (StringUtils.isNotBlank(itemBean.getEmail())) {
      EmailValidator emailValidator = EmailValidator.getInstance(false);
      if (!emailValidator.isValid(itemBean.getEmail())) {
        errorMessages.append("The email address looks incorrect");
      }
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Check geocoding
    itemBean = CheckGeoPointCommand.updateGeoPoint(itemBean);

    // Clean the content
    String cleanedContent = HtmlCommand.cleanContent(itemBean.getDescription());

    // Transform the fields and store...
    Item item;
    if (itemBean.getId() > -1) {
      // Update
      LOG.debug("Saving an existing record... ");
      item = ItemRepository.findById(itemBean.getId());
      if (item == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      // Insert
      LOG.debug("Saving a new record... ");
      item = new Item();
      // These values can be set on insert, but not update
      if (itemBean.getAssignedTo() > -1) {
        item.setAssignedTo(itemBean.getAssignedTo());
        item.setAssigned(now);
      }
      if (itemBean.getApprovedBy() > -1) {
        item.setApprovedBy(itemBean.getApprovedBy());
      }
      item.setApproved(itemBean.getApproved());
      item.setSource(itemBean.getSource());
      item.setCollectionId(itemBean.getCollectionId());
      item.setCreatedBy(itemBean.getCreatedBy());
    }
    // @note set the uniqueId before setting the name
    item.setUniqueId(generateUniqueId(item, itemBean));
    item.setModifiedBy(itemBean.getModifiedBy());
    item.setCategoryId(itemBean.getCategoryId());
    item.setCategoryIdList(itemBean.getCategoryIdList());
    item.setName(itemBean.getName());
    item.setSummary(itemBean.getSummary());
    item.setDescription(cleanedContent);
    item.setLatitude(itemBean.getLatitude());
    item.setLongitude(itemBean.getLongitude());
    item.setLocation(itemBean.getLocation());
    item.setStreet(itemBean.getStreet());
    item.setAddressLine2(itemBean.getAddressLine2());
    item.setAddressLine3(itemBean.getAddressLine3());
    item.setCity(itemBean.getCity());
    item.setState(itemBean.getState());
    item.setCountry(itemBean.getCountry());
    item.setPostalCode(itemBean.getPostalCode());
    item.setCounty(itemBean.getCounty());
    item.setPhoneNumber(ItemPhoneNumberCommand.format(itemBean.getPhoneNumber()));
    item.setEmail(itemBean.getEmail());
    item.setCost(itemBean.getCost());
    item.setExpectedDate(itemBean.getExpectedDate());
    item.setStartDate(itemBean.getStartDate());
    item.setEndDate(itemBean.getEndDate());
    item.setExpirationDate(itemBean.getExpirationDate());
    item.setUrl(itemBean.getUrl());
    item.setBarcode(itemBean.getBarcode());
    item.setKeywords(itemBean.getKeywords());
    item.setCustomFieldList(itemBean.getCustomFieldList());
    item.setIpAddress(itemBean.getIpAddress());
    return ItemRepository.save(item);
  }

  public static boolean saveBatchItem(Item item) {
    // @todo consider geocoding any addresses
    item.setUniqueId(generateUniqueId(null, item));
    Timestamp now = new Timestamp(System.currentTimeMillis());
    item.setApproved(now);
    if (item.getAssignedTo() > -1) {
      item.setAssigned(now);
    }
    return (ItemRepository.save(item) != null);
  }

}
