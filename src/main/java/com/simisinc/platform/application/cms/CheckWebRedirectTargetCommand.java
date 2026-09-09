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

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.Page;

/**
 * Reports whether an admin-managed redirect's {@code toUrl} still resolves to something this site
 * serves.
 *
 * <p>Why this exists: {@code SaveWebRedirectCommand} sanitizes a {@code toUrl} but never checks
 * that it resolves, and nothing re-checks afterwards. A redirect created against a live page
 * becomes a permanent dead end the moment that page is deleted, unpublished, or emptied -- and
 * nothing surfaces it. The redirect keeps answering 301, so the visitor, and every search engine,
 * follows it into a 404.
 *
 * <p>That is not hypothetical. {@code /employee-benefits} pointed at
 * {@code /employee-benefits-summary}, which served 200 until 2026-08-31 and then stopped. Over the
 * following fortnight eleven distinct clients followed that redirect into a 404, among them a
 * visitor arriving from Google and both Googlebot and Bingbot. A 301 is cached hard by browsers
 * and treated as permanent by crawlers, so a redirect into a 404 is worse than no redirect at all:
 * the dead destination is what gets remembered.
 *
 * <p><b>Why a report and not a save-time rejection.</b> Validating on save cannot catch this. The
 * redirect above was correct when it was written; the target died two weeks later. A save-time
 * check would have passed it and never looked again. Blocking a save would also be wrong on its
 * own terms -- a redirect is legitimately created before its destination exists, when staging a
 * move. So this classifies, and the admin list shows the answer; nothing here refuses a save or
 * changes what {@code WebRequestFilter} serves.
 *
 * <p><b>Why so much is deliberately NOT judged.</b> The costly failure for a checker like this is
 * the false positive: flag enough working redirects and the real one stops being read. An external
 * URL cannot be judged without a network call this must not make. A static asset (a PDF under
 * {@code /assets/}, anything under the prefixes {@code WebRequestFilter} exempts) is served by the
 * container with no {@code web_pages} row, so its absence from the database proves nothing. Both
 * come back {@link TargetStatus#NOT_CHECKED}, which the list renders as silence rather than
 * reassurance -- saying "not checked" is honest where "OK" would be a guess.
 *
 * <p>What remains -- an internal path that should resolve to a page -- is judged with the same
 * derivation {@code WebPageListWidget} already uses for its live/broken buckets: a standard page
 * from {@code XMLPageLoader}, or a {@code /directory/} path, is always live; otherwise a
 * {@code web_pages} row must exist, not be a draft, and either carry {@code page_xml} or redirect
 * onward. Reusing that rule rather than inventing a second one is the point -- two resolvers that
 * disagree would make this list contradict the page list it sits next to.
 *
 * @author SimIS Inc.
 */
public class CheckWebRedirectTargetCommand {

  /** Prefixes served as static resources -- mirrors {@code WebRequestFilter}'s exempt list. */
  private static final String[] STATIC_PREFIXES = { "/assets", "/combined", "/css", "/fonts", "/html",
      "/images", "/javascript", "/web-content" };

  public enum TargetStatus {
    /** Resolves to something this site serves. */
    OK(false, "Resolves"),
    /** External, or a static asset -- deliberately not judged here. See the class doc. */
    NOT_CHECKED(false, "Not checked"),
    /** No page record and not a built-in page: the redirect lands on a 404. */
    MISSING_PAGE(true, "Target page does not exist"),
    /** The page exists but is a draft, which is a 404 for everyone not signed in. */
    DRAFT_PAGE(true, "Target page is a draft"),
    /** Published, but with no layout to render. */
    EMPTY_PAGE(true, "Target page has no content");

    private final boolean broken;
    private final String label;

    TargetStatus(boolean broken, String label) {
      this.broken = broken;
      this.label = label;
    }

    public boolean isBroken() {
      return broken;
    }

    public String getLabel() {
      return label;
    }
  }

  private CheckWebRedirectTargetCommand() {
    // Static utility, not instantiated
  }

  /**
   * Classifies a redirect's destination.
   *
   * @param toUrl the redirect's stored destination
   * @param standardPages built-in pages from {@code XMLPageLoader}; may be null
   * @param webPageMap {@code web_pages} rows keyed by lower-cased link; may be null
   * @return never null -- an unjudgeable destination is {@link TargetStatus#NOT_CHECKED}
   */
  public static TargetStatus check(String toUrl, Map<String, Page> standardPages,
      Map<String, WebPage> webPageMap) {

    String path = normalize(toUrl);
    if (path == null) {
      return TargetStatus.NOT_CHECKED;
    }

    // The homepage is always served
    if ("/".equals(path)) {
      return TargetStatus.OK;
    }

    // Dynamic, and a standard page needs no database row
    if (path.startsWith("/directory/")) {
      return TargetStatus.OK;
    }
    if (standardPages != null && standardPages.containsKey(path)) {
      return TargetStatus.OK;
    }

    WebPage webPage = webPageMap == null ? null : webPageMap.get(path);
    if (webPage == null) {
      return TargetStatus.MISSING_PAGE;
    }
    if (webPage.getDraft()) {
      return TargetStatus.DRAFT_PAGE;
    }
    // A page that redirects onward still resolves; following that hop is not this check's job
    if (StringUtils.isNotBlank(webPage.getRedirectUrl())) {
      return TargetStatus.OK;
    }
    if (StringUtils.isBlank(webPage.getPageXml())) {
      return TargetStatus.EMPTY_PAGE;
    }
    return TargetStatus.OK;
  }

  /**
   * The site-relative, comparable form of a destination, or null when it is not ours to judge.
   *
   * <p>Query strings and fragments are dropped because a page is identified by its path alone, and
   * a trailing slash is dropped because {@code WebRequestFilter} canonicalizes one away before any
   * lookup -- comparing {@code /careers/} against a stored {@code /careers} would otherwise report
   * a working redirect as broken. Lower-cased to match {@code WebPageRepository.findByLink}, which
   * compares on {@code LOWER(link)}.
   */
  static String normalize(String toUrl) {
    String trimmed = StringUtils.trimToNull(toUrl);
    if (trimmed == null) {
      return null;
    }
    // External destinations, including protocol-relative ones, need a network call to judge
    String lower = trimmed.toLowerCase();
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")) {
      return null;
    }
    if (!lower.startsWith("/")) {
      return null;
    }
    int cut = StringUtils.indexOfAny(lower, "?#");
    if (cut >= 0) {
      lower = lower.substring(0, cut);
    }
    if (lower.isEmpty()) {
      return null;
    }
    // Static resources have no page record, so their absence from the database means nothing
    for (String prefix : STATIC_PREFIXES) {
      if (lower.equals(prefix) || lower.startsWith(prefix + "/")) {
        return null;
      }
    }
    if (lower.length() > 1 && lower.endsWith("/")) {
      lower = StringUtils.stripEnd(lower, "/");
    }
    if (lower.isEmpty()) {
      return "/";
    }
    // A path with a file extension is a static file, not a page slug
    int lastSlash = lower.lastIndexOf('/');
    if (lower.indexOf('.', lastSlash) > lastSlash) {
      return null;
    }
    return lower;
  }
}
