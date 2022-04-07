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

package com.simisinc.platform.infrastructure.database;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * Represents a database value used in insert/update statements and where clause
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class SqlValue {

  public static final int GEOM_TYPE = 2018052314;
  public static final int JSONB_TYPE = 2018053112;

  // Name/condition/field
  private String field;
  private int sqlType;
  private int castType = -1;
  private boolean isNull = false;
  private boolean hasValue = true;

  // Value by Type
  private String stringValue;
  private String[] stringValues;
  private Long longValue;
  private Long[] longValues;
  private Integer intValue;
  private Double doubleValue;
  private Timestamp timestampValue;
  private Timestamp[] timestampValues;
  private BigDecimal bigDecimalValue;
  private Boolean booleanValue;

  public SqlValue(String fieldOrClause) {
    this.field = fieldOrClause;
    this.hasValue = false;
  }

  public SqlValue(String fieldOrClause, String stringValue) {
    this.field = fieldOrClause;
    this.stringValue = stringValue;
    this.sqlType = Types.VARCHAR;
  }

  public SqlValue(String fieldOrClause, String[] stringValues) {
    this.field = fieldOrClause;
    this.stringValues = stringValues;
    this.sqlType = Types.VARCHAR;
  }

  public SqlValue(String fieldOrClause, long longValue) {
    this.field = fieldOrClause;
    this.longValue = longValue;
    this.sqlType = Types.BIGINT;
  }

  public SqlValue(String fieldOrClause, Long[] longValues) {
    this.field = fieldOrClause;
    this.longValues = longValues;
    this.sqlType = Types.BIGINT;
  }

  public SqlValue(String fieldOrClause, long longValue, boolean isNull) {
    this.field = fieldOrClause;
    this.longValue = longValue;
    this.sqlType = Types.BIGINT;
    this.isNull = isNull;
  }

  public SqlValue(String fieldOrClause, int intValue) {
    this.field = fieldOrClause;
    this.intValue = intValue;
    this.sqlType = Types.INTEGER;
  }

  public SqlValue(String fieldOrClause, int intValue, boolean isNull) {
    this.field = fieldOrClause;
    this.intValue = intValue;
    this.sqlType = Types.INTEGER;
    this.isNull = isNull;
  }

  public SqlValue(String fieldOrClause, double doubleValue) {
    this.field = fieldOrClause;
    this.doubleValue = doubleValue;
    this.sqlType = Types.DOUBLE;
  }

  public SqlValue(String fieldOrClause, double doubleValue, boolean isNull) {
    this.field = fieldOrClause;
    this.doubleValue = doubleValue;
    this.sqlType = Types.DOUBLE;
    this.isNull = isNull;
  }

  public SqlValue(String fieldOrClause, Timestamp timestampValue) {
    this.field = fieldOrClause;
    this.timestampValue = timestampValue;
    this.sqlType = Types.TIMESTAMP;
  }

  public SqlValue(String fieldOrClause, Timestamp[] timestampValues) {
    this.field = fieldOrClause;
    this.timestampValues = timestampValues;
    this.sqlType = Types.TIMESTAMP;
  }

  public SqlValue(String fieldOrClause, BigDecimal bigDecimalValue) {
    this.field = fieldOrClause;
    this.bigDecimalValue = bigDecimalValue;
    this.sqlType = Types.NUMERIC;
  }

  public SqlValue(String fieldOrClause, Boolean booleanValue) {
    this.field = fieldOrClause;
    this.booleanValue = booleanValue;
    this.sqlType = Types.BOOLEAN;
  }

  public SqlValue(String fieldName, int castType, String value) {
    this.field = fieldName;
    if (castType == JSONB_TYPE) {
      this.stringValue = value;
      this.sqlType = Types.VARCHAR;
      this.castType = castType;
    }
  }

  public SqlValue(String fieldName, int type, double latitude, double longitude) {
    this.field = fieldName;
    if (type == GEOM_TYPE) {
      if (latitude == 0 && longitude == 0) {
        this.stringValue = "NULL";
      } else {
        this.stringValue = "ST_SetSRID(ST_MakePoint(" + latitude + ", " + longitude + "), 4326)";
      }
    }
    this.hasValue = false;
  }

  public String getFieldOrClause() {
    return field;
  }

  public String getStringValue() {
    return stringValue;
  }

  public String[] getStringValues() {
    return stringValues;
  }

  public Long getLongValue() {
    return longValue;
  }

  public Long[] getLongValues() {
    return longValues;
  }

  public Integer getIntValue() {
    return intValue;
  }

  public Double getDoubleValue() {
    return doubleValue;
  }

  public Timestamp getTimestampValue() {
    return timestampValue;
  }

  public Timestamp[] getTimestampValues() {
    return timestampValues;
  }

  public BigDecimal getBigDecimalValue() {
    return bigDecimalValue;
  }

  public Boolean getBooleanValue() {
    return booleanValue;
  }

  public int getSqlType() {
    return sqlType;
  }

  public boolean isNull() {
    return isNull;
  }

  public void setNull(boolean aNull) {
    isNull = aNull;
  }

  public boolean hasValue() {
    return hasValue;
  }

  public int getCastType() {
    return castType;
  }
}
