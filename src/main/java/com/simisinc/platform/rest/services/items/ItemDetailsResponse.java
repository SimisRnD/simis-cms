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

package com.simisinc.platform.rest.services.items;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simisinc.platform.domain.model.items.Item;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class ItemDetailsResponse {

  String uniqueId;
  String name;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  String summary;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  String street;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  String city;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  String state;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  String postalCode;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Double latitude;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Double longitude;

  public ItemDetailsResponse(Item record) {
    uniqueId = record.getUniqueId();
    name = record.getName();
    summary = record.getSummary();
    street = record.getStreet();
    city = record.getCity();
    state = record.getState();
    postalCode = record.getPostalCode();
    if (record.hasGeoPoint()) {
      latitude = record.getLatitude();
      longitude = record.getLongitude();
    }
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public String getName() {
    return name;
  }

  public String getSummary() {
    return summary;
  }

  public String getStreet() {
    return street;
  }

  public String getCity() {
    return city;
  }

  public String getState() {
    return state;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public Double getLatitude() {
    return latitude;
  }

  public Double getLongitude() {
    return longitude;
  }
}
