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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionRole;
import com.simisinc.platform.domain.model.items.Member;
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
 * @created 8/24/18 10:24 AM
 */
public class CollectionRoleRepository {

  private static Log LOG = LogFactory.getLog(CollectionRoleRepository.class);

  private static String TABLE_NAME = "lookup_collection_role";
  private static String PRIMARY_KEY[] = new String[]{"role_id"};

  public static CollectionRole findById(long id) {
    if (id == -1) {
      return null;
    }
    return (CollectionRole) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("role_id = ?", id),
        CollectionRoleRepository::buildRecord);
  }

  public static CollectionRole findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (CollectionRole) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("code = ?", code),
        CollectionRoleRepository::buildRecord);
  }

  public static List<CollectionRole> findAllAvailableForCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils().add("collection_id IS NULL OR collection_id = ?", collectionId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("level,role_id").setUseCount(false),
        CollectionRoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionRole>) result.getRecords();
    }
    return null;
  }

  public static List<CollectionRole> findAllByMember(Member member) {
    if (member.getItemId() == -1 || member.getUserId() == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM member_roles WHERE role_id = lookup_collection_role.role_id AND item_id = ? AND user_id = ?)", new Long[]{member.getItemId(), member.getUserId()});
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("role_id").setUseCount(false),
        CollectionRoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionRole>) result.getRecords();
    }
    return null;
  }

  public static List<CollectionRole> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("title"),
        CollectionRoleRepository::buildRecord);
    return (List<CollectionRole>) result.getRecords();
  }

  public static CollectionRole save(CollectionRole record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static CollectionRole add(CollectionRole record) {
    SqlUtils insertValues = new SqlUtils()
        .add("code", StringUtils.trimToNull(record.getCode()))
        .addIfExists("collection_id", record.getCollectionId(), -1)
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("archived", record.getArchived());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static CollectionRole update(CollectionRole record) {
    SqlUtils updateValues = new SqlUtils()
        .add("code", StringUtils.trimToNull(record.getCode()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("archived", record.getArchived());
    SqlUtils where = new SqlUtils()
        .add("role_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void remove(CollectionRole record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("role_id = ?", record.getId()));
  }

  public static void remove(Collection record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("collection_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", collection.getId()));
  }

  private static CollectionRole buildRecord(ResultSet rs) {
    try {
      CollectionRole record = new CollectionRole();
      record.setId(rs.getLong("role_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
