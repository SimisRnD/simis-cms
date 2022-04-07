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
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.SalesTaxNexusAddressRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.USSalesTaxRatesRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/19 10:08 PM
 */
public class SalesTaxCommand {

  private static Log LOG = LogFactory.getLog(SalesTaxCommand.class);

  // https://blog.taxjar.com/charging-sales-tax-rates/

  // https://www.avalara.com/us/en/learn/whitepapers/shipping-handling-sales-tax.html
  // https://www.avalara.com/us/en/blog/2018/11/how-to-handle-sales-tax-on-shipping-a-state-by-state-guide.html
  // https://www.accuratetax.com/blog/sales-tax-shipping-charges/

  // https://law.lis.virginia.gov/admincode/title23/agency10/chapter210/section6000/
  // Virginia: Separately stated shipping charges, including postage, are exempt from Virginia sales tax,
  // but handling charges are always taxable. When shipping charges are combined with handling or other fees
  // as a single charge, they’re taxable.

  // https://www.state.nj.us/treasury/taxation/su_8.shtml
  // As of October 1, 2006, delivery charges (shipping, handling, postage, etc.) are subject to tax if the
  // goods purchased are subject to tax

  // North Carolina sales tax may apply to charges for shipping, handling, delivery, freight, and postage
  // https://files.nc.gov/ncdor/documents/files/sales_and_use_tax_combined_bulletins_1.pdf

  public static final List<String> ORIGIN_BASED_STATES = Arrays.asList(
      "ARIZONA", "AZ",
      "CALIFORNIA", "CA",  // modified origin state where state, county and city taxes are based on the origin, but district taxes are based on the destination (the buyer).
      "ILLINOIS", "IL",
      "MISSISSIPPI", "MS",
      "MISSOURI", "MO",
      "NEW MEXICO", "NM",
      "OHIO", "OH",
      "PENNSYLVANIA", "PA",
      "TENNESSEE", "TN",
      "TEXAS", "TX",
      "UTAH", "UT",
      "VIRGINIA", "VA"
  );

  // @note List is incomplete
  public static final List<String> TAX_ON_SHIPPING = Arrays.asList(
      "ARKANSAS", "AR",
      "CONNECTICUT", "CT",
      "FLORIDA", "FL",
      "GEORGIA", "GA",
      "HAWAII", "HI",
      "IDAHO", "ID",
      "KANSAS", "KS",
      "KENTUCKY", "KY",
      "LOUISIANA", "LA",
      // "MARYLAND", "MD", // when handling is specified, delivery is taxed
      "NEW JERSEY", "NJ",
      "NORTH CAROLINA", "NC"
  );

  // @note List is incomplete
  public static final List<String> TAX_ON_HANDLING = Arrays.asList(
      "ARIZONA", "AZ",
      "GEORGIA", "GA",
      "HAWAII", "HI",
      "IDAHO", "ID",
      "KANSAS", "KS",
      "KENTUCKY", "KY",
      "MARYLAND", "MD",
      "NEW JERSEY", "NJ",
      "NORTH CAROLINA", "NC",
      "VIRGINIA", "VA"
  );

  /**
   * If the delivery address falls in a different state than those in which your business (or merchant of sale) has
   * nexus, no sales tax need be collected.
   *
   * @param address
   * @return
   */
  public static BigDecimal estimatedTaxRateForAddress(Address address) throws DataException {

    // Determine the country/region/postal code being shipped to
    String country = address.getCountry();
    String state = address.getState();
    String zipCode = address.getPostalCode();

    // @todo SmartCalcs supports tax rates in the United States, Canada, Australia, and European Union
    // https://support.taxjar.com/article/157-what-countries-does-smartcalcs-support

    if (!"UNITED STATES".equalsIgnoreCase(country) && !"US".equalsIgnoreCase(country)) {
      // @todo Non-US Addresses
      LOG.debug("Not a US address: " + country);
      return null;
    }

    // See which countries and states to base taxes on
    List<SalesTaxNexusAddress> nexusAddressList = SalesTaxNexusAddressRepository.findAll();
    if (nexusAddressList == null) {
      LOG.debug("No nexus addresses have been setup");
      return null;
    }

    // Compare the nexus states which the address products are being shipped to
    for (SalesTaxNexusAddress nexusAddress : nexusAddressList) {

      // @todo SmartCalcs supports tax rates in the United States, Canada, Australia, and European Union

      if (country.equals(nexusAddress.getCountry()) && state.equals(nexusAddress.getState())) {

        // Determine if the tax is based on origin
        if (isOriginBased(nexusAddress)) {
          // Use the nexus address for the sales tax
          country = nexusAddress.getCountry();
          state = nexusAddress.getState();
          zipCode = nexusAddress.getPostalCode();
        }

        // Now calculate
        USSalesTaxRate salesTaxRate = USSalesTaxRatesRepository.findByStateZipCode(state, zipCode);
        if (salesTaxRate == null) {
          // Find just by state - zip code was not found
          LOG.debug("Zip code was not found: " + zipCode);
          salesTaxRate = USSalesTaxRatesRepository.findByState(state);
          if (salesTaxRate == null) {
            LOG.debug("Could not find state: " + state);
            return null;
          }
        }
        // A rate was found
        LOG.debug("Found tax rate: " + salesTaxRate.getCombinedRate().doubleValue());
        if (salesTaxRate.getCombinedRate().doubleValue() >= 0.0) {
          return salesTaxRate.getCombinedRate();
        }
      }
    }
    return null;
  }

  public static BigDecimal estimateTax(Cart cart, Address address, BigDecimal taxRate, ShippingRate shippingRate) throws DataException {

    // Require an address
    if (StringUtils.isBlank(address.getCountry()) || StringUtils.isBlank(address.getState()) || StringUtils.isBlank(address.getPostalCode())) {
      LOG.warn("Missing address information");
      throw new DataException("Customer address is incomplete");
    }

    // Use the subTotal and discount, which are already calculated
    BigDecimal subTotal = cart.getSubtotalAmount();
    BigDecimal discount = cart.getDiscount();

    // Use the specified shipping and handling
    BigDecimal shipping = shippingRate.getShippingFee();
    BigDecimal handling = shippingRate.getHandlingFee();

    // Require a subtotal (taxable items only)
    if (subTotal == null) {
      LOG.warn("Missing product subtotal object");
      throw new DataException("Product subtotal is incomplete");
    }

    // Apply the discount before taxing
    if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
      subTotal = subTotal.subtract(discount);
    }

    // Determine the taxes
    BigDecimal taxes = new BigDecimal(0);

    // Require a tax rate
    if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) == 0) {
      return taxes;
    }

    // A tax rate was found...
    LOG.debug("Tax rate found: " + taxRate.doubleValue());

    // Determine if shipping and/or handling need to be taxed
    BigDecimal amountToTax = subTotal;

    // Check handling and shipping based on state
    boolean additionalTax = false;

    // Determine taxes on handling
    if (handling != null && isHandlingTaxable(address)) {
      additionalTax = true;
      amountToTax = amountToTax.add(handling);
      LOG.debug("Adding handling fee to taxes: " + handling.toPlainString());
    }

    // Determine taxes on shipping
    if (shipping != null && isShippingTaxable(address)) {
      additionalTax = true;
      amountToTax = amountToTax.add(shipping);
      LOG.debug("Adding shipping fee to taxes: " + shipping.toPlainString());
    }

    if (!additionalTax) {
      LOG.warn("Shipping and Handling Tax information not found for: " + address.getState());
    }

    // Return the final tax amount (Bankers' Rounding)
    return taxRate.multiply(amountToTax).setScale(2, RoundingMode.HALF_EVEN);
  }

  public static boolean isOriginBased(SalesTaxNexusAddress address) {
    String state = address.getState().toUpperCase();
    return ORIGIN_BASED_STATES.stream().anyMatch(state::equals);
  }

  public static boolean isShippingTaxable(Address address) {
    String state = address.getState().toUpperCase();
    return TAX_ON_SHIPPING.stream().anyMatch(state::equals);
  }

  public static boolean isHandlingTaxable(Address address) {
    String state = address.getState().toUpperCase();
    return TAX_ON_HANDLING.stream().anyMatch(state::equals);
  }
}
