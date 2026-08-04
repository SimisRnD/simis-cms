/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves draft preview link tokens (#419) -- a time-limited bearer token that
 * lets a visitor holding the link view a web page's current draft content before it's published.
 *
 * @author SimIS Inc.
 * @created 8/4/2026
 */
public class WebPagePreviewTokenRepository {

  private static Log LOG = LogFactory.getLog(WebPagePreviewTokenRepository.class);

  private static String TABLE_NAME = "web_page_preview_tokens";
  private static String[] PRIMARY_KEY = new String[]{"web_page_preview_token_id"};

  public static WebPagePreviewToken add(WebPagePreviewToken record) {
    SqlUtils insertValues = new SqlUtils()
        .add("web_page_id", record.getWebPageId())
        .add("page_path", record.getPagePath())
        .add("token", record.getToken())
        .add("expires_at", record.getExpiresAt())
        .add("created_by", record.getCreatedBy(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /**
   * Returns the token record only if it exists, matches the given page AND the exact path it was
   * minted for, and has not expired. The path check matters for a wildcard page (e.g. "/news/*"):
   * without it, a token minted while previewing one URL would also validate for every other URL
   * backed by that same WebPage row.
   */
  public static WebPagePreviewToken findValidToken(String token, long webPageId, String pagePath) {
    if (StringUtils.isBlank(token) || webPageId == -1 || StringUtils.isBlank(pagePath)) {
      return null;
    }
    return (WebPagePreviewToken) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("token = ?", token)
            .add("web_page_id = ?", webPageId)
            .add("page_path = ?", pagePath)
            .add("expires_at > ?", new Timestamp(System.currentTimeMillis())),
        WebPagePreviewTokenRepository::buildRecord);
  }

  /**
   * Deletes every outstanding token for a page (#419 review finding): called whenever its draft is
   * published or discarded, so a still-unexpired, previously-issued link can never later resurface
   * a different, unrelated draft than the one it was generated against.
   */
  public static void removeAllForPage(Connection connection, long webPageId) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("web_page_id = ?", webPageId));
  }

  /** Non-transactional variant for call sites that don't already hold a connection. */
  public static void removeAllForPage(long webPageId) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("web_page_id = ?", webPageId));
  }

  private static WebPagePreviewToken buildRecord(ResultSet rs) {
    try {
      WebPagePreviewToken record = new WebPagePreviewToken();
      record.setId(rs.getLong("web_page_preview_token_id"));
      record.setWebPageId(rs.getLong("web_page_id"));
      record.setPagePath(rs.getString("page_path"));
      record.setToken(rs.getString("token"));
      record.setExpiresAt(rs.getTimestamp("expires_at"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
