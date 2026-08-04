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

import java.sql.Timestamp;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.persistence.cms.WebPagePreviewTokenRepository;

/**
 * Issues a time-limited bearer token (#419) that lets a visitor holding the link view a web
 * page's current draft content at its real URL, before the draft is reviewed or published.
 * Modeled on {@code UserRepository.createAccountToken}: a plaintext {@link UUID}, expiry enforced
 * SQL-side by every lookup ({@link WebPagePreviewTokenRepository#findValidToken}) rather than a
 * separate cleanup job.
 *
 * @author SimIS Inc.
 * @created 8/4/2026
 */
public class GeneratePreviewLinkCommand {

  private static final int DEFAULT_TTL_HOURS = 24;

  private GeneratePreviewLinkCommand() {
    // Static command
  }

  /**
   * @param pagePath the exact request path the visitor was previewing (not just {@code
   *        webPage.getLink()}) -- required so the token can be scoped to that one URL rather than
   *        the whole page template, which matters for a wildcard page like "/news/*" that backs
   *        many distinct URLs from a single {@code WebPage} row.
   */
  public static WebPagePreviewToken generateFor(WebPage webPage, String pagePath, long createdBy) throws DataException {
    if (webPage == null || webPage.getId() == -1) {
      throw new DataException("A valid web page is required");
    }
    if (StringUtils.isBlank(pagePath)) {
      throw new DataException("A valid page path is required");
    }
    int ttlHours = resolvePreviewLinkTtlHours(LoadSitePropertyCommand.loadByName("security.previewLinkTtlHours"));
    WebPagePreviewToken record = new WebPagePreviewToken();
    record.setWebPageId(webPage.getId());
    record.setPagePath(pagePath);
    record.setToken(UUID.randomUUID().toString());
    record.setExpiresAt(new Timestamp(System.currentTimeMillis() + (ttlHours * 3600L * 1000L)));
    record.setCreatedBy(createdBy);
    WebPagePreviewToken savedRecord = WebPagePreviewTokenRepository.add(record);
    if (savedRecord == null) {
      throw new DataException("The preview link could not be created");
    }
    return savedRecord;
  }

  /** @return the configured TTL in hours, or {@link #DEFAULT_TTL_HOURS} for a blank/invalid value. */
  public static int resolvePreviewLinkTtlHours(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_TTL_HOURS;
    }
    int hours;
    try {
      hours = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_TTL_HOURS;
    }
    return hours > 0 ? hours : DEFAULT_TTL_HOURS;
  }
}
