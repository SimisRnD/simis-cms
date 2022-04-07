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

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/18 11:44 AM
 */
public class RoleRepository {

  private static Log LOG = LogFactory.getLog(RoleRepository.class);

  private static String TABLE_NAME = "lookup_role";

  public static Role findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (Role) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("code = ?", code),
        RoleRepository::buildRecord);
  }

  public static Role findById(int id) {
    if (id == -1) {
      return null;
    }
    return (Role) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("role_id = ?", id),
        RoleRepository::buildRecord);
  }

  public static List<Role> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM user_roles WHERE role_id = lookup_role.role_id AND user_id = ?)", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("role_id").setUseCount(false),
        RoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Role>) result.getRecords();
    }
    return null;
  }

  public static List<Role> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("level"),
        RoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Role>) result.getRecords();
    }
    return null;
  }

  /**
   * Build the record from the database
   *
   * @param rs
   * @return
   * @throws SQLException
   */
  private static Role buildRecord(ResultSet rs) {
    try {
      Role record = new Role();
      record.setId(rs.getInt("role_id"));
      record.setLevel(rs.getInt("level"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
