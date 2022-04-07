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
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.SalesTaxNexusAddress;
import com.simisinc.platform.infrastructure.persistence.ecommerce.SalesTaxNexusAddressRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands for working with TaxJar
 *
 * @author matt rajkowski
 * @created 9/15/19 4:36 PM
 */
public class TaxJarCommand {

  private static Log LOG = LogFactory.getLog(TaxJarCommand.class);

  private static String API_URL = "https://api.taxjar.com/v2";
  private static String SANDBOX_URL = "https://api.sandbox.taxjar.com/v2";

  public static boolean determineTax(Order order) {

    LOG.debug("determineTax() called...");

    if (order == null) {
      LOG.debug("Order is required");
      return false;
    }

    if (order.getPaid()) {
      LOG.debug("Order is paid");
//      return true;
    }

    if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
      // There's nothing to charge
      LOG.debug("The amount is 0: " + order.getTotalAmount());
      return false;
    }

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.salesTaxService");
    if (!"TaxJar".equalsIgnoreCase(service)) {
      LOG.warn("TaxJar is not configured");
      return false;
    }

    String apiKey = LoadSitePropertyCommand.loadByName("ecommerce.taxjar.apiKey");
    if (StringUtils.isBlank(apiKey)) {
      LOG.warn("TaxJar key is not configured");
      return false;
    }

    // @todo make sure 2-Letter ISO codes are used for country (state is ok)
    String country = order.getShippingAddress().getCountry();
    String countryISO = country;
    if (countryISO.equalsIgnoreCase("United States")) {
      countryISO = "US";
    }
    String stateISO = order.getShippingAddress().getState();

    List<SalesTaxNexusAddress> nexusAddressList = SalesTaxNexusAddressRepository.findAll();
    if (nexusAddressList == null) {
      LOG.debug("No nexus addresses configured");
      return false;
    }

    SalesTaxNexusAddress matchedNexusAddress = null;
    for (SalesTaxNexusAddress thisNexusAddress : nexusAddressList) {
      if (country.equals(thisNexusAddress.getCountry()) && stateISO.equals(thisNexusAddress.getState())) {
        matchedNexusAddress = thisNexusAddress;
      }
    }
    if (matchedNexusAddress == null) {
      LOG.debug("No nexus");
      return false;
    }

    // @todo SmartCalcs supports tax rates in the United States, Canada, Australia, and European Union

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("to_country", countryISO);
    params.put("to_zip", order.getShippingAddress().getPostalCode());
    params.put("to_state", stateISO);
    params.put("to_city", order.getShippingAddress().getCity());
    params.put("to_street", order.getShippingAddress().getStreet());
    // Either amount OR line items are required
    params.put("amount", order.getSubtotalAmount());
    params.put("shipping", order.getShippingFee());

    List<Map> nexusAddresses = new ArrayList<>();
    Map<String, Object> nexusAddress = new LinkedHashMap<>();
    nexusAddress.put("country", "US");
    nexusAddress.put("zip", matchedNexusAddress.getPostalCode());
    nexusAddress.put("state", matchedNexusAddress.getState());
    nexusAddress.put("city", matchedNexusAddress.getCity());
    nexusAddress.put("street", matchedNexusAddress.getStreet());
    nexusAddresses.add(nexusAddress);
    params.put("nexus_addresses", nexusAddresses);

    // Either amount OR line items are required
    /*
    List<Map> lineItems = new ArrayList<>();
    Map<String, Object> lineItem = new LinkedHashMap<>();
    lineItem.put("id", 1);
    lineItem.put("quantity", 1);
    lineItem.put("product_tax_code", "20010");
    lineItem.put("unit_price", 15);
    lineItem.put("discount", 0);
    lineItems.add(lineItem);
    params.put("line_items", lineItems);
     */

    String jsonString = JsonCommand.createJsonNode(params).toString();
    LOG.debug("JSON STRING: " + jsonString);

    if (1 == 1) {
      return false;
    }

    JsonNode response = sendTaxJarHttpPost("/taxes", jsonString);
    if (response == null) {
      LOG.warn("TaxJar response was null");
      return false;
    }

    // {
    //  "tax": {
    //    "order_total_amount": 16.5,
    //    "shipping": 1.5,
    //    "taxable_amount": 15,
    //    "amount_to_collect": 1.35,
    //    "rate": 0.09,
    //    "has_nexus": true,
    //    "freight_taxable": false,
    //    "tax_source": "destination",
    //    "jurisdictions": {
    //      "country": "US",
    //      "state": "CA",
    //      "county": "LOS ANGELES",
    //      "city": "LOS ANGELES"
    //    },
    //    "breakdown": {
    //      "taxable_amount": 15,
    //      "tax_collectable": 1.35,
    //      "combined_tax_rate": 0.09,
    //      "state_taxable_amount": 15,
    //      "state_tax_rate": 0.0625,
    //      "state_tax_collectable": 0.94,
    //      "county_taxable_amount": 15,
    //      "county_tax_rate": 0.0025,
    //      "county_tax_collectable": 0.04,
    //      "city_taxable_amount": 0,
    //      "city_tax_rate": 0,
    //      "city_tax_collectable": 0,
    //      "special_district_taxable_amount": 15,
    //      "special_tax_rate": 0.025,
    //      "special_district_tax_collectable": 0.38,
    //      "line_items": [
    //        {
    //          "id": "1",
    //          "taxable_amount": 15,
    //          "tax_collectable": 1.35,
    //          "combined_tax_rate": 0.09,
    //          "state_taxable_amount": 15,
    //          "state_sales_tax_rate": 0.0625,
    //          "state_amount": 0.94,
    //          "county_taxable_amount": 15,
    //          "county_tax_rate": 0.0025,
    //          "county_amount": 0.04,
    //          "city_taxable_amount": 0,
    //          "city_tax_rate": 0,
    //          "city_amount": 0,
    //          "special_district_taxable_amount": 15,
    //          "special_tax_rate": 0.025,
    //          "special_district_amount": 0.38
    //        }
    //      ]
    //    }
    //  }
    //}


    return false;
  }

  public static boolean sendOrder(Order order) throws DataException {


    return false;
  }

  /*
  public static boolean sendRefund(Refund refund) throws DataException {


    return false;
  }
   */


  private static JsonNode sendTaxJarHttpPost(String endpoint, String jsonString) {

    // Determine the configuration
    String apiKey = LoadSitePropertyCommand.loadByName("ecommerce.taxjar.apiKey");
    String url = (EcommerceCommand.isProductionEnabled() ? API_URL : SANDBOX_URL) + endpoint;

    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader("Accept", "application/json");
      httpPost.setHeader("Content-type", "application/json");
      httpPost.setHeader("Authorization", "Bearer " + apiKey);

      StringEntity requestEntity = new StringEntity(
          jsonString,
          ContentType.APPLICATION_JSON);
      httpPost.setEntity(requestEntity);

      CloseableHttpResponse response = client.execute(httpPost);
      if (response == null) {
        LOG.error("HttpPost Response is empty");
        return null;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpPost Entity is null");
        return null;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpPost Remote content is empty");
        return null;
      }

      // TaxJar API uses the following error codes
      // https://developers.taxjar.com/api/reference/#errors
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 399) {
        LOG.error("HttpPost Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        return null;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }
      JsonNode json = JsonLoader.fromString(remoteContent);

//      if (json.has("id") && json.has("status")) {
      // Update the record to mark it as 'synced'
//        EmailRepository.markSynced(emailAddress);
//        return true;
//      }

      return json;
    } catch (Exception e) {
      LOG.error("validateRequest", e);
    }
    return null;
  }
}
