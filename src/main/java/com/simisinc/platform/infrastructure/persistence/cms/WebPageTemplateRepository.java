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

import com.simisinc.platform.domain.model.cms.WebPageTemplate;
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
 * Description
 *
 * @author matt rajkowski
 * @created 5/7/18 7:35 AM
 */
public class WebPageTemplateRepository {

  private static Log LOG = LogFactory.getLog(WebPageTemplateRepository.class);

  private static String TABLE_NAME = "web_page_templates";
  private static String PRIMARY_KEY[] = new String[]{"template_id"};

  private static DataResult query(WebPageTemplateSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("template_id = ?", specification.getId(), -1);
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, WebPageTemplateRepository::buildRecord);
  }

  public static WebPageTemplate findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebPageTemplate) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("template_id = ?", id),
        WebPageTemplateRepository::buildRecord);
  }

  public static WebPageTemplate findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (WebPageTemplate) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("name = ?", name),
        WebPageTemplateRepository::buildRecord);
  }

  public static List<WebPageTemplate> findAll() {
    return findAll(null, null);
  }

  public static List<WebPageTemplate> findAll(WebPageTemplateSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("template_order, name");
    DataResult result = query(specification, constraints);
    return (List<WebPageTemplate>) result.getRecords();
  }

  public static WebPageTemplate save(WebPageTemplate record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static WebPageTemplate add(WebPageTemplate record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("image_path", StringUtils.trimToNull(record.getImagePath()))
        .add("page_xml", StringUtils.trimToNull(record.getPageXml()))
        .add("template_order", record.getTemplateOrder())
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("css", StringUtils.trimToNull(record.getCss()))
        .add("category", StringUtils.trimToNull(record.getCategory()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static WebPageTemplate update(WebPageTemplate record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("image_path", StringUtils.trimToNull(record.getImagePath()))
        .add("page_xml", StringUtils.trimToNull(record.getPageXml()))
        .add("template_order", record.getTemplateOrder())
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("css", StringUtils.trimToNull(record.getCss()))
        .add("category", StringUtils.trimToNull(record.getCategory()));
    SqlUtils where = new SqlUtils()
        .add("template_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static WebPageTemplate buildRecord(ResultSet rs) {
    try {
      WebPageTemplate record = new WebPageTemplate();
      record.setId(rs.getLong("template_id"));
      record.setName(rs.getString("name"));
      record.setImagePath(rs.getString("image_path"));
      record.setPageXml(rs.getString("page_xml"));
      record.setTemplateOrder(rs.getInt("template_order"));
      record.setDescription(rs.getString("description"));
//      WebPageTemplateRuleListJSONCommand.populateFromJSONString(record, rs.getString("rules"));
      record.setCss(rs.getString("css"));
      record.setCategory(rs.getString("category"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
