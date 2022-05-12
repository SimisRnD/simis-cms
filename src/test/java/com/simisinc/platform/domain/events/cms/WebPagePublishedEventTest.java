/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.model.cms.WebPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class WebPagePublishedEventTest {

  @Test
  void checkEvent() {
    WebPage webPage = new WebPage();
    webPage.setId(1L);
    webPage.setTitle("Products");
    webPage.setLink("/products");

    WebPagePublishedEvent event = new WebPagePublishedEvent(webPage);
    Assertions.assertEquals(webPage.getId(), event.getWebPage().getId());
    Assertions.assertEquals("Products", event.getTitle());
    Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
    Assertions.assertEquals(WebPagePublishedEvent.ID, event.getDomainEventType());
  }

  @Test
  void checkHomePageEvent() {
    WebPage webPage = new WebPage();
    webPage.setId(1L);
    webPage.setLink("/");

    WebPagePublishedEvent event = new WebPagePublishedEvent(webPage);
    Assertions.assertEquals(webPage.getId(), event.getWebPage().getId());
    Assertions.assertEquals("Home page", event.getTitle());
    Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
    Assertions.assertEquals(WebPagePublishedEvent.ID, event.getDomainEventType());
  }

  @Test
  void checkNoTitleEvent() {
    WebPage webPage = new WebPage();
    webPage.setId(1L);
    webPage.setLink("/products");

    WebPagePublishedEvent event = new WebPagePublishedEvent(webPage);
    Assertions.assertEquals(webPage.getId(), event.getWebPage().getId());
    Assertions.assertEquals("/products", event.getTitle());
    Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
    Assertions.assertEquals(WebPagePublishedEvent.ID, event.getDomainEventType());
  }
}