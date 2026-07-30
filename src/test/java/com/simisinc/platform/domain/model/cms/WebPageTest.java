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

package com.simisinc.platform.domain.model.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

class WebPageTest {

  // enabled, searchable, and showInSitemap each match their column's own DEFAULT true
  // (NEW_10010__new_cms.sql). WebPageFormWidget.post() writes each of these values explicitly on
  // every save -- including a brand-new page's first save, before BeanUtils.populate ever runs --
  // so these Java defaults are what a freshly created page actually gets whenever the field
  // disagreed with its column. Verified live: a page created through the running app had
  // enabled=false (never exposed as a form field at all -- no code path ever set it true) and
  // showInSitemap=false (the create form's toggle renders unchecked for a brand-new page), and
  // was excluded from both sitemap.xml and WebPageRepository.search() as a result.

  @Test
  void aNewPageDefaultsToEnabled() {
    assertTrue(new WebPage().isEnabled());
  }

  @Test
  void aNewPageDefaultsToSearchable() {
    assertTrue(new WebPage().isSearchable());
  }

  @Test
  void aNewPageDefaultsToShownInTheSitemap() {
    assertTrue(new WebPage().isShowInSitemap());
  }

  @Test
  void aPageWithAFuturePublishAtIsScheduled() {
    WebPage webPage = new WebPage();
    webPage.setPublishAt(new Timestamp(System.currentTimeMillis() + 60_000));
    assertTrue(webPage.isScheduled());
  }

  @Test
  void aPageWithAPastPublishAtIsNotScheduled() {
    WebPage webPage = new WebPage();
    webPage.setPublishAt(new Timestamp(System.currentTimeMillis() - 60_000));
    assertFalse(webPage.isScheduled());
  }

  @Test
  void aPageWithNoPublishAtIsNotScheduled() {
    assertFalse(new WebPage().isScheduled());
  }

  @Test
  void aPageWithAFutureExpiresAtIsExpiringSoon() {
    WebPage webPage = new WebPage();
    webPage.setExpiresAt(new Timestamp(System.currentTimeMillis() + 60_000));
    assertTrue(webPage.isExpiringSoon());
  }

  @Test
  void aPageWithAPastExpiresAtIsNotExpiringSoon() {
    WebPage webPage = new WebPage();
    webPage.setExpiresAt(new Timestamp(System.currentTimeMillis() - 60_000));
    assertFalse(webPage.isExpiringSoon());
  }

  @Test
  void aPageWithNoExpiresAtIsNotExpiringSoon() {
    assertFalse(new WebPage().isExpiringSoon());
  }
}
