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

package com.simisinc.platform.infrastructure.persistence.ecommerce;

import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Properties for querying objects from the order repository
 *
 * @author matt rajkowski
 * @created 6/10/19 7:39 PM
 */
public class OrderSpecification {

  private long id = -1L;
  private String uniqueId = null;
  private long customerId = -1;
  private String customerNumber = null;
  private String email = null;
  private String phoneNumber = null;
  private String name = null;
  private long createdBy = -1;
  private int showSandbox = DataConstants.UNDEFINED;
  private int showIncompleteOrders = DataConstants.UNDEFINED;
  private int showCanceledOrders = DataConstants.UNDEFINED;
  private int showProcessedOrders = DataConstants.UNDEFINED;
  private int showShippedOrders = DataConstants.UNDEFINED;

  public OrderSpecification() {
  }

  public OrderSpecification(long id) {
    this.id = id;
  }

  public OrderSpecification(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getCustomerNumber() {
    return customerNumber;
  }

  public void setCustomerNumber(String customerNumber) {
    this.customerNumber = customerNumber;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(long customerId) {
    this.customerId = customerId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public int getShowSandbox() {
    return showSandbox;
  }

  public void setShowSandbox(int showSandbox) {
    this.showSandbox = showSandbox;
  }

  public void setShowSandbox(boolean showSandbox) {
    this.showSandbox = (showSandbox ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getShowIncompleteOrders() {
    return showIncompleteOrders;
  }

  public void setShowIncompleteOrders(int showIncompleteOrders) {
    this.showIncompleteOrders = showIncompleteOrders;
  }

  public void setShowIncompleteOrders(boolean showIncompleteOrders) {
    this.showIncompleteOrders = (showIncompleteOrders ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getShowCanceledOrders() {
    return showCanceledOrders;
  }

  public void setShowCanceledOrders(int showCanceledOrders) {
    this.showCanceledOrders = showCanceledOrders;
  }

  public void setShowCanceledOrders(boolean showCanceledOrders) {
    this.showCanceledOrders = (showCanceledOrders ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getShowProcessedOrders() {
    return showProcessedOrders;
  }

  public void setShowProcessedOrders(int showProcessedOrders) {
    this.showProcessedOrders = showProcessedOrders;
  }

  public void setShowProcessedOrders(boolean showProcessedOrders) {
    this.showProcessedOrders = (showProcessedOrders ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getShowShippedOrders() {
    return showShippedOrders;
  }

  public void setShowShippedOrders(int showShippedOrders) {
    this.showShippedOrders = showShippedOrders;
  }

  public void setShowShippedOrders(boolean showShippedOrders) {
    this.showShippedOrders = (showShippedOrders ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
