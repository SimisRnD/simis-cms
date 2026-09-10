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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;

/**
 * The page metadata an admin types on /admin/web-page (issue #1740).
 *
 * <p>These three columns are the ones that feed a page's tsvector, so they are also the fields
 * someone is most likely to be filling in when they hit the limit -- a description written for SEO
 * runs long easily, and 255 characters is roughly two sentences.
 *
 * @author SimIS Inc.
 */
class SaveWebPageCommandLengthTest {

  private static WebPage webPage(String title, String keywords, String description) {
    WebPage bean = new WebPage();
    bean.setLink("/a-page");
    bean.setTitle(title);
    bean.setKeywords(keywords);
    bean.setDescription(description);
    return bean;
  }

  @Test
  void anOverLongPageDescriptionIsRefusedWithTheLimitInTheMessage() {
    WebPage bean = webPage("A page", null, "x".repeat(256));

    DataException exception = assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

    assertTrue(exception.getMessage().contains("A page description can be up to 255 characters"),
        exception.getMessage());
  }

  @Test
  void anOverLongPageTitleIsRefused() {
    WebPage bean = webPage("x".repeat(256), null, null);

    DataException exception = assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

    assertTrue(exception.getMessage().contains("A page title can be up to 255 characters"),
        exception.getMessage());
  }

  @Test
  void anOverLongKeywordsListIsRefused() {
    WebPage bean = webPage("A page", "x".repeat(256), null);

    DataException exception = assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

    assertTrue(exception.getMessage().contains("Page keywords can be up to 255 characters"),
        exception.getMessage());
  }

  @Test
  void allThreeTooLongAreReportedInOneMessage() {
    // an admin who pasted into every field should be told once, not made to discover them one save
    // at a time
    WebPage bean = webPage("x".repeat(256), "x".repeat(256), "x".repeat(256));

    DataException exception = assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

    assertTrue(exception.getMessage().contains("A page title"), exception.getMessage());
    assertTrue(exception.getMessage().contains("Page keywords"), exception.getMessage());
    assertTrue(exception.getMessage().contains("A page description"), exception.getMessage());
  }

  // These two assert only that nothing is refused *for length*. The save then reaches the database,
  // which a unit test has no connection for, so the catch is deliberately wide -- the point is the
  // message, not that the save completes.
  @Test
  void metadataExactlyAtTheLimitIsNotRefusedForLength() {
    // 255 is what the column holds, so 255 must not be rejected on length grounds
    WebPage bean = webPage("x".repeat(255), "x".repeat(255), "x".repeat(255));

    try {
      SaveWebPageCommand.saveWebPage(bean);
    } catch (Exception e) {
      assertTrue(e.getMessage() == null || !e.getMessage().contains("can be up to"),
          "metadata at exactly the limit must not be refused for length: " + e.getMessage());
    }
  }

  @Test
  void absentMetadataIsNotReportedAsTooLong() {
    // every one of these fields is optional; a page saved without them must not trip a length check
    WebPage bean = webPage(null, null, null);

    try {
      SaveWebPageCommand.saveWebPage(bean);
    } catch (Exception e) {
      assertTrue(e.getMessage() == null || !e.getMessage().contains("can be up to"), e.getMessage());
    }
  }
}
