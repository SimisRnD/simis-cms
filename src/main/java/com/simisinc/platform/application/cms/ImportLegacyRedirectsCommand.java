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

package com.simisinc.platform.application.cms;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;

/**
 * One-time, idempotent import of the legacy {@code CMS_PATH/config/cms/redirects.csv} file (issue
 * #408) into the database-backed {@code web_redirects} table.
 *
 * <p>
 * Invoked once per JVM startup from {@code ContextListener}, after the database and cache manager
 * are up -- the same place other one-time startup bootstrapping happens (preloading the content
 * cache, loading the filesystem lists), rather than as a Flyway migration or a dedicated admin
 * button. A CSV file is per-node local configuration, not schema, so it doesn't fit Flyway's
 * "runs once against the shared database" model well (a Flyway migration would only ever see the
 * primary node's copy of the file, and would need its own separate idempotency tracking on top of
 * Flyway's since Flyway's "ran once" bookkeeping says nothing about whether every node's copy of
 * the file has been imported). Running it at startup on every node instead means each node's own
 * file (if any) always gets a chance to be imported, and doing so is safe to repeat -- on every
 * restart, and across nodes that happen to share the same file -- because every row is de-duplicated
 * against the database by from_path before it is inserted, and a from_path that lands in
 * WebRedirectRepository#add as a genuine race is still caught by the table's unique index.
 * </p>
 *
 * <p>
 * A row imported this way is enabled, status 301 (permanent) -- the CSV format has no concept of
 * either -- and attributed to no user (createdBy/modifiedBy left at WebRedirect's -1 default, which
 * WebRedirectRepository maps to a NULL created_by/modified_by rather than violating the
 * users(user_id) foreign key).
 * </p>
 *
 * @author SimIS Inc.
 */
public class ImportLegacyRedirectsCommand {

  private static final Log LOG = LogFactory.getLog(ImportLegacyRedirectsCommand.class);

  private ImportLegacyRedirectsCommand() {
    // Static utility, not instantiated
  }

  /**
   * Reads {@code config/cms/redirects.csv} (if present) via {@link LoadRedirectsCommand#load()} and
   * inserts any row whose from_path isn't already present in {@code web_redirects}. A no-op when the
   * file is absent or empty. Never throws -- an individual row failure (an unsafe/invalid value, or a
   * database error) is logged and skipped rather than aborting the rest of the import.
   */
  public static void importFromCsv() {
    Map<String, String> legacyRedirects = LoadRedirectsCommand.load();
    if (legacyRedirects == null || legacyRedirects.isEmpty()) {
      return;
    }

    int imported = 0;
    int alreadyPresent = 0;
    int skippedInvalid = 0;

    for (Map.Entry<String, String> entry : legacyRedirects.entrySet()) {
      String fromPath = normalizeFromPath(entry.getKey());
      String toUrl = UrlCommand.sanitizeUrl(entry.getValue());
      if (fromPath == null || toUrl == null) {
        skippedInvalid++;
        LOG.warn("Skipping legacy redirect with an unsafe or invalid path: '" + entry.getKey() + "' -> '"
            + entry.getValue() + "'");
        continue;
      }

      if (WebRedirectRepository.findByFromPath(fromPath) != null) {
        // Already imported (or an equivalent admin-created row already exists) -- leave it alone
        // rather than overwrite an admin's edit with the legacy CSV value.
        alreadyPresent++;
        continue;
      }

      WebRedirect redirect = new WebRedirect();
      redirect.setFromPath(fromPath);
      redirect.setToUrl(toUrl);
      redirect.setStatusCode(WebRedirect.PERMANENT);
      redirect.setEnabled(true);
      redirect.setCreatedBy(-1);
      redirect.setModifiedBy(-1);

      if (WebRedirectRepository.add(redirect) != null) {
        imported++;
      } else {
        skippedInvalid++;
        LOG.error("Failed to import legacy redirect for from_path: " + fromPath);
      }
    }

    if (imported > 0 || alreadyPresent > 0 || skippedInvalid > 0) {
      LOG.info("Legacy redirects.csv import: " + imported + " imported, " + alreadyPresent
          + " already present, " + skippedInvalid + " skipped (invalid)");
    }
  }

  /**
   * Legacy rows are not guaranteed to start with a {@code /} the way an admin-entered from_path is
   * required to (see {@code SaveWebRedirectCommand}) -- normalize so the imported row can actually
   * match a request path at {@code WebRequestFilter}, which always compares against an absolute
   * path. Mirrors {@code SaveWebRedirectCommand}'s rejection of an absolute http(s) URL as a
   * from_path (that would never match a request path anyway, and to_url already goes through
   * {@link UrlCommand#sanitizeUrl}, whose own scheme allow-list would otherwise let it through here).
   *
   * @return a leading-slash path, or {@code null} if the entry is blank or an external URL
   */
  private static String normalizeFromPath(String fromPath) {
    String trimmed = StringUtils.trimToNull(fromPath);
    if (trimmed == null || trimmed.startsWith("http:") || trimmed.startsWith("https:")) {
      return null;
    }
    return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
  }
}
