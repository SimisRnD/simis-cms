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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves admin-managed URL redirect rules (issue #408), replacing the legacy
 * CMS_PATH/config/cms/redirects.csv file.
 *
 * <p>
 * {@link #findByFromPath(String)} is the loader function for {@code CacheManager.WEB_REDIRECT_CACHE},
 * a Caffeine {@code LoadingCache} keyed by from_path (see {@code LoadWebRedirectCommand}, mirroring
 * {@code ContentRepository::findByUniqueId} / {@code CacheManager.CONTENT_UNIQUE_ID_CACHE}) -- it is
 * used both there and for the admin form's duplicate-path check, since a disabled row must still
 * count as a from_path conflict, and {@code WebRequestFilter} needs to be able to tell "disabled"
 * apart from "no row at all" (see its class doc). {@link #findEnabledByFromPath(String)} is a
 * convenience, enabled-only counterpart kept for callers that only ever care about a live redirect.
 * </p>
 *
 * <p>
 * {@link #add(WebRedirect)}, {@link #update(WebRedirect)}, and {@link #remove(WebRedirect)} all
 * invalidate the affected from_path key(s) in {@code CacheManager.WEB_REDIRECT_CACHE} on a
 * successful write, mirroring {@code ContentRepository}/{@code CollectionRepository}'s pattern of
 * doing cache invalidation in the repository layer rather than in callers. {@link #update} in
 * particular can rename {@code fromPath} (see {@code WebRedirectRepositoryTest
 * .updateCanRenameTheFromPath()}), so it invalidates both the new key and -- when it differs -- the
 * previous one, the same "look up the previous record first" shape {@code WebPageRepository#update}
 * uses for its own renamable unique key ({@code link}).
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebRedirectRepository {

  private static Log LOG = LogFactory.getLog(WebRedirectRepository.class);

  private static String TABLE_NAME = "web_redirects";
  private static String[] PRIMARY_KEY = new String[] { "web_redirect_id" };

  public static List<WebRedirect> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, null, new DataConstraints().setDefaultColumnToSortBy("from_path"),
        WebRedirectRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<WebRedirect>) result.getRecords();
    }
    return new ArrayList<>();
  }

  public static WebRedirect findById(long id) {
    if (id == -1) {
      return null;
    }
    return (WebRedirect) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("web_redirect_id = ?", id),
        WebRedirectRepository::buildRecord);
  }

  /** The redirect for {@code fromPath}, enabled or not -- used by the admin form's duplicate-path check. */
  public static WebRedirect findByFromPath(String fromPath) {
    if (fromPath == null) {
      return null;
    }
    return (WebRedirect) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("from_path = ?", fromPath),
        WebRedirectRepository::buildRecord);
  }

  /**
   * The enabled redirect for {@code fromPath}, or {@code null} if there isn't one or it is
   * disabled. A convenience for a caller that only ever wants a live redirect and has no need to
   * distinguish "disabled" from "missing" -- {@code CacheManager.WEB_REDIRECT_CACHE} uses the
   * unqualified {@link #findByFromPath(String)} instead, precisely so {@code WebRequestFilter} can
   * make that distinction (see its class doc).
   */
  public static WebRedirect findEnabledByFromPath(String fromPath) {
    if (fromPath == null) {
      return null;
    }
    return (WebRedirect) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("from_path = ?", fromPath).add("enabled = ?", true),
        WebRedirectRepository::buildRecord);
  }

  public static WebRedirect save(WebRedirect record) {
    if (record.getId() != null && record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static WebRedirect add(WebRedirect record) {
    SqlUtils insertValues = new SqlUtils()
        .add("from_path", record.getFromPath())
        .add("to_url", record.getToUrl())
        .add("status_code", record.getStatusCode())
        .add("enabled", record.getEnabled())
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    // Belt-and-suspenders: the LoadingCache's loader never caches a null (not-found) result (see
    // CacheManager.WEB_REDIRECT_CACHE), so there is normally nothing stale to evict for a brand-new
    // from_path -- but invalidate anyway so a same-path add()/remove()/add() sequence (e.g. the admin
    // recreating a redirect they just deleted) can't leave a request served by an evicted-but-not-yet-
    // reloaded value from a prior generation of this key.
    CacheManager.invalidateKey(CacheManager.WEB_REDIRECT_CACHE, record.getFromPath());
    return record;
  }

  public static WebRedirect update(WebRedirect record) {
    // Look up the previous fromPath before overwriting it -- fromPath is editable (see
    // updateCanRenameTheFromPath()) and is the cache key, so a rename must evict both the new key
    // (to pick up this update) and the old one (which would otherwise keep resolving to the
    // redirect's pre-rename destination until its TTL expires). Mirrors WebPageRepository#update's
    // "previousRecord" pattern for its own renamable unique key (link).
    WebRedirect previousRecord = findById(record.getId());
    SqlUtils updateValues = new SqlUtils()
        .add("from_path", record.getFromPath())
        .add("to_url", record.getToUrl())
        .add("status_code", record.getStatusCode())
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils().add("web_redirect_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      CacheManager.invalidateKey(CacheManager.WEB_REDIRECT_CACHE, record.getFromPath());
      if (previousRecord != null && !previousRecord.getFromPath().equals(record.getFromPath())) {
        CacheManager.invalidateKey(CacheManager.WEB_REDIRECT_CACHE, previousRecord.getFromPath());
      }
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(WebRedirect record) {
    boolean removed = DB.deleteFrom(TABLE_NAME, new SqlUtils().add("web_redirect_id = ?", record.getId())) > 0;
    if (removed) {
      CacheManager.invalidateKey(CacheManager.WEB_REDIRECT_CACHE, record.getFromPath());
    }
    return removed;
  }

  private static WebRedirect buildRecord(ResultSet rs) {
    try {
      WebRedirect record = new WebRedirect();
      record.setId(rs.getLong("web_redirect_id"));
      record.setFromPath(rs.getString("from_path"));
      record.setToUrl(rs.getString("to_url"));
      record.setStatusCode(rs.getInt("status_code"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
