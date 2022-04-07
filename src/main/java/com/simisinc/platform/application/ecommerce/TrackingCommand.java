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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URLEncoder;

/**
 * Package Tracking
 *
 * @author matt rajkowski
 * @created 11/20/19 7:45 AM
 */
public class TrackingCommand {

  private static Log LOG = LogFactory.getLog(TrackingCommand.class);

  // https://www.usps.com/business/web-tools-apis/general-api-developer-guide.htm#_Toc423593925
  private static String USPS_TRACKING_API_URL = "https://secure.shippingapis.com/ShippingAPI.dll?API=TrackV2&XML=";

  /**
   * @param trackingNumbers
   * @return
   * @throws DataException
   */
//  public static TrackingInfo trackPackage(String trackingNumbers, StringBuilder errorMessages) {
  public static String trackUSPSPackage(String trackingNumbers, StringBuilder errorMessages) {

    if (trackingNumbers == null) {
      return null;
    }

    String uspsUserId = LoadSitePropertyCommand.loadByName("ecommerce.usps.webtools.userid");

    // Use USPS Verification
    if (StringUtils.isBlank(uspsUserId)) {
      LOG.warn("USPS service userId is not configured");
      return null;
    }

    // Create the xml
    String xml =
        "<TrackRequest USERID=\"" + StringEscapeUtils.escapeXml11(uspsUserId) + "\">\n" +
            "<TrackID ID=\"" + StringEscapeUtils.escapeXml11(trackingNumbers) + "\"></TrackID></TrackRequest>";

    // Retrieve any tracking information
    try {
      LOG.debug("Sending xml... " + xml);
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(USPS_TRACKING_API_URL + URLEncoder.encode(xml, "UTF-8"));
      HttpResponse response = client.execute(request);
      HttpEntity entity = response.getEntity();
      String responseValue = EntityUtils.toString(entity);
      LOG.debug("USPS RESPONSE: " + responseValue);

      // Process the XML response
      if (!responseValue.contains("<TrackResponse>")) {
        return null;
      }

      // Look for an error
      if (responseValue.contains("<Error>")) {
        String description = StringUtils.substringBetween(responseValue, "<Description>", "</Description>");
        if (StringUtils.isNotBlank(description)) {
          appendMessage(errorMessages, description);
        }
      }

      // <?xml version="1.0"?>
      // <TrackResponse>
      //   <TrackInfo ID="EJ958088694US">
      //     <TrackSummary>
      //       Your item was delivered at 1:39 pm on June 1 in WOBURN MA 01815.
      //     </TrackSummary>
      //     <TrackDetail>
      //       May 30 7:44 am NOTICE LEFT WOBURN MA 01815.
      //     </TrackDetail>
      //     <TrackDetail>
      //       May 30 7:36 am ARRIVAL AT UNIT NORTH READING MA 01889.
      //     </TrackDetail>
      //     <TrackDetail>
      //       May 29 6:00 pm ACCEPT OR PICKUP PORTSMOUTH NH 03801.
      //     </TrackDetail>
      //   </TrackInfo>
      // </TrackResponse>

      // Parse the XML
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputStream is = IOUtils.toInputStream(responseValue, "UTF-8");
      Document document = builder.parse(is);

      NodeList trackInfoList = document.getElementsByTagName("TrackInfo");
      if (trackInfoList == null || trackInfoList.getLength() != 1) {
        return null;
      }
      NodeList fields = trackInfoList.item(0).getChildNodes();
      for (int i = 0; i < fields.getLength(); i++) {
        if (fields.item(i).getNodeType() != Element.ELEMENT_NODE) {
          continue;
        }
        Element field = (Element) fields.item(i);
        String fieldName = field.getTagName();
        String value = field.getTextContent();
        LOG.debug("Found: " + fieldName + "=" + value);
        if (StringUtils.isBlank(value)) {
          continue;
        }
        if (fieldName.equals("TrackSummary")) {
          return (value);
        }
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("USPS API tracking issues: " + e.getMessage());
    }
    return null;
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }
}
