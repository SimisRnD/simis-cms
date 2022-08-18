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

import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Persists and retrieves pricing rule objects
 *
 * @author matt rajkowski
 * @created 11/21/19 8:53 PM
 */
public class PricingRuleRepository {

  private static Log LOG = LogFactory.getLog(PricingRuleRepository.class);

  private static String TABLE_NAME = "pricing_rules";
  private static String[] PRIMARY_KEY = new String[]{"rule_id"};

  private static DataResult query(PricingRuleSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils();
      if (StringUtils.isNotBlank(specification.getCountryCode())) {
        where.add("valid_country_code IS NULL OR valid_country_code = ?", specification.getCountryCode());
      }
      if (StringUtils.isNotBlank(specification.getPromoCode())) {
        where.add("upper(promo_code) = ?", specification.getPromoCode().toUpperCase());
      }
      if (specification.getEnabled() != DataConstants.UNDEFINED) {
        where.add("enabled = ?", specification.getEnabled() == DataConstants.TRUE);
      }
      if (specification.getIsValidToday() == DataConstants.TRUE) {
        where.add("(from_date IS NULL OR from_date <= CURRENT_TIMESTAMP)");
        where.add("(to_date IS NULL OR to_date > CURRENT_TIMESTAMP)");
      }
      if (specification.getHasPromoCode() != DataConstants.UNDEFINED) {
        where.add("promo_code IS NULL OR promo_code = ''");
      }
      if (StringUtils.isNotBlank(specification.getIncludesSku())) {
        // @todo use a JSON field type to improve accuracy
        where.add("LOWER(valid_skus) LIKE LOWER(?) ESCAPE '!'", "%" + specification.getIncludesSku().toLowerCase() + "%");
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, PricingRuleRepository::buildRecord);
  }

  public static List<PricingRule> findAll(PricingRuleSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("to_date desc, created");
    DataResult result = query(specification, constraints);
    return (List<PricingRule>) result.getRecords();
  }

  public static List<PricingRule> findAllRulesByValidSku(String sku) {
    PricingRuleSpecification specification = new PricingRuleSpecification();
    specification.setEnabled(true);
    specification.setHasPromoCode(false);
    specification.setIncludesSku(sku);
    DataResult result = query(specification, null);
    List<PricingRule> recordList = (List<PricingRule>) result.getRecords();
    // The query is currently not precise, so fish out the matches
    List<PricingRule> pricingRuleList = new ArrayList<>();
    for (PricingRule record : recordList) {
      List<String> validSkuList = Stream.of(record.getValidSkus().toUpperCase().split(Pattern.quote(",")))
          .map(String::trim)
          .collect(toList());
      if (validSkuList.contains(sku.toUpperCase())) {
        pricingRuleList.add(record);
      }
    }
    return pricingRuleList;
  }

  public static PricingRule findById(long ruleId) {
    return (PricingRule) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("rule_id = ?", ruleId),
        PricingRuleRepository::buildRecord);
  }


  public static PricingRule save(PricingRule record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static PricingRule add(PricingRule record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("name", record.getName())
            .add("description", record.getDescription())
            .add("error_message", record.getErrorMessage())
            .add("from_date", record.getFromDate())
            .add("to_date", record.getToDate())
            .add("promo_code", record.getPromoCode())
            .add("uses_per_code", record.getUsesPerCode())
            .add("uses_per_customer", record.getUsesPerCustomer())
            .add("times_used", record.getTimesUsed())
            .add("created_by", record.getCreatedBy())
            .add("modified_by", record.getModifiedBy())
            .add("enabled", record.getEnabled())
            .add("minimum_subtotal", record.getMinimumSubtotal())
            .add("minimum_order_qty", record.getMinimumOrderQuantity())
            .add("maximum_order_qty", record.getMaximumOrderQuantity())
            .add("item_limit", record.getItemLimit())
            .add("valid_skus", record.getValidSkus())
            .add("invalid_skus", record.getInvalidSkus())
            .add("subtotal_percent", record.getSubtotalPercent())
            .add("subtract_amount", record.getSubtractAmount())
            .add("buy_x_items", record.getBuyXItems())
            .add("get_y_free", record.getGetYItemsFree())
            .add("free_shipping", record.getFreeShipping())
            .add("free_product_sku", record.getFreeProductSku())
            .add("free_shipping_code", record.getFreeShippingCode())
            .add("valid_country_code", record.getCountryCode());
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

  public static PricingRule update(PricingRule record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", record.getName())
        .add("description", record.getDescription())
        .add("error_message", record.getErrorMessage())
        .add("from_date", record.getFromDate())
        .add("to_date", record.getToDate())
        .add("promo_code", record.getPromoCode())
        .add("uses_per_code", record.getUsesPerCode())
        .add("uses_per_customer", record.getUsesPerCustomer())
//        .add("times_used", record.getTimesUsed())
        .add("enabled", record.getEnabled())
        .add("minimum_subtotal", record.getMinimumSubtotal())
        .add("minimum_order_qty", record.getMinimumOrderQuantity())
        .add("maximum_order_qty", record.getMaximumOrderQuantity())
        .add("item_limit", record.getItemLimit())
        .add("valid_skus", record.getValidSkus())
        .add("invalid_skus", record.getInvalidSkus())
        .add("subtotal_percent", record.getSubtotalPercent())
        .add("subtract_amount", record.getSubtractAmount())
        .add("buy_x_items", record.getBuyXItems())
        .add("get_y_free", record.getGetYItemsFree())
        .add("free_shipping", record.getFreeShipping())
        .add("free_product_sku", record.getFreeProductSku())
        .add("free_shipping_code", record.getFreeShippingCode())
        .add("valid_country_code", record.getCountryCode())
        .add("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("rule_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static PricingRule buildRecord(ResultSet rs) {
    try {
      PricingRule record = new PricingRule();
      record.setId(rs.getLong("rule_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setErrorMessage(rs.getString("error_message"));
      record.setFromDate(rs.getTimestamp("from_date"));
      record.setToDate(rs.getTimestamp("to_date"));
      record.setPromoCode(rs.getString("promo_code"));
      record.setUsesPerCode(DB.getInt(rs, "uses_per_code", 0));
      record.setTimesUsed(rs.getInt("times_used"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setMinimumSubtotal(rs.getBigDecimal("minimum_subtotal"));
      record.setMinimumOrderQuantity(rs.getInt("minimum_order_qty"));
      record.setMaximumOrderQuantity(rs.getInt("maximum_order_qty"));
      record.setValidSkus(rs.getString("valid_skus"));
      record.setInvalidSkus(rs.getString("invalid_skus"));
      record.setSubtotalPercent(rs.getInt("subtotal_percent"));
      record.setSubtractAmount(rs.getBigDecimal("subtract_amount"));
      record.setFreeShipping(rs.getBoolean("free_shipping"));
      record.setFreeProductSku(rs.getString("free_product_sku"));
      record.setFreeShippingCode(rs.getString("free_shipping_code"));
      record.setCountryCode(rs.getString("valid_country_code"));
      record.setUsesPerCustomer(DB.getInt(rs, "uses_per_customer", 0));
      record.setItemLimit(DB.getInt(rs, "item_limit", 0));
      record.setBuyXItems(DB.getInt(rs, "buy_x_items", 0));
      record.setGetYItemsFree(DB.getInt(rs, "get_y_free", 0));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
