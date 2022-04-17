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

import com.simisinc.platform.domain.model.ecommerce.USSalesTaxRate;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves US sales tax rates objects
 *
 * @author matt rajkowski
 * @created 4/25/19 8:00 AM
 */
public class USSalesTaxRatesRepository {

  private static Log LOG = LogFactory.getLog(USSalesTaxRatesRepository.class);

  private static String TABLE_NAME = "us_sales_tax_rates";

  private static DataResult query(USSalesTaxRatesSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      if (specification.getStateAbbreviation() != null) {
        where.add("state = ?", specification.getStateAbbreviation().toUpperCase());
      }
      if (specification.getZipCode() != null) {
        String zipCode = specification.getZipCode();
        if (zipCode.length() > 5) {
          zipCode = zipCode.substring(0, 5);
        }
        where.add("zip_code = ?", zipCode);
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, USSalesTaxRatesRepository::buildRecord);
  }

  public static List<USSalesTaxRate> findAll(USSalesTaxRatesSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("population desc");
    DataResult result = query(specification, constraints);
    return (List<USSalesTaxRate>) result.getRecords();
  }

  public static USSalesTaxRate findByStateZipCode(String state, String zipCode) {
    if (StringUtils.isBlank(state)) {
      return null;
    }
    if (StringUtils.isBlank(zipCode)) {
      return null;
    }
    SqlUtils where = new SqlUtils();
    where.add("state = ?", state.toUpperCase());
    if (zipCode.length() > 5) {
      zipCode = zipCode.substring(0, 5);
    }
    where.add("zip_code = ?", zipCode);
    return (USSalesTaxRate) DB.selectRecordFrom(
        TABLE_NAME, where,
        USSalesTaxRatesRepository::buildRecord);
  }

  public static USSalesTaxRate findByState(String state) {
    if (StringUtils.isBlank(state)) {
      return null;
    }
    SqlUtils where = new SqlUtils();
    where.add("state = ?", state.toUpperCase());
    return (USSalesTaxRate) DB.selectRecordFrom(
        TABLE_NAME, where,
        USSalesTaxRatesRepository::buildRecord);
  }

  private static USSalesTaxRate buildRecord(ResultSet rs) {
    try {
      USSalesTaxRate record = new USSalesTaxRate();
      record.setStateAbbreviation(rs.getString("state"));
      record.setZipCode(rs.getString("zip_code"));
      record.setRegionName(rs.getString("region_name"));
      record.setCombinedRate(rs.getBigDecimal("combined_rate"));
      record.setRiskLevel(rs.getInt("risk_level"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
