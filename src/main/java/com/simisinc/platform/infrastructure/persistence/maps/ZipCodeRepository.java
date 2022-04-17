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

package com.simisinc.platform.infrastructure.persistence.maps;

import com.simisinc.platform.domain.model.maps.ZipCode;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Persists and retrieves zip code objects
 *
 * @author matt rajkowski
 * @created 5/29/18 9:54 AM
 */
public class ZipCodeRepository {

  private static Log LOG = LogFactory.getLog(ZipCodeRepository.class);

  private static String TABLE_NAME = "zip_codes";

  public static ZipCode findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    if (code.length() != 5) {
      return null;
    }
    SqlUtils where = new SqlUtils();
    where.add("code = ?", code);
    return (ZipCode) DB.selectRecordFrom(
        TABLE_NAME, where,
        ZipCodeRepository::buildRecord);
  }

  private static ZipCode buildRecord(ResultSet rs) {
    try {
      ZipCode record = new ZipCode();
      record.setCode(rs.getString("code"));
      record.setType(rs.getString("code_type"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
