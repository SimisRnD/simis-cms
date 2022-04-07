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

import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/8/19 9:21 AM
 */
public class ShippingRateRepository {

  private static Log LOG = LogFactory.getLog(ShippingRateRepository.class);

  private static String TABLE_NAME = "shipping_rates";
  private static String ADDITIONAL_SELECT = "lookup_shipping_method.title";
  private static String JOIN = "LEFT JOIN lookup_shipping_method ON (shipping_rates.shipping_method = lookup_shipping_method.method_id)";
  private static String PRIMARY_KEY[] = new String[]{"rate_id"};

  private static DataResult query(ShippingRateSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils().add(ADDITIONAL_SELECT);
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    // Use the specification to find the best matching rate for the given address
    if (specification != null) {
      if (StringUtils.isNotBlank(specification.getCountryCode())) {
        where.add("country_code = ?", specification.getCountryCode());
      }
      if (StringUtils.isNotBlank(specification.getRegion()) && StringUtils.isNotBlank(specification.getPostalCode())) {
        String region = specification.getRegion();
        String postalCode = specification.getPostalCode();
        if ("*".equals(region) && "*".equals(postalCode)) {
          // Non-specific, fall-back
          where.add("postal_code = '*'");
          where.add("region = '*'");
        } else {
          // Location specific and non-specific
          if ("US".equals(specification.getCountryCode())) {
            if (postalCode.length() > 5) {
              postalCode = postalCode.substring(0, 5);
            }
          }
          // Determine the region setting
          if (specification.getSpecificRegionOnly()) {
            // Look for a specific region (like Alaska, Hawaii)
            where.add("(postal_code = ? OR (postal_code = '*' AND region = ?))", new String[]{postalCode, region});
          } else {
            // Use any generic region
            where.add("(postal_code = ? OR (postal_code = '*' AND region = ?) OR (postal_code = '*' AND region = '*'))", new String[]{postalCode, region});
          }
        }
      }
      if (specification.getOrderSubtotal() != null) {
        where.add("min_subtotal <= ?", specification.getOrderSubtotal());
      }
      if (specification.getPackageTotalWeightOz() >= 0) {
        where.add("min_weight_oz <= ?", specification.getPackageTotalWeightOz());
      }
      if (specification.getEnabledOnly()) {
        where.add("lookup_shipping_method.enabled = ?", true);
      }
      constraints.setDefaultColumnToSortBy("postal_code, region, shipping_method, shipping_fee");
    }

    return DB.selectAllFrom(TABLE_NAME, select, joins, where, null, constraints, ShippingRateRepository::buildRecord);
  }

  public static List<ShippingRate> findAll(ShippingRateSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("country_code, region, postal_code, shipping_method, shipping_fee");
    DataResult result = query(specification, constraints);
    return (List<ShippingRate>) result.getRecords();
  }

  public static ShippingRate findById(long shippingRateId) {
    SqlUtils select = new SqlUtils().add(ADDITIONAL_SELECT);
    SqlJoins joins = new SqlJoins().add(JOIN);
    return (ShippingRate) DB.selectRecordFrom(
        TABLE_NAME,
        select,
        joins,
        new SqlUtils().add("rate_id = ?", shippingRateId),
        ShippingRateRepository::buildRecord);
  }

  public static ShippingRate save(ShippingRate record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ShippingRate add(ShippingRate record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("country_code", record.getCountryCode())
            .add("region", record.getRegion())
            .add("postal_code", record.getPostalCode())
            .add("min_subtotal", record.getMinSubTotal())
            .add("min_weight_oz", record.getMinWeightOz())
            .add("shipping_fee", record.getShippingFee())
            .add("handling_fee", record.getHandlingFee())
            .add("shipping_code", record.getShippingCode())
            .add("shipping_method", record.getShippingMethodId())
            .add("display_text", record.getDisplayText())
            .add("exclude_skus", record.getExcludeSkus());
//            .addIfExists("created_by", record.getCreatedBy(), -1)
//            .addIfExists("modified_by", record.getModifiedBy(), -1);
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static ShippingRate update(ShippingRate record) {
    SqlUtils updateValues = new SqlUtils()
        .add("country_code", record.getCountryCode())
        .add("region", record.getRegion())
        .add("postal_code", record.getPostalCode())
        .add("min_subtotal", record.getMinSubTotal())
        .add("min_weight_oz", record.getMinWeightOz())
        .add("shipping_fee", record.getShippingFee())
        .add("handling_fee", record.getHandlingFee())
        .add("shipping_code", record.getShippingCode())
        .add("shipping_method", record.getShippingMethodId())
        .add("display_text", record.getDisplayText())
        .add("exclude_skus", record.getExcludeSkus());
//        .add("modified_by", record.getModifiedBy(), -1)
//        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("rate_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(ShippingRate record) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("rate_id = ?", record.getId())) > 0;
  }

  private static ShippingRate buildRecord(ResultSet rs) {
    try {
      ShippingRate record = new ShippingRate();
      record.setId(rs.getLong("rate_id"));
      record.setCountryCode(rs.getString("country_code"));
      record.setRegion(rs.getString("region"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setMinSubTotal(rs.getBigDecimal("min_subtotal"));
      record.setMinWeightOz(rs.getInt("min_weight_oz"));
      record.setShippingFee(rs.getBigDecimal("shipping_fee"));
      record.setHandlingFee(rs.getBigDecimal("handling_fee"));
      record.setShippingCode(rs.getString("shipping_code"));
      record.setShippingMethodId(rs.getInt("shipping_method"));
      record.setDisplayText(rs.getString("display_text"));
      record.setExcludeSkus(rs.getString("exclude_skus"));
      // joined tables
      record.setDescription(rs.getString("title"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
