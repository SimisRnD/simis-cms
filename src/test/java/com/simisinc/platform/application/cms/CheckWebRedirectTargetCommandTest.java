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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.cms.CheckWebRedirectTargetCommand.TargetStatus;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.Page;

/**
 * Verifies {@link CheckWebRedirectTargetCommand}. Two things are being protected here: that a
 * redirect which has quietly become a dead end is reported, and -- at least as important -- that
 * the many legitimate destinations it cannot judge are never reported. A checker that cries wolf
 * on working redirects stops being read, and then the real one goes unnoticed too.
 */
class CheckWebRedirectTargetCommandTest {

  private static Map<String, Page> standardPages(String... links) {
    Map<String, Page> pages = new HashMap<>();
    for (String link : links) {
      pages.put(link, new Page());
    }
    return pages;
  }

  private static WebPage page(String link, boolean draft, String pageXml, String redirectUrl) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setDraft(draft);
    webPage.setPageXml(pageXml);
    webPage.setRedirectUrl(redirectUrl);
    return webPage;
  }

  private static Map<String, WebPage> pageMap(WebPage... webPages) {
    Map<String, WebPage> map = new HashMap<>();
    for (WebPage webPage : webPages) {
      map.put(webPage.getLink().toLowerCase(), webPage);
    }
    return map;
  }

  private static TargetStatus check(String toUrl, Map<String, Page> standard, Map<String, WebPage> pages) {
    return CheckWebRedirectTargetCommand.check(toUrl, standard, pages);
  }

  // --- the defect this exists for ---

  @Test
  void a_redirect_to_a_page_that_no_longer_exists_is_reported() {
    /* The observed case: /employee-benefits pointed at /employee-benefits-summary, which served
       200 until the page went away. The redirect kept answering 301 into a 404 for a fortnight. */
    TargetStatus status = check("/employee-benefits-summary", standardPages(), pageMap());
    assertEquals(TargetStatus.MISSING_PAGE, status);
    assertTrue(status.isBroken());
  }

  @Test
  void a_target_left_as_a_draft_is_reported_because_the_public_gets_a_404() {
    TargetStatus status = check("/benefits", standardPages(),
        pageMap(page("/benefits", true, "<page/>", null)));
    assertEquals(TargetStatus.DRAFT_PAGE, status);
    assertTrue(status.isBroken());
  }

  @Test
  void a_published_target_with_no_layout_is_reported() {
    TargetStatus status = check("/benefits", standardPages(),
        pageMap(page("/benefits", false, "  ", null)));
    assertEquals(TargetStatus.EMPTY_PAGE, status);
    assertTrue(status.isBroken());
  }

  // --- destinations that resolve ---

  @Test
  void a_live_page_resolves() {
    assertEquals(TargetStatus.OK, check("/careers", standardPages(),
        pageMap(page("/careers", false, "<page/>", null))));
  }

  @Test
  void a_built_in_page_resolves_without_any_page_record() {
    /* Standard pages come from XMLPageLoader and have no web_pages row. Judging them by the
       database alone would report every one of them as broken. */
    assertEquals(TargetStatus.OK, check("/login", standardPages("/login"), pageMap()));
  }

  @Test
  void a_page_that_redirects_onward_still_resolves() {
    assertEquals(TargetStatus.OK, check("/old-page", standardPages(),
        pageMap(page("/old-page", false, null, "/new-page"))));
  }

  @Test
  void a_directory_path_resolves_because_it_is_generated() {
    assertEquals(TargetStatus.OK, check("/directory/anything", standardPages(), pageMap()));
  }

  @Test
  void the_homepage_resolves() {
    assertEquals(TargetStatus.OK, check("/", standardPages(), pageMap()));
  }

  // --- what must never be judged, so the report stays trustworthy ---

  @Test
  void an_external_destination_is_not_judged() {
    for (String url : new String[] { "https://example.com/x", "http://example.com/x", "//example.com/x" }) {
      TargetStatus status = check(url, standardPages(), pageMap());
      assertEquals(TargetStatus.NOT_CHECKED, status, url);
      assertFalse(status.isBroken(), url);
    }
  }

  @Test
  void a_static_asset_is_not_judged_because_it_has_no_page_record() {
    for (String url : new String[] { "/assets/file/20260820005714-4/eeo-program.pdf", "/images/logo.png",
        "/css/custom/stylesheet.css", "/javascript/app.js", "/web-content/images/x.png" }) {
      assertEquals(TargetStatus.NOT_CHECKED, check(url, standardPages(), pageMap()), url);
    }
  }

  @Test
  void a_path_with_a_file_extension_is_not_judged() {
    assertEquals(TargetStatus.NOT_CHECKED, check("/sitemap.xml", standardPages(), pageMap()));
  }

  @Test
  void a_blank_or_relative_destination_is_not_judged() {
    for (String url : new String[] { null, "", "   ", "careers" }) {
      assertEquals(TargetStatus.NOT_CHECKED, check(url, standardPages(), pageMap()), String.valueOf(url));
    }
  }

  @Test
  void null_maps_do_not_throw() {
    assertEquals(TargetStatus.MISSING_PAGE, CheckWebRedirectTargetCommand.check("/x", null, null));
    assertEquals(TargetStatus.OK, CheckWebRedirectTargetCommand.check("/", null, null));
  }

  // --- normalization, each of which would otherwise be a false positive ---

  @Test
  void a_trailing_slash_does_not_make_a_working_redirect_look_broken() {
    /* WebRequestFilter canonicalizes /careers/ to /careers before any lookup, so comparing the
       slashed form against the stored link would report a working destination as missing. */
    assertEquals(TargetStatus.OK, check("/careers/", standardPages(),
        pageMap(page("/careers", false, "<page/>", null))));
  }

  @Test
  void a_query_string_or_fragment_is_ignored() {
    Map<String, WebPage> pages = pageMap(page("/careers", false, "<page/>", null));
    assertEquals(TargetStatus.OK, check("/careers?utm_source=x", standardPages(), pages));
    assertEquals(TargetStatus.OK, check("/careers#benefits", standardPages(), pages));
  }

  @Test
  void matching_is_case_insensitive_like_the_repository_lookup() {
    /* WebPageRepository.findByLink compares on LOWER(link). */
    assertEquals(TargetStatus.OK, check("/Careers", standardPages(),
        pageMap(page("/careers", false, "<page/>", null))));
  }

  @Test
  void normalize_keeps_a_plain_path_intact() {
    assertEquals("/careers", CheckWebRedirectTargetCommand.normalize("/Careers/"));
  }
}
