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

import com.simisinc.platform.domain.model.ecommerce.ShippingCarrier;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCarrierRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Tracking number utilities
 *
 * @author matt rajkowski
 * @created 02/10/20 4:03 PM
 */
public class TrackingServiceCommand {

  private static Log LOG = LogFactory.getLog(TrackingServiceCommand.class);

  public static Map<String, String> trackingNumberLinkMap(String trackingNumberValue, ShippingMethod shippingMethod) {

    if (StringUtils.isBlank(trackingNumberValue)) {
      return null;
    }

    ShippingCarrier shippingCarrier = BoxzookaShipmentCommand.getShippingCarrier(shippingMethod);

    Map<String, String> trackingNumberMap = new HashMap<>();

    List<String> trackingNumberList = Stream.of(trackingNumberValue.split(","))
        .map(String::trim)
        .collect(toList());

    for (String thisTrackingNumber : trackingNumberList) {

      TrackingNumber trackingNumber = new TrackingNumber();
      trackingNumber.setTrackingNumber(thisTrackingNumber);
      if (shippingCarrier != null) {
        trackingNumber.setShippingCarrierId(shippingCarrier.getId());
      }

      String link = determineTrackingNumberWebLink(trackingNumber);
      if (StringUtils.isNotBlank(link)) {
        trackingNumberMap.put(thisTrackingNumber, link);
      }
    }
    return trackingNumberMap;
  }

  public static String determineTrackingNumberWebLink(TrackingNumber trackingNumber) {

    // Validate the required parameters
    if (StringUtils.isBlank(trackingNumber.getTrackingNumber())) {
      LOG.warn("No tracking number");
      return null;
    }

    // Determine the link
    String trackingNumberValue = trackingNumber.getTrackingNumber().trim().toUpperCase();
    ShippingCarrier shippingCarrier = ShippingCarrierRepository.findById(trackingNumber.getShippingCarrierId());
    String link = null;

    // Determine the carrier website
    // Look into https://github.com/adgaudio/MysteryTrackingNumber
    //
    if (trackingNumberValue.startsWith("1Z") || (shippingCarrier != null && shippingCarrier.getCode().equals("UPS"))) {
      // UPS
      // http://wwwapps.ups.com/WebTracking/track?track=yes&trackNums=1ZXXXXXXXXXXXXXXXX
      link = "http://wwwapps.ups.com/WebTracking/track?track=yes&trackNums=" + trackingNumberValue;
    } else if ((shippingCarrier != null && shippingCarrier.getCode().equals("USPS")) ||
        (trackingNumberValue.startsWith("94") && trackingNumberValue.length() == 22) ||
        (trackingNumberValue.startsWith("420") && trackingNumberValue.length() == 30)
    ) {
      // USPS
      // USPS Tracking: 9400 0000 0000 0000 0000 00
      //
      //Priority Mail: 9205 5000 0000 0000 0000 00
      //
      //Certified Mail: 9407 3000 0000 0000 0000 00
      //
      //Collect on Delivery: 9303 3000 0000 0000 0000 00
      //
      //Global Express Guaranteed: 82 000 000 00
      //
      //Priority Mail Express International: EC 000 000 000 US
      //
      //Priority Mail Express: 9270 1000 0000 0000 0000 00
      //EA 000 000 000 US
      //
      //Priority Mail International: CP 000 000 000 US
      //
      //Registered Mail: 9208 8000 0000 0000 0000 00
      //
      //Signature Confirmation: 9202 1000 0000 0000 0000 00
      link = "https://tools.usps.com/go/TrackConfirmAction?tLabels=" + trackingNumberValue;
    } else if (shippingCarrier != null &&
        (shippingCarrier.getCode().equalsIgnoreCase("FEDEX") ||
            shippingCarrier.getCode().equalsIgnoreCase("SMARTPOST"))) {
      // FedEx
      // http://www.fedex.com/Tracking?action=track&tracknumbers=XXXXXXXXXXXXXXX
      link = "http://www.fedex.com/Tracking?action=track&tracknumbers=" + trackingNumberValue;
    } else if (shippingCarrier != null && shippingCarrier.getCode().equals("DHL")) {
      // DHL US
      // http://track.dhl-usa.com/TrackByNbr.asp?ShipmentNumber=XXXXXXXXXXXXXXXXX

      // DHL Global
      // http://webtrack.dhlglobalmail.com/?trackingnumber=XXXXXXXXXXXXXXXXXXXXXX
      link = "http://webtrack.dhlglobalmail.com/?trackingnumber=" + trackingNumberValue;
    }
    trackingNumber.setLink(link);
    return link;
  }
}
