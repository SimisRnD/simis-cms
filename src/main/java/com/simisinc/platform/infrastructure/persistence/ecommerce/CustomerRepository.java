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

import com.simisinc.platform.application.ecommerce.SaveCustomerCommand;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves customer objects
 *
 * @author matt rajkowski
 * @created 4/24/19 10:45 PM
 */
public class CustomerRepository {

  private static Log LOG = LogFactory.getLog(CustomerRepository.class);

  private static String TABLE_NAME = "customers";
  private static String PRIMARY_KEY[] = new String[] { "customer_id" };

  private static DataResult query(CustomerSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils().addIfExists("customer_id = ?", specification.getId(), -1).addIfExists("LOWER(email) = ?",
          specification.getEmail() != null ? specification.getEmail().toLowerCase() : null);
      if (StringUtils.isNotBlank(specification.getUniqueId())) {
        where.add("(LOWER(customer_unique_id) = LOWER(?) OR LOWER(customer_unique_id) LIKE LOWER(?))",
            new String[] { specification.getUniqueId(), specification.getUniqueId() + "%" });
      }
      if (StringUtils.isNotBlank(specification.getOrderNumber())) {
        where.add(
            "EXISTS (SELECT 1 FROM orders WHERE customers.customer_id = orders.customer_id AND LOWER(order_unique_id) = ?)",
            specification.getOrderNumber().toLowerCase());
      }
      if (StringUtils.isNotBlank(specification.getPhoneNumber())) {
        where.add("phone_number = ?", specification.getPhoneNumber());
      }
      if (StringUtils.isNotBlank(specification.getName())) {
        where.add(
            "(LOWER(concat_ws(' ', first_name, last_name)) LIKE LOWER(?) ESCAPE '!' OR LOWER(concat_ws(' ', shipping_first_name, shipping_last_name)) LIKE LOWER(?) ESCAPE '!')",
            new String[] { "%" + specification.getName() + "%", "%" + specification.getName() + "%" });
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, CustomerRepository::buildRecord);
  }

  public static List<Customer> findAll(CustomerSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("customer_id");
    DataResult result = query(specification, constraints);
    return (List<Customer>) result.getRecords();
  }

  public static Customer findById(long customerId) {
    return (Customer) DB.selectRecordFrom(TABLE_NAME, new SqlUtils().add("customer_id = ?", customerId),
        CustomerRepository::buildRecord);
  }

  public static Customer save(Customer record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Customer add(Customer record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("customer_unique_id", record.getUniqueId())
            .add("email", record.getEmail())
            .add("first_name", record.getFirstName())
            .add("last_name", record.getLastName())
            .add("organization", record.getOrganization())
            .add("barcode", record.getBarcode())
            /*
             * .add("street_address", record.getStreet()) .add("address_line_2",
             * record.getAddressLine2()) .add("address_line_3", record.getAddressLine3())
             * .add("city", record.getCity()) .add("state", record.getState())
             * .add("country", record.getCountry()) .add("postal_code",
             * record.getPostalCode()) .add("county", record.getCounty())
             */
            .add("phone_number", record.getPhoneNumber())
            .add("tax_id", record.getTaxId())
            .add("remote_customer_id", record.getRemoteCustomerId())
            .addIfExists("created_by", record.getCreatedBy(), -1)
            .addIfExists("modified_by", record.getModifiedBy(), -1);
        if (record.getBillingAddress() != null) {
          insertValues.add("billing_first_name", record.getBillingAddress().getFirstName())
              .add("billing_last_name", record.getBillingAddress().getLastName())
              .add("billing_organization", record.getBillingAddress().getOrganization())
              .add("billing_street_address", record.getBillingAddress().getStreet())
              .add("billing_address_line_2", record.getBillingAddress().getAddressLine2())
              .add("billing_address_line_3", record.getBillingAddress().getAddressLine3())
              .add("billing_city", record.getBillingAddress().getCity())
              .add("billing_state", record.getBillingAddress().getState())
              .add("billing_country", record.getBillingAddress().getCountry())
              .add("billing_postal_code", record.getBillingAddress().getPostalCode())
              .add("billing_county", record.getBillingAddress().getCounty())
              .add("billing_phone_number", record.getBillingAddress().getPhoneNumber());
        }
        if (record.getShippingAddress() != null) {
          insertValues.add("shipping_first_name", record.getShippingAddress().getFirstName())
              .add("shipping_last_name", record.getShippingAddress().getLastName())
              .add("shipping_organization", record.getShippingAddress().getOrganization())
              .add("shipping_street_address", record.getShippingAddress().getStreet())
              .add("shipping_address_line_2", record.getShippingAddress().getAddressLine2())
              .add("shipping_address_line_3", record.getShippingAddress().getAddressLine3())
              .add("shipping_city", record.getShippingAddress().getCity())
              .add("shipping_state", record.getShippingAddress().getState())
              .add("shipping_country", record.getShippingAddress().getCountry())
              .add("shipping_postal_code", record.getShippingAddress().getPostalCode())
              .add("shipping_county", record.getShippingAddress().getCounty())
              .add("shipping_phone_number", record.getShippingAddress().getPhoneNumber());
        }
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        {
          // Generate a new customer unique id
          LOG.debug("Updating customer unique id for id: " + record.getId());
          SqlUtils update = new SqlUtils().add("customer_unique_id", SaveCustomerCommand.generateUniqueId(record));
          SqlUtils where = new SqlUtils().add("customer_id = ?", record.getId());
          DB.update(connection, TABLE_NAME, update, where);
        }
        if (record.getCartId() > 0) {
          // Update the cart
          LOG.debug("Updating cart " + record.getCartId() + " with the customer id");
          SqlUtils update = new SqlUtils().add("customer_id", record.getId());
          SqlUtils where = new SqlUtils().add("cart_id = ?", record.getCartId());
          DB.update(connection, "carts", update, where);
        }
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException | CheckDigitException se) {
      LOG.error("Exception: " + se.getMessage(), se);
    }
    return null;
  }

  public static Customer update(Customer record) {
    SqlUtils updateValues = new SqlUtils()
        .add("email", record.getEmail())
        .add("first_name", record.getFirstName())
        .add("last_name", record.getLastName())
        .add("organization", record.getOrganization())
        .add("barcode", record.getBarcode())
        /*
         * .add("street_address", record.getStreet()) .add("address_line_2",
         * record.getAddressLine2()) .add("address_line_3", record.getAddressLine3())
         * .add("city", record.getCity()) .add("state", record.getState())
         * .add("country", record.getCountry()) .add("postal_code",
         * record.getPostalCode()) .add("county", record.getCounty())
         */
        .add("phone_number", record.getPhoneNumber())
        .add("tax_id", record.getTaxId())
        .add("remote_customer_id", record.getRemoteCustomerId())
        .addIfExists("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    if (record.getBillingAddress() != null) {
      LOG.debug("Updating the billing information...");
      updateValues
          .add("billing_first_name", record.getBillingAddress().getFirstName())
          .add("billing_last_name", record.getBillingAddress().getLastName())
          .add("billing_organization", record.getBillingAddress().getOrganization())
          .add("billing_street_address", record.getBillingAddress().getStreet())
          .add("billing_address_line_2", record.getBillingAddress().getAddressLine2())
          .add("billing_address_line_3", record.getBillingAddress().getAddressLine3())
          .add("billing_city", record.getBillingAddress().getCity())
          .add("billing_state", record.getBillingAddress().getState())
          .add("billing_country", record.getBillingAddress().getCountry())
          .add("billing_postal_code", record.getBillingAddress().getPostalCode())
          .add("billing_county", record.getBillingAddress().getCounty())
          .add("billing_phone_number", record.getBillingAddress().getPhoneNumber());
    } else {
      LOG.debug("Resetting the billing information...");
      updateValues
          .add("billing_first_name", (String) null)
          .add("billing_last_name", (String) null)
          .add("billing_organization", (String) null)
          .add("billing_street_address", (String) null)
          .add("billing_address_line_2", (String) null)
          .add("billing_address_line_3", (String) null)
          .add("billing_city", (String) null)
          .add("billing_state", (String) null)
          .add("billing_country", (String) null)
          .add("billing_postal_code", (String) null)
          .add("billing_county", (String) null)
          .add("billing_phone_number", (String) null);
    }
    if (record.getShippingAddress() != null) {
      LOG.debug("Updating the shipping information...");
      updateValues
          .add("shipping_first_name", record.getShippingAddress().getFirstName())
          .add("shipping_last_name", record.getShippingAddress().getLastName())
          .add("shipping_organization", record.getShippingAddress().getOrganization())
          .add("shipping_street_address", record.getShippingAddress().getStreet())
          .add("shipping_address_line_2", record.getShippingAddress().getAddressLine2())
          .add("shipping_address_line_3", record.getShippingAddress().getAddressLine3())
          .add("shipping_city", record.getShippingAddress().getCity())
          .add("shipping_state", record.getShippingAddress().getState())
          .add("shipping_country", record.getShippingAddress().getCountry())
          .add("shipping_postal_code", record.getShippingAddress().getPostalCode())
          .add("shipping_county", record.getShippingAddress().getCounty())
          .add("shipping_phone_number", record.getShippingAddress().getPhoneNumber());
    } else {
      LOG.debug("Resetting the shipping information...");
      updateValues
          .add("shipping_first_name", (String) null)
          .add("shipping_last_name", (String) null)
          .add("shipping_organization", (String) null)
          .add("shipping_street_address", (String) null)
          .add("shipping_address_line_2", (String) null)
          .add("shipping_address_line_3", (String) null)
          .add("shipping_city", (String) null)
          .add("shipping_state", (String) null)
          .add("shipping_country", (String) null)
          .add("shipping_postal_code", (String) null)
          .add("shipping_county", (String) null)
          .add("shipping_phone_number", (String) null);
    }
    SqlUtils where = new SqlUtils().add("customer_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE,
      // record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void updateContactInfo(Customer record) {
    if (record.getId() == -1) {
      LOG.debug("Can't update customer -1");
      return;
    }
    LOG.debug("Updating the customer record");
    SqlUtils set = new SqlUtils()
      .add("first_name", record.getFirstName())
      .add("last_name", record.getLastName())
      .add("email", record.getEmail());
    SqlUtils where = new SqlUtils().add("customer_id = ?", record.getId());
    DB.update(TABLE_NAME, set, where);
  }

  private static Customer buildRecord(ResultSet rs) {
    try {
      Customer record = new Customer();
      Address billingAddress = new Address();
      Address shippingAddress = new Address();

      record.setId(rs.getLong("customer_id"));
      record.setUniqueId(rs.getString("customer_unique_id"));
      record.setEmail(rs.getString("email"));
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setOrganization(rs.getString("organization"));
      record.setBarcode(rs.getString("barcode"));
      /*
       * record.setStreet(rs.getString("street_address"));
       * record.setAddressLine2(rs.getString("address_line_2"));
       * record.setAddressLine3(rs.getString("address_line_3"));
       * record.setCity(rs.getString("city")); record.setState(rs.getString("state"));
       * record.setCountry(rs.getString("country"));
       * record.setPostalCode(rs.getString("postal_code"));
       * record.setCounty(rs.getString("county"));
       */
      record.setPhoneNumber(rs.getString("phone_number"));
      billingAddress.setFirstName(rs.getString("billing_first_name"));
      billingAddress.setLastName(rs.getString("billing_last_name"));
      billingAddress.setOrganization(rs.getString("billing_organization"));
      billingAddress.setStreet(rs.getString("billing_street_address"));
      billingAddress.setAddressLine2(rs.getString("billing_address_line_2"));
      billingAddress.setAddressLine3(rs.getString("billing_address_line_3"));
      billingAddress.setCity(rs.getString("billing_city"));
      billingAddress.setState(rs.getString("billing_state"));
      billingAddress.setCountry(rs.getString("billing_country"));
      billingAddress.setPostalCode(rs.getString("billing_postal_code"));
      billingAddress.setCounty(rs.getString("billing_county"));
      billingAddress.setPhoneNumber(rs.getString("billing_phone_number"));
      shippingAddress.setFirstName(rs.getString("shipping_first_name"));
      shippingAddress.setLastName(rs.getString("shipping_last_name"));
      shippingAddress.setOrganization(rs.getString("shipping_organization"));
      shippingAddress.setStreet(rs.getString("shipping_street_address"));
      shippingAddress.setAddressLine2(rs.getString("shipping_address_line_2"));
      shippingAddress.setAddressLine3(rs.getString("shipping_address_line_3"));
      shippingAddress.setCity(rs.getString("shipping_city"));
      shippingAddress.setState(rs.getString("shipping_state"));
      shippingAddress.setCountry(rs.getString("shipping_country"));
      shippingAddress.setPostalCode(rs.getString("shipping_postal_code"));
      shippingAddress.setCounty(rs.getString("shipping_county"));
      shippingAddress.setPhoneNumber(rs.getString("shipping_phone_number"));
      record.setTaxId(rs.getString("tax_id"));
      record.setRemoteCustomerId(rs.getString("remote_customer_id"));
      record.setCurrency(rs.getString("currency"));
      record.setAccountBalance(rs.getBigDecimal("account_balance"));
      record.setTotalSpend(rs.getBigDecimal("total_spend"));
      record.setOrderCount(rs.getInt("order_count"));
      record.setDelinquent(rs.getBoolean("delinquent"));
      record.setDiscount(rs.getString("discount"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      // Update the aggregate
      record.setBillingAddress(billingAddress);
      record.setShippingAddress(shippingAddress);
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
