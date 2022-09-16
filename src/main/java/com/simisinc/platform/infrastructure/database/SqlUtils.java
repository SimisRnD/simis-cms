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

import com.simisinc.platform.presentation.controller.DataConstants;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of insert/update/where properties and values
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class SqlUtils {

  private List<SqlValue> values = new ArrayList<>();

  public SqlUtils add(SqlValue object) {
    values.add(object);
    return this;
  }

  public SqlUtils add(String name) {
    values.add(new SqlValue(name));
    return this;
  }

  public SqlUtils addNames(String... names) {
    if (names.length > 0) {
      for (String name : names) {
        values.add(new SqlValue(name));
      }
    }
    return this;
  }

  public SqlUtils add(String name, String value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils add(String name, String[] value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils add(String name, String value, int maxLength) {
    if (value != null && value.length() > maxLength) {
      value = value.substring(0, maxLength);
    }
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, String value) {
    if (value != null) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils addIfExists(String name, String value, int maxLength) {
    if (value != null) {
      if (value.length() > maxLength) {
        value = value.substring(0, maxLength);
      }
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, long value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils add(String name, Long[] value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, long value, long emptyValue) {
    if (value != emptyValue) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, long value, long nullValue) {
    if (value == nullValue) {
      values.add(new SqlValue(name, value, true));
    } else {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, int value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, int value, int emptyValue) {
    if (value != emptyValue) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, int value, int nullValue) {
    if (value == nullValue) {
      values.add(new SqlValue(name, value, true));
    } else {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, double value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, double value, double emptyValue) {
    if (value != emptyValue) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, double value, double nullValue) {
    if (value == nullValue) {
      values.add(new SqlValue(name, value, true));
    } else {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils addWhen(String name, boolean value, boolean checkValue) {
    if (value == checkValue) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils addIfDataConstantExists(String name, int value) {
    if (value != DataConstants.UNDEFINED) {
      add(name, value == DataConstants.TRUE);
    }
    return this;
  }

  public SqlUtils add(String name, Timestamp value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils add(String name, Timestamp[] value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils add(String name, Object[] value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, Timestamp value) {
    if (value != null) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, BigDecimal value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addIfExists(String name, BigDecimal value) {
    if (value != null) {
      values.add(new SqlValue(name, value));
    }
    return this;
  }

  public SqlUtils add(String name, boolean value) {
    values.add(new SqlValue(name, value));
    return this;
  }

  public SqlUtils addGeomPoint(String name, double latitude, double longitude) {
    values.add(new SqlValue(name, SqlValue.GEOM_TYPE, latitude, longitude));
    return this;
  }

  public List<SqlValue> getValues() {
    return values;
  }

}
