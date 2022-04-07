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

import com.simisinc.platform.application.cms.WebPageXmlLayoutCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
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
 * @created 5/3/18 5:44 PM
 */
public class WebPageRepository {

  private static Log LOG = LogFactory.getLog(WebPageRepository.class);

  private static String TABLE_NAME = "web_pages";
  private static String PRIMARY_KEY[] = new String[]{"web_page_id"};

  private static SqlUtils createWhereStatement(WebPageSpecification specification) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("LOWER(link) = ?", specification.getLink())
          .addIfDataConstantExists("enabled = ?", specification.getEnabled())
          .addIfDataConstantExists("draft = ?", specification.getDraft())
          .addIfDataConstantExists("searchable = ?", specification.getSearchable())
          .addIfDataConstantExists("show_in_sitemap = ?", specification.getInSitemap())
          .addIfDataConstantExists("has_redirect = ?", specification.getHasRedirect());
    }
    return where;
  }

  private static DataResult query(WebPageSpecification specification, DataConstraints constraints) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectAllFrom(TABLE_NAME, where, constraints, WebPageRepository::buildRecord);
  }

  public static WebPage findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebPage) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("web_page_id = ?", id),
        WebPageRepository::buildRecord);
  }

  public static WebPage findByLink(String link) {
    if (StringUtils.isBlank(link)) {
      return null;
    }
    return (WebPage) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(link) = ?", link),
        WebPageRepository::buildRecord);
  }

  public static List<WebPage> findAll() {
    return findAll(null, null);
  }

  public static List<WebPage> findAll(WebPageSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("link");
    DataResult result = query(specification, constraints);
    return (List<WebPage>) result.getRecords();
  }

  public static WebPage save(WebPage record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static WebPage add(WebPage record) {
    String link = StringUtils.trimToNull(record.getLink());
    if (link != null) {
      link = link.toLowerCase();
    }
    SqlUtils insertValues = new SqlUtils()
        .add("link", link)
        .add("redirect_url", StringUtils.trimToNull(record.getRedirectUrl()))
        .add("page_title", StringUtils.trimToNull(record.getTitle()))
        .add("page_keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("page_description", StringUtils.trimToNull(record.getDescription()))
        .add("draft", record.getDraft())
        .add("enabled", record.isEnabled())
        .add("searchable", record.isSearchable())
        .add("show_in_sitemap", record.getShowInSitemap())
        .add("created_by", record.getCreatedBy())
        .add("role_id_list", record.getRoleIdList())
        .add("page_xml", record.getPageXml())
        .add("draft_page_xml", StringUtils.trimToNull(record.getDraftPageXml()))
        .add("comments", record.getComments())
        .add("page_image_url", record.getImageUrl())
        .add("has_redirect", StringUtils.trimToNull(record.getRedirectUrl()) != null);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static WebPage update(WebPage record) {
    String link = StringUtils.trimToNull(record.getLink());
    if (link != null) {
      link = link.toLowerCase();
    }
    // Before the update, retrieve the existing link for this page in case it changed
    WebPage previousRecord = WebPageRepository.findById(record.getId());
    // Update the record
    SqlUtils updateValues = new SqlUtils()
        .add("link", link)
        .add("redirect_url", StringUtils.trimToNull(record.getRedirectUrl()))
        .add("page_title", StringUtils.trimToNull(record.getTitle()))
        .add("page_keywords", StringUtils.trimToNull(record.getKeywords()))
        .add("page_description", StringUtils.trimToNull(record.getDescription()))
        .add("draft", record.getDraft())
        .add("enabled", record.isEnabled())
        .add("searchable", record.isSearchable())
        .add("show_in_sitemap", record.getShowInSitemap())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("modified_by", record.getModifiedBy())
        .add("role_id_list", record.getRoleIdList())
        .add("page_xml", record.getPageXml())
        .add("draft_page_xml", StringUtils.trimToNull(record.getDraftPageXml()))
        .add("comments", record.getComments())
        .add("page_image_url", record.getImageUrl())
        .add("has_redirect", StringUtils.trimToNull(record.getRedirectUrl()) != null);
    SqlUtils where = new SqlUtils()
        .add("web_page_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // Force the page(s) to re-cache
      if (previousRecord != null) {
        WebPageXmlLayoutCommand.removeCustomPage(previousRecord.getLink());
      }
      WebPageXmlLayoutCommand.removeCustomPage(record.getLink());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void publish(WebPage record) {
    if (record.getId() == -1) {
      return;
    }
    // Handle publishing and making sure there is content to publish
    String set = "page_xml = draft_page_xml, draft_page_xml = null, draft = false";
    SqlUtils where = new SqlUtils().add("draft_page_xml IS NOT NULL AND web_page_id = ?", record.getId());
    if (DB.update(TABLE_NAME, set, where)) {
      // Force the page to re-cache
      WebPageXmlLayoutCommand.removeCustomPage(record.getLink());
    }
  }

  public static void markAsModified(WebPage record, long userId) {
    SqlUtils updateValues = new SqlUtils()
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("modified_by", userId);
    SqlUtils where = new SqlUtils()
        .add("web_page_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // Now update the record for additional workflows
    record.setModifiedBy(userId);
  }

  public static void removeDraft(WebPage record) {
    if (record == null || record.getId() == -1) {
      return;
    }
    String set = "draft_page_xml = null, draft = false";
    SqlUtils where = new SqlUtils().add("web_page_id = ?", record.getId());
    if (DB.update(TABLE_NAME, set, where)) {
      // Force the page to re-cache
      WebPageXmlLayoutCommand.removeCustomPage(record.getLink());
    }
  }

  public static void remove(WebPage record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("web_page_id = ?", record.getId()));
    // Force the page to re-cache
    WebPageXmlLayoutCommand.removeCustomPage(record.getLink());
  }

  private static WebPage buildRecord(ResultSet rs) {
    try {
      WebPage record = new WebPage();
      record.setId(rs.getLong("web_page_id"));
      record.setLink(rs.getString("link"));
      record.setRedirectUrl(rs.getString("redirect_url"));
      record.setTitle(rs.getString("page_title"));
      record.setKeywords(rs.getString("page_keywords"));
      record.setDescription(rs.getString("page_description"));
      record.setDraft(rs.getBoolean("draft"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setRoleIdList(rs.getString("role_id_list"));
      record.setPageXml(rs.getString("page_xml"));
      record.setDraftPageXml(rs.getString("draft_page_xml"));
      record.setComments(rs.getString("comments"));
      record.setImageUrl(rs.getString("page_image_url"));
      record.setSearchable(rs.getBoolean("searchable"));
      record.setShowInSitemap(rs.getBoolean("show_in_sitemap"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
