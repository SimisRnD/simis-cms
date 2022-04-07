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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.application.cms.ThemeJSONCommand;
import com.simisinc.platform.SiteProperty;
import com.simisinc.platform.domain.model.cms.Theme;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/12/19 1:56 PM
 */
public class ThemeRepository {

  private static Log LOG = LogFactory.getLog(ThemeRepository.class);

  private static String TABLE_NAME = "themes";
  private static String PRIMARY_KEY[] = new String[]{"theme_id"};

  public static Theme findByName(String name) {
    return (Theme) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("LOWER(name) = ?", name.toLowerCase()),
        ThemeRepository::buildRecord);
  }

  public static Theme findById(long id) {
    return (Theme) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("theme_id = ?", id),
        ThemeRepository::buildRecord);
  }

  public static List<Theme> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("theme_id"),
        ThemeRepository::buildRecord);
    return (List<Theme>) result.getRecords();
  }

  public static Theme save(Theme record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Theme save(List<SiteProperty> sitePropertyList, String name) {
    Theme theme = new Theme(name);
    theme.setSiteProperties(sitePropertyList);
    return save(theme);
  }

  public static Theme add(Theme record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()));
    insertValues.add(new SqlValue("entries", SqlValue.JSONB_TYPE, ThemeJSONCommand.createJSONString(record)));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Theme update(Theme record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", record.getName())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    updateValues.add(new SqlValue("entries", SqlValue.JSONB_TYPE, ThemeJSONCommand.createJSONString(record)));
    SqlUtils where = new SqlUtils()
        .add("theme_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void remove(Theme record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("theme_id = ?", record.getId()));
  }

  private static Theme buildRecord(ResultSet rs) {
    try {
      Theme record = new Theme();
      record.setId(rs.getLong("theme_id"));
      record.setName(rs.getString("name"));
      ThemeJSONCommand.populateFromJSONString(record, rs.getString("entries"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
