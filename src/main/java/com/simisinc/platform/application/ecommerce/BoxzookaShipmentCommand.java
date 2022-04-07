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
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.ShippingCarrier;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCarrierRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Commands for working with Boxzooka
 *
 * @author matt rajkowski
 * @created 11/18/19 10:58 PM
 */
public class BoxzookaShipmentCommand {

  private static Log LOG = LogFactory.getLog(BoxzookaShipmentCommand.class);

  public static ShippingCarrier getShippingCarrier(ShippingMethod shippingMethod) {
    if (shippingMethod != null && StringUtils.isNotBlank(shippingMethod.getBoxzookaCode())) {
      String shippingCode = shippingMethod.getBoxzookaCode();
      if (shippingCode.startsWith("FDX.")) {
        shippingCode = "FEDEX";
      } else if (shippingCode.startsWith("UPS.")) {
        shippingCode = "UPS";
      } else if (shippingCode.startsWith("USPS.")) {
        shippingCode = "USPS";
      } else if (shippingCode.startsWith("DHLEC.")) {
        shippingCode = "DHL";
      }
      return ShippingCarrierRepository.findByCode(shippingCode);
    }
    return null;
  }

  public static List<TrackingNumber> retrieveTrackingNumbers(Order order) throws DataException {

    LOG.debug("updateTrackingNumbers() called...");

    // Verify the service is enabled
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (!"Boxzooka".equalsIgnoreCase(service)) {
      LOG.warn("Boxzooka is not configured");
      return null;
    }

    // @todo Determine the carrier, tracking, and ship date/time

    try {
      JsonNode json = BoxzookaApiClientCommand.sendBoxzookaHttpGet("/v1/shipment/" + BoxzookaApiClientCommand.externalOrderIdFromOrder(order));
      if (json == null) {
        throw new DataException("The request could not be sent");
      }

      // Two versions, one documented, the other from using the API
      if (json.has("success")) {
        JsonNode success = json.get("success");
        // Determine the tracking number(s)
        List<TrackingNumber> trackingNumberList = new ArrayList<>();
        if (success.has("shipment")) {
          JsonNode shipment = success.get("shipment");
          // Determine the JSON structure (there are two versions)
          if (shipment.has("tracking")) {
            // The API says this should be the response (must be old method supporting a single shipment)
            // {
            //  "success": {
            //    "shipment": {
            //      "order_key": "139480",
            //      "carrier": "UPS",
            //      "external_id": "995061039202",
            //      "shipped_product": {
            //        "BDBC-QN-01": {
            //          "sku": "BDBC-QN-01",
            //          "quantity": 1,
            //          "item_name": "Try A Breeze Comforter"
            //        }
            //      },
            //      "tracking": [
            //        "1Z6A76A90342463991"
            //      ]
            //    }
            //  }
            //}
            // Determine the carrier
            long carrierId = -1;
            if (shipment.has("carrier")) {
              JsonNode carrierNode = shipment.get("carrier");
              String carrier = carrierNode.asText();
              ShippingCarrier shippingCarrier = ShippingCarrierRepository.findByCode(carrier);
              if (shippingCarrier != null) {
                carrierId = shippingCarrier.getId();
              } else {
                LOG.warn("Boxzooka carrier code not found: " + carrier);
              }
            }
            if (shipment.has("shipped_product")) {
              JsonNode shippedProductNode = shipment.get("shipped_product");
              // Get the first entry, or look for 'sku'

            }
            JsonNode trackingArray = shipment.get("tracking");
            if (trackingArray.isArray()) {
              for (JsonNode trackingNumberNode : trackingArray) {
                TrackingNumber trackingNumber = generateTrackingNumber(order.getId(), carrierId, null, trackingNumberNode.asText());
                trackingNumberList.add(trackingNumber);
              }
            }
          } else if (shipment.has("shipments")) {
            // The Sandbox is returning this (must be new method supporting multiple shipments)
            // {"success":{
            // "shipment":{
            //  "order_key":"139480",
            //  "external_id":"995061039202",
            //  "shipments":{
            //    "1Z6A76A90342463991":{
            //      "carrier":"UPS",
            //      "ship_date":"2019-06-06T22:10:04-05:00",
            //      "tracking":"1Z6A76A90342463991",
            //      "product": {
            //        "BDBC-QN-01": {
            //          "sku":"BDBC-QN-01",
            //          "quantity":1,
            //          "item_name":"Try A Breeze Comforter"
            //        }
            //        }}}}}}
            JsonNode shipments = shipment.get("shipments");
            Iterator<JsonNode> fields = shipments.elements();
            while (fields.hasNext()) {
              JsonNode field = fields.next();
              if (field.has("tracking")) {
                // Determine the carrier
                long carrierId = -1;
                if (field.has("carrier")) {
                  JsonNode carrierNode = field.get("carrier");
                  String carrier = carrierNode.asText();
                  ShippingCarrier shippingCarrier = ShippingCarrierRepository.findByCode(carrier);
                  if (shippingCarrier != null) {
                    carrierId = shippingCarrier.getId();
                  } else {
                    LOG.warn("Boxzooka carrier code not found: " + carrier);
                  }
                }
                // Use the remote shipping date for this shipment (there could be others)
                Timestamp shipDate = null;
                if (field.has("ship_date")) {
                  // "2019-06-06T22:10:04-05:00"
                  JsonNode shipDateNode = field.get("ship_date");
                  try {
                    TemporalAccessor creationAccessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(shipDateNode.asText());
                    Instant instant = Instant.from(creationAccessor);
                    shipDate = Timestamp.from(instant);
                    order.setShippedDate(shipDate);
                  } catch (Exception e) {
                    LOG.warn("Could not parse shipDate: " + shipDateNode.asText(), e);
                  }
                }
                if (field.has("product")) {
                  // @todo get the SKUs to associate to the tracking number
                  // @note Is there an array somewhere here?


                }
                JsonNode trackingNumberNode = field.get("tracking");
                TrackingNumber trackingNumber = generateTrackingNumber(order.getId(), carrierId, shipDate, trackingNumberNode.asText());
                trackingNumberList.add(trackingNumber);
              }
            }
          }
          if (!trackingNumberList.isEmpty()) {
            return trackingNumberList;
          }
        }
        return null;
      }

      // {"Error":"required field is empty","message":{"item.0.weight":["The item.0.weight field is required."]}}
      if (json.has("Error")) {
        String message = null;
        if (json.has("message")) {
          message = ("Boxzooka error: " + json.get("Error").asText() + "; " + json.get("message").asText());
        } else {
          message = ("Boxzooka error: " + json.get("Error").asText());
        }
        LOG.debug(message);
//        throw new DataException(message);
        return null;
      }
    } catch (DataException de) {
      throw new DataException(de.getMessage());
    } catch (Exception e) {
      LOG.error("Exception when calling BXZ-Api#getShipmentByOrder: " + e.getMessage());
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }
    return null;
  }

  private static TrackingNumber generateTrackingNumber(long orderId, long carrierId, Timestamp shipDate, String trackingNumberValue) {
    TrackingNumber trackingNumber = new TrackingNumber();
    trackingNumber.setOrderId(orderId);
    trackingNumber.setShippingCarrierId(carrierId);
    trackingNumber.setShipDate(shipDate);
    trackingNumber.setTrackingNumber(trackingNumberValue);
    // @todo determine which products are in this shipment
    //trackingNumber.setOrderItemIdList();
    return trackingNumber;
  }
}
