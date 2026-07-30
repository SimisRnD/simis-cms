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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves capability objects (issue #701)
 *
 * @author elizabeth houser
 */
public class CapabilityRepository {

  private static Log LOG = LogFactory.getLog(CapabilityRepository.class);

  private static String TABLE_NAME = "capabilities";

  public static Capability findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (Capability) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("code = ?", code),
        CapabilityRepository::buildRecord);
  }

  /**
   * All capabilities granted to any role the given user holds (user_roles -> role_capabilities).
   * Mirrors RoleRepository.findAllByUserId's EXISTS-subquery shape.
   */
  public static List<Capability> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (" +
            "SELECT 1 FROM role_capabilities rc " +
            "JOIN user_roles ur ON ur.role_id = rc.role_id " +
            "WHERE rc.capability_id = capabilities.capability_id AND ur.user_id = ?)", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("code").setUseCount(false),
        CapabilityRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Capability>) result.getRecords();
    }
    return null;
  }

  public static List<Capability> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("code"),
        CapabilityRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Capability>) result.getRecords();
    }
    return null;
  }

  private static Capability buildRecord(ResultSet rs) {
    try {
      Capability record = new Capability();
      record.setId(rs.getLong("capability_id"));
      record.setCode(rs.getString("code"));
      record.setCategory(rs.getString("category"));
      record.setDescription(rs.getString("description"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
