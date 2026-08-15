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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;

/**
 * Validates and saves a {@code web_redirects} row (issue #408). Mirrors {@code
 * SaveWebhookSubscriptionCommand}'s shape: the incoming form bean is validated, then copied field
 * by field onto either a freshly loaded copy of the existing persisted record (update) or a new
 * one (create) -- never persisted directly.
 *
 * <p>
 * This is the duplicate-{@code from_path} pre-check {@link WebRedirectRepository#add(WebRedirect)}
 * deliberately leaves to a later stage (see {@code WebRedirectRepositoryTest
 * .fromPathMustBeUniqueAtTheDatabaseLevel()}): {@link WebRedirectRepository#findByFromPath(String)}
 * is used here -- rather than the enabled-only {@link WebRedirectRepository#findEnabledByFromPath}
 * -- so a disabled row still counts as a conflict, matching the unique index it backstops.
 * </p>
 *
 * <p>
 * {@code toUrl} is passed through {@link UrlCommand#sanitizeUrl(String)}, the same defense-at-the
 * -source helper content-authored href/src values go through. That guards against the value
 * breaking out of the {@code Location} header context or using an active scheme ({@code
 * javascript:}, {@code data:}, ...) -- it is <strong>not</strong> open-redirect protection: it
 * happily accepts any {@code http://}/{@code https://} URL regardless of host. The from_path
 * denylist below and the admin-only restriction on an external {@code toUrl} (see {@link
 * #save(WebRedirect, boolean)}) are what actually bound who can point the trusted site's own URLs
 * at an arbitrary external destination.
 * </p>
 *
 * @author SimIS Inc.
 */
public class SaveWebRedirectCommand {

  private static final Log LOG = LogFactory.getLog(SaveWebRedirectCommand.class);

  // Prefixes WebRequestFilter and its downstream pipeline treat specially (health checks, ACME
  // challenges, static resources, the REST API, /logout) plus /admin and /login -- the two most
  // security-sensitive routes, gated by role/authentication machinery that only ever runs AFTER
  // WebRequestFilter's redirect check (issue #408 review). Since /admin/web-redirects is itself
  // reachable by "admin" or "content-manager" (see WebRedirectListWidget.hasAccess()), without this
  // denylist either role could claim one of these paths as a from_path and, for every visitor,
  // either take down the entire admin console (including this page) or turn /login into a
  // credential-phishing redirector to an external site.
  private static final String[] RESERVED_FROM_PATH_PREFIXES = {
      "/admin", "/login", "/logout", "/api", "/healthz", "/.well-known",
      "/favicon", "/css", "/fonts", "/html", "/images", "/javascript",
      "/combined.css", "/combined.js", "/sitemap.xml", "/feed.xml", "/feed"
  };

  private SaveWebRedirectCommand() {
    // Static utility, not instantiated
  }

  /**
   * Equivalent to {@code save(bean, true)} -- i.e. permits an external {@code toUrl}. Existing
   * callers that have no per-user role to offer (e.g. the CSV import path, tests) get the
   * unrestricted behavior; {@link com.simisinc.platform.presentation.widgets.admin.cms
   * .WebRedirectFormWidget}, which does know the acting user's role, calls {@link
   * #save(WebRedirect, boolean)} directly.
   *
   * @param bean form-submitted values: id (-1 for a new record), fromPath, toUrl, statusCode,
   *        enabled, createdBy/modifiedBy
   * @return the saved record
   * @throws DataException when required fields are missing/invalid, the from path collides with
   *         an existing redirect, or the referenced existing record cannot be found
   */
  public static WebRedirect save(WebRedirect bean) throws DataException {
    return save(bean, true);
  }

  /**
   * @param bean form-submitted values: id (-1 for a new record), fromPath, toUrl, statusCode,
   *        enabled, createdBy/modifiedBy
   * @param actingUserIsAdmin whether the user submitting this form has the "admin" role (as opposed
   *        to only "content-manager", the other role /admin/web-redirects accepts -- see
   *        WebRedirectListWidget.hasAccess()). A non-admin may only redirect to a site-relative
   *        path: an external destination is a much stronger, silent, no-click capability (any
   *        visitor hitting a trusted URL on this site is bounced, not just someone who clicks a
   *        link a content manager authored on a page) and is restricted to the more trusted role.
   * @return the saved record
   * @throws DataException when required fields are missing/invalid, the from path is reserved, is
   *         a duplicate of an existing redirect, would create a redirect loop, or a non-admin
   *         submitted an external toUrl -- or the referenced existing record cannot be found
   */
  public static WebRedirect save(WebRedirect bean, boolean actingUserIsAdmin) throws DataException {
    StringBuilder errorMessages = new StringBuilder();

    if (bean.getCreatedBy() == -1 || bean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this redirect was not set. ");
    }

    String fromPath = StringUtils.trimToNull(bean.getFromPath());
    if (fromPath == null) {
      errorMessages.append("A from path is required. ");
    } else if (fromPath.startsWith("http:") || fromPath.startsWith("https:")) {
      errorMessages.append("The from path must be site-relative, not an external URL. ");
    } else if (!fromPath.startsWith("/")) {
      errorMessages.append("The from path must start with a /. ");
    } else if (isReservedFromPath(fromPath)) {
      errorMessages.append("The from path conflicts with a reserved system path and cannot be used. ");
    }

    String toUrl = StringUtils.trimToNull(bean.getToUrl());
    String sanitizedToUrl = toUrl == null ? null : UrlCommand.sanitizeUrl(toUrl);
    if (toUrl == null) {
      errorMessages.append("A to URL is required. ");
    } else if (sanitizedToUrl == null) {
      errorMessages.append("The to URL is not valid. ");
    } else if (!actingUserIsAdmin && isExternalUrl(sanitizedToUrl)) {
      errorMessages.append("Only administrators may redirect to an external URL; content managers can redirect "
          + "to a path on this site only. ");
    }

    if (fromPath != null && sanitizedToUrl != null && fromPath.equals(sanitizedToUrl)) {
      errorMessages.append("A redirect cannot point to itself. ");
    }

    if (bean.getStatusCode() != WebRedirect.PERMANENT && bean.getStatusCode() != WebRedirect.TEMPORARY) {
      errorMessages.append("The status code must be 301 or 302. ");
    }

    boolean isNew = bean.getId() == null || bean.getId() <= -1;

    if (fromPath != null) {
      WebRedirect existingByPath = WebRedirectRepository.findByFromPath(fromPath);
      if (existingByPath != null && (isNew || !existingByPath.getId().equals(bean.getId()))) {
        errorMessages.append("A redirect for that from path already exists. ");
      }
    }

    // A two-record cycle (A -> B, B -> A, or any longer chain that eventually loops back on
    // itself) would send a browser into an infinite series of redirects. Only worth walking once
    // the from/to path values are themselves individually valid -- an invalid fromPath or toUrl is
    // already being rejected above.
    if (fromPath != null && sanitizedToUrl != null && !fromPath.equals(sanitizedToUrl)
        && createsRedirectLoop(fromPath, sanitizedToUrl)) {
      errorMessages.append("This redirect would create a redirect loop with an existing redirect. ");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages);
    }

    WebRedirect record;
    if (isNew) {
      LOG.debug("Saving a new web redirect...");
      record = new WebRedirect();
      record.setCreatedBy(bean.getCreatedBy());
    } else {
      LOG.debug("Saving an existing web redirect...");
      record = WebRedirectRepository.findById(bean.getId());
      if (record == null) {
        throw new DataException("The existing redirect could not be found");
      }
    }

    record.setFromPath(fromPath);
    record.setToUrl(sanitizedToUrl);
    record.setStatusCode(bean.getStatusCode());
    record.setEnabled(bean.getEnabled());
    record.setModifiedBy(bean.getModifiedBy());

    WebRedirect saved = WebRedirectRepository.save(record);
    if (saved == null) {
      throw new DataException("Your information could not be saved due to a system error. Please try again.");
    }
    return saved;
  }

  private static boolean isReservedFromPath(String fromPath) {
    // Case-insensitive: RESERVED_FROM_PATH_PREFIXES are all lowercase literals, but a from_path of
    // "/ADMIN" or "/Login" would otherwise sail past this check while still reading as a plausible
    // shadow of the real (case-sensitive, but visually identical) route -- a phishing/confusion
    // vector even though it can't literally intercept the real route's requests (issue #992).
    String normalized = fromPath.toLowerCase();
    for (String reserved : RESERVED_FROM_PATH_PREFIXES) {
      if (normalized.equals(reserved) || normalized.startsWith(reserved + "/")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isExternalUrl(String url) {
    String lower = url.toLowerCase();
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  /**
   * Walks the chain of existing {@code from_path -> to_url} redirects starting at {@code toUrl} (as
   * if {@code fromPath -> toUrl} were already saved), following each hop's own {@code to_url} as
   * the next {@code from_path} to look up. Returns {@code true} the moment the walk revisits any
   * path it has already seen -- whether that is {@code fromPath} itself (the classic two-record
   * cycle the review flagged) or some other already-visited hop (a longer cycle the new edge
   * completes or feeds into). Terminates normally (no loop) as soon as the chain reaches a path
   * with no redirect of its own. Bounded by the total number of existing redirects so a
   * (structurally impossible, but defensive) unbounded walk can't occur.
   */
  private static boolean createsRedirectLoop(String fromPath, String toUrl) {
    List<WebRedirect> all = WebRedirectRepository.findAll();
    Set<String> visited = new HashSet<>();
    visited.add(fromPath);
    String current = toUrl;
    int maxHops = all.size() + 1;
    for (int hop = 0; hop < maxHops; hop++) {
      if (!visited.add(current)) {
        return true;
      }
      WebRedirect next = findByFromPath(all, current);
      if (next == null) {
        return false;
      }
      current = next.getToUrl();
    }
    return true;
  }

  private static WebRedirect findByFromPath(List<WebRedirect> redirects, String fromPath) {
    for (WebRedirect redirect : redirects) {
      if (fromPath.equals(redirect.getFromPath())) {
        return redirect;
      }
    }
    return null;
  }
}
