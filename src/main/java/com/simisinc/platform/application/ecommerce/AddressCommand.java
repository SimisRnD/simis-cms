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
import com.simisinc.platform.domain.model.ecommerce.Address;
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
 * Address
 *
 * @author matt rajkowski
 * @created 4/24/19 10:08 PM
 */
public class AddressCommand {

  private static Log LOG = LogFactory.getLog(AddressCommand.class);

  private static String USPS_SHIPPING_API_URL = "https://secure.shippingapis.com/ShippingAPI.dll?API=Verify&XML=";

  /**
   * The Address Standardization Web Tool corrects errors in street addresses, including abbreviations and missing
   * information, and supplies ZIP Codes and ZIP Codes + 4.  By eliminating address errors, you will improve overall
   * package delivery service.
   *
   * @param address
   * @return
   * @throws DataException
   */
  public static Address verifyAddress(Address address, StringBuilder errorMessages) {

    if (address == null) {
      return null;
    }

    // Only for US, maybe Canada
    if (!"UNITED STATES".equalsIgnoreCase(address.getCountry())) {
      LOG.debug("Not a US address");
      return null;
    }

    String service = LoadSitePropertyCommand.loadByName("ecommerce.addressValidation");
    String uspsUserId = LoadSitePropertyCommand.loadByName("ecommerce.usps.webtools.userid");


    // @todo Determine if using USPS or Built-In Zipcode data to format address


    // Use USPS Verification
    if (StringUtils.isBlank(service) || !service.equalsIgnoreCase("USPS")) {
      LOG.debug("USPS service is not enabled");
      return null;
    }
    if (StringUtils.isBlank(uspsUserId)) {
      LOG.warn("USPS service userId is not configured");
      return null;
    }

    // Create the xml
    String xml =
        "<AddressValidateRequest USERID=\"" + StringEscapeUtils.escapeXml11(uspsUserId) + "\">" +
            "<Revision>1</Revision>" +
            "<Address ID=\"0\">" +
            (StringUtils.isNotBlank(address.getStreet()) ? "<Address1>" + StringEscapeUtils.escapeXml11(address.getStreet().trim()) + "</Address1>" : "<Address1/>") +
            (StringUtils.isNotBlank(address.getAddressLine2()) ? "<Address2>" + StringEscapeUtils.escapeXml11(address.getAddressLine2().trim()) + "</Address2>" : "<Address2/>") +
            (StringUtils.isNotBlank(address.getCity()) ? "<City>" + StringEscapeUtils.escapeXml11(address.getCity().trim()) + "</City>" : "<City/>") +
            (StringUtils.isNotBlank(address.getState()) ? "<State>" + StringEscapeUtils.escapeXml11(address.getState().trim()) + "</State>" : "<State/>") +
            (StringUtils.isNotBlank(address.getPostalCode()) ? "<Zip5>" + StringEscapeUtils.escapeXml11(address.getPostalCode().trim()) + "</Zip5>" : "<Zip5/>") +
            "<Zip4></Zip4>" +
            "</Address>" +
            "</AddressValidateRequest>";

    // Retrieve a formatted address
    try {
      LOG.debug("Sending xml... " + xml);
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(USPS_SHIPPING_API_URL + URLEncoder.encode(xml, "UTF-8"));
      HttpResponse response = client.execute(request);
      HttpEntity entity = response.getEntity();
      String responseValue = EntityUtils.toString(entity);
      LOG.debug("USPS RESPONSE: " + responseValue);

      // Process the XML response
      if (!responseValue.contains("<AddressValidateResponse>")) {
        return null;
      }

      // Look for an error
      if (responseValue.contains("<Error>")) {
        String description = StringUtils.substringBetween(responseValue, "<Description>", "</Description>");
        if (StringUtils.isNotBlank(description)) {
          appendMessage(errorMessages, description);
        }
      }

      // Prepare the object to send back
      int valueCount = 0;
      Address formattedAddress = new Address();
      formattedAddress.setCountry(address.getCountry());
      formattedAddress.setFirstName(address.getFirstName());
      formattedAddress.setLastName(address.getLastName());

      // Parse the XML
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputStream is = IOUtils.toInputStream(responseValue, "UTF-8");
      Document document = builder.parse(is);
      NodeList addressList = document.getElementsByTagName("Address");
      if (addressList == null || addressList.getLength() != 1) {
        return null;
      }
      NodeList fields = addressList.item(0).getChildNodes();
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
        if (fieldName.equals("Address1")) {
          formattedAddress.setAddressLine2(value);
        } else if (fieldName.equals("Address2")) {
          formattedAddress.setStreet(value);
          ++valueCount;
        } else if (fieldName.equals("City")) {
          formattedAddress.setCity(value);
          ++valueCount;
        } else if (fieldName.equals("State")) {
          formattedAddress.setState(value);
          ++valueCount;
        } else if (fieldName.equals("Zip5")) {
          formattedAddress.setPostalCode(value);
          ++valueCount;
        } else if (fieldName.equals("Zip4")) {
          if (StringUtils.isNotBlank(formattedAddress.getPostalCode())) {
            formattedAddress.setPostalCode(formattedAddress.getPostalCode() + "-" + value);
          }
        }
      }
      if (valueCount >= 4) {
        return formattedAddress;
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("USPS address validation issues: " + e.getMessage());
    }
    return null;
  }

  public static boolean isMostlyTheSame(Address address1, Address address2) {
    return (
        address1.getStreet().equalsIgnoreCase(address2.getStreet()) &&
            ((StringUtils.isBlank(address1.getAddressLine2()) && StringUtils.isBlank(address2.getAddressLine2()))
                || address1.getAddressLine2().equalsIgnoreCase(address2.getAddressLine2())) &&
            address1.getCity().equalsIgnoreCase(address2.getCity()) &&
            address1.getState().equalsIgnoreCase(address2.getState()) &&
            address1.getPostalCode().substring(0, 5).equalsIgnoreCase(address2.getPostalCode().substring(0, 5))
    );
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }
}
