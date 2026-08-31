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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Decides whether a caller may read a content record through the REST API (issue #1701).
 *
 * <p>
 * {@code GET /api/content/{uniqueId}} returned any content record to anyone holding an app key.
 * That mattered because the key is not a credential: {@code RestRequestFilter} admits a GET on a
 * key alone while the site is online, and the Apps screen describes the key as safe to share and
 * safe to embed in client-side scripts. So content placed only on a role-, group- or
 * internal-restricted page was readable by anyone who knew its uniqueId. The write path
 * ({@code ContentService.post}) was already gated; only the read path was not.
 * </p>
 *
 * <p>
 * <b>A content record has no owning page.</b> {@code content_unique_id} carries no foreign key to
 * {@code web_pages}, so the question "may this caller see this content" has to be answered
 * indirectly: find the pages that render it and ask whether any of them is one the caller may
 * open. That is the same association {@code WebPageSearchResultsWidget} already computes to decide
 * what a search may return, and the same two patterns page XML can use to reference content.
 * </p>
 *
 * <p>
 * <b>Any-page, not every-page.</b> If a block is rendered on both a public page and a staff-only
 * one, it is already public — requiring every page to pass would deny content the caller could
 * simply read by opening the public page, which protects nothing and breaks a legitimate read.
 * </p>
 *
 * @author SimIS Inc.
 * @created 8/31/26 5:00 PM
 */
public class ValidateApiAccessToContentCommand {

  /**
   * The escape hatch, not the switch that turns this on. Enforcement is the default: this closes a
   * hole rather than adding a restriction, so an unset or unreadable property must still enforce —
   * the inverse of {@code security.internalPages.group} (issue #1688), which ships blank because it
   * adds a NEW restriction and opting in is the caller's decision there.
   */
  public static final String PROPERTY_ENFORCE = "security.contentApi.enforcePageAccess";

  private static Log LOG = LogFactory.getLog(ValidateApiAccessToContentCommand.class);

  private ValidateApiAccessToContentCommand() {
    // Static command
  }

  /**
   * Returns true when this caller may read this content record.
   *
   * @param contentUniqueId the content being requested
   * @param user the REST caller; null or a guest is treated as anonymous by the page gate
   * @return true when at least one page rendering this content is one the caller may open
   */
  public static boolean hasAccess(String contentUniqueId, User user) {
    if (StringUtils.isBlank(contentUniqueId)) {
      return false;
    }

    if (!isEnforced()) {
      return true;
    }

    List<WebPage> renderingPages = findPagesRendering(contentUniqueId);

    if (renderingPages.isEmpty()) {
      // Orphan content: no page renders it, so there is no page gate to inherit and no way to
      // reason about who should see it. Allowed deliberately -- content is also reached outside
      // the page system, and denying here would break those callers with a 404 that looks like a
      // missing record rather than a policy decision. Logged so the case is visible rather than
      // silent, since it is the one path this command cannot actually reason about.
      LOG.debug("No web page renders content '" + contentUniqueId + "' -- allowing, nothing to inherit");
      return true;
    }

    for (WebPage webPage : renderingPages) {
      if (ValidateApiAccessToWebPageCommand.hasAccess(webPage, user)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Enforcing unless a site has explicitly opted out. Read via the string form rather than
   * {@code loadByNameAsBoolean}, which returns false for a missing row -- and a missing row is
   * exactly what an installation has between upgrading the code and running the migration. Failing
   * open in that window would reopen the hole for every existing deployment.
   */
  private static boolean isEnforced() {
    String value = StringUtils.trimToNull(LoadSitePropertyCommand.loadByName(PROPERTY_ENFORCE));
    return !"false".equalsIgnoreCase(value);
  }

  /**
   * The pages whose XML references this content, by either form page layouts use. Mirrors
   * {@code WebPageSearchResultsWidget}: {@code <uniqueId>x</uniqueId>} is the widget-preference
   * form and {@code ${uniqueId:x}} the inline-embed form.
   */
  private static List<WebPage> findPagesRendering(String contentUniqueId) {
    String elementForm = "<uniqueId>" + contentUniqueId + "</uniqueId>";
    String inlineForm = "${uniqueId:" + contentUniqueId + "}";
    List<WebPage> matches = new java.util.ArrayList<>();
    List<WebPage> webPageList = WebPageRepository.findAll();
    if (webPageList == null) {
      return matches;
    }
    for (WebPage webPage : webPageList) {
      String pageXml = webPage.getPageXml();
      if (StringUtils.isBlank(pageXml)) {
        continue;
      }
      if (pageXml.contains(elementForm) || pageXml.contains(inlineForm)) {
        matches.add(webPage);
      }
    }
    return matches;
  }
}
