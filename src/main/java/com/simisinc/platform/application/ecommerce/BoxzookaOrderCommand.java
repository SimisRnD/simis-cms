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

package com.simisinc.platform.application.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands for working with Boxzooka
 *
 * @author matt rajkowski
 * @created 11/7/19 7:36 PM
 */
public class BoxzookaOrderCommand {

  private static Log LOG = LogFactory.getLog(BoxzookaOrderCommand.class);

  public static boolean postNewOrder(Order order) throws DataException {

    LOG.debug("postNewOrder() called...");

    if (order == null) {
      LOG.warn("Order is null");
      return false;
    }

    if (order.getProcessed()) {
      LOG.debug("Order is already processed");
      return true;
    }

    // Verify the service is enabled
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (!"Boxzooka".equalsIgnoreCase(service)) {
      LOG.warn("Boxzooka is not configured");
      return false;
    }

    // Use the fulfillment option
    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);

    // @todo make sure 2-Letter ISO codes are used for country (state is ok)
    String country = order.getShippingAddress().getCountry();
    String countryISO = country;
    if (countryISO.equalsIgnoreCase("United States")) {
      countryISO = "US";
    }
    String stateISO = order.getShippingAddress().getState();

    // @todo Show the shipping method
    ShippingMethod shippingMethod = ShippingMethodRepository.findById(order.getShippingMethodId());
    String method = shippingMethod.getBoxzookaCode();

    // @todo the shipping equivalent will be stored in the database entry

    // BXZ.PKP">Pack and Hold
    // BXZ.SAMEDAY.NYC">BXZ Same Day
    // UPS.EXP.1">UPS Next Day Air Early(Next Business Day by 10AM)
    // UPS.DOM.1">UPS Next Day Air(Next Business Day by 2pm)
    // UPS.DOM.2">UPS Second Day Air(Second Business Day by 2pm)
    // UPS.DOM.3">UPS 3-Day Air (Third Business Day by 2pm)
    // UPS.GRD.RESI">UPS Ground (1 - 5 Business Day)
    // FDX.EXP.1">FedEx Next Day Air Early(Next Business Day by 10AM)
    // FDX.DOM.1">FedEx Next Day Air(Next Business Day by 2pm)
    // FDX.DOM.2">FedEx Second Day Air(Second Business Day by 2pm)
    // FDX.DOM.3">FedEx 3-Day Air (Third Business Day by 2pm)
    // FDX.GRD">FedEx Ground (1 - 5 Business Day)
    // FDX.HOME">FedEx Home
    // SMARTPOST">FedEx SmartPost
    // DHLEC.SAMEDAY">DHL Same Day
    // DHLEC.MAX">3-Day Priority (Usually Delivered W/IN 3 Calendar Days)
    // DHLEC.STD.GRD">Standard Ground Shipping (2- 7 Calendar Days)
    // USPS.PRIORITY">USPS Priority
    // USPS.PARCEL">USPS ParcelSelect
    // USPS.FIRST">USPS First
    // USPS.EXPRESS">USPS Express


    // Create the record to send to Boxzooka
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("order_key", BoxzookaApiClientCommand.externalOrderIdFromOrder(order));
    params.put("external_id", order.getUniqueId());
    params.put("warehouse_id", 1);
    if (StringUtils.isNotBlank(method)) {
      params.put("method", method);
    } else {
      LOG.warn("A Boxzooka method was not found");
    }
    params.put("order_value", order.getTotalAmount());
    params.put("order_type", "retail");
//    params.put("order_type", "whole-sale");
//    params.put("shipping_type", "domestic");
//    params.put("shipping_type", "international");
//    params.put("carrier_account", "");
//    params.put("slip_note", "");
//    params.put("ship_date", "");
//    params.put("cancel_date", "");

    Map<String, Object> address = new LinkedHashMap<>();
    address.put("first_name", order.getShippingAddress().getFirstName());
    address.put("last_name", order.getShippingAddress().getLastName());
    address.put("company", order.getShippingAddress().getOrganization());
    address.put("address1", order.getShippingAddress().getStreet());
    address.put("address2", order.getShippingAddress().getAddressLine2());
    address.put("city", order.getShippingAddress().getCity());
    address.put("province", stateISO);
    address.put("zip", order.getShippingAddress().getPostalCode());
    address.put("country", countryISO);
    address.put("phone", order.getShippingAddress().getPhoneNumber());
    address.put("email", order.getEmail());
    params.put("address", address);

    // Determine the order items
    boolean fullyProcessed = true;
    Map<String, Map<String, Object>> orderItemMap = new LinkedHashMap<>();
    Map<String, Object> itemMap = new LinkedHashMap<>();
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    List<OrderItem> processedOrderItemList = new ArrayList<>();
    int count = -1;
    for (OrderItem orderItem : orderItemList) {
      // Ignore previously processed items
      if (orderItem.getProcessed()) {
        LOG.debug("Order item is already processed");
        continue;
      }
      // Only need those fulfilled by Boxzooka or not specified
      Product product = ProductRepository.findById(orderItem.getProductId());
      if (!FulfillmentOptionCommand.canBeFulfilledBy(boxzookaFulfillmentOption, product)) {
        fullyProcessed = false;
        LOG.debug("Order item is not a Boxzooka item");
        continue;
      }

      // Use Boxzooka values
      Integer quantityValue = orderItem.getQuantity().intValue();
      BigDecimal itemAmountValue = orderItem.getEachAmount().setScale(2, RoundingMode.HALF_EVEN);

      // See if the SKU is already added, then adjust it since duplicates are not allowed
      if (orderItemMap.containsKey(orderItem.getProductSku())) {
        // Update the object
        Map<String, Object> orderItemObject = orderItemMap.get(orderItem.getProductSku());
        // Update the quantity
        quantityValue = quantityValue + ((Integer) orderItemObject.get("quantity"));
        orderItemObject.put("quantity", quantityValue);
        // Update the amount
        BigDecimal existingValue = (BigDecimal) orderItemObject.get("value");
        BigDecimal newValue = existingValue.add(itemAmountValue);
        orderItemObject.put("value", newValue);
      } else {
        // Create the object
        ++count;
        Map<String, Object> orderItemObject = new LinkedHashMap<>();
        orderItemObject.put("sku", orderItem.getProductSku());
//      orderItem.put("upc", cartItem.getProductBarcode());
        orderItemObject.put("name", orderItem.getProductName());
        // @todo use the weight
        orderItemObject.put("weight", 0);
        orderItemObject.put("quantity", quantityValue);
        orderItemObject.put("value", itemAmountValue);
        itemMap.put(String.valueOf(count), orderItemObject);
        orderItemMap.put(orderItem.getProductSku(), orderItemObject);
      }
      // Append to the processed list for later
      processedOrderItemList.add(orderItem);
    }
    params.put("item", itemMap);

    // There are no cart items to send to Boxzooka
    if (count == -1) {
      return false;
    }

    String data = JsonCommand.createJsonNode(params).toString();
    LOG.debug("JSON STRING:\n" + data);

    try {
      LOG.debug("Data: " + data);
      JsonNode json = BoxzookaApiClientCommand.sendBoxzookaHttpPost("/v1/order", data);
      if (json == null) {
        throw new DataException("The order could not be sent");
      }

      // {"success":"order created in system"}
      if (json.has("success")) {
        // Update each order item's status
        for (OrderItem orderItem : processedOrderItemList) {
          OrderItemRepository.markStatusAsPreparing(orderItem);
        }
        // If the order is complete, update the status
        if (fullyProcessed) {
          OrderRepository.markStatusAsPreparing(order);
        } else {
          OrderRepository.markStatusAsPartiallyPrepared(order);
        }
        return true;
      }

      // {"Error":"required field is empty","message":{"item.0.weight":["The item.0.weight field is required."]}}
      if (json.has("Error")) {
        if (json.has("message")) {
          throw new DataException("Boxzooka error: " + json.get("Error").asText() + "; " + json.get("message").asText());
        } else {
          throw new DataException("Boxzooka error: " + json.get("Error").asText());
        }
      }
    } catch (DataException de) {
      LOG.error("Exception when calling BXZ-Api#newOrder", de);
      throw new DataException(de.getMessage());
    } catch (Exception e) {
      LOG.error("Exception when calling BXZ-Api#newOrder", e);
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }
    return false;
  }

  public static boolean cancelOrder(Order order) throws DataException {

    LOG.debug("cancelOrder() called...");

    // Verify the service is enabled
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (!"Boxzooka".equalsIgnoreCase(service)) {
      LOG.warn("Boxzooka is not configured");
      return false;
    }

    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);
    if (!FulfillmentOptionCommand.canBePartiallyFulfilledBy(boxzookaFulfillmentOption, order)) {
      LOG.warn("This is not a Boxzooka order");
      return false;
    }

    try {
      JsonNode json = BoxzookaApiClientCommand.sendBoxzookaHttpDelete("/v1/order/" + BoxzookaApiClientCommand.externalOrderIdFromOrder(order));
      if (json == null) {
        throw new DataException("The request could not be sent");
      }

      // {"success":"order cancelled in system"}
      if (json.has("success")) {
        return true;
      }

      // {"Error":"order did not find or already cancelled"}
      if (json.has("Error")) {
        String errorMessage = json.get("Error").asText();
        if ("order did not find or already cancelled".equals(errorMessage)) {
          return true;
        }
        // Not sure of this error...
        throw new DataException("Boxzooka error: " + json.get("Error").asText());
      }
    } catch (DataException de) {
      LOG.error("Exception when calling BXZ-Api#cancelOrderByOrderKey", de);
      throw new DataException(de.getMessage());
    } catch (Exception e) {
      LOG.error("Exception when calling BXZ-Api#cancelOrderByOrderKey", e);
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }
    return false;
  }
}
