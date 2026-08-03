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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageVersion;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageVersionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the /admin/web-page-versions widget (#405): listing a page's prior published versions
 * and restoring a selected one back into the draft slot.
 *
 * @author elizabeth houser
 */
class WebPageVersionsListWidgetTest extends WidgetBase {

  private static WebPage webPage(long id) {
    WebPage record = new WebPage();
    record.setId(id);
    record.setLink("/example");
    return record;
  }

  private static WebPageVersion version(long id, long webPageId, String pageXml, long publishedBy) {
    WebPageVersion record = new WebPageVersion();
    record.setId(id);
    record.setWebPageId(webPageId);
    record.setPageXml(pageXml);
    record.setPublishedBy(publishedBy);
    return record;
  }

  @Test
  void executeLoadsTheWebPageVersionsAndAuthorMap() {
    addQueryParameter(widgetContext, "webPageId", "3");
    WebPage page = webPage(3L);
    WebPageVersion version = version(10L, 3L, "<xml>v1</xml>", 7L);
    User author = new User();
    author.setId(7L);
    author.setFirstName("Jamie");

    try (MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class);
        MockedStatic<WebPageVersionRepository> versionRepo = mockStatic(WebPageVersionRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      pageRepo.when(() -> WebPageRepository.findById(3L)).thenReturn(page);
      versionRepo.when(() -> WebPageVersionRepository.findByWebPageId(eq(3L), any(DataConstraints.class)))
          .thenReturn(List.of(version));
      userRepo.when(() -> UserRepository.findByUserId(7L)).thenReturn(author);

      WidgetContext result = new WebPageVersionsListWidget().execute(widgetContext);

      assertEquals(page, result.getRequest().getAttribute("webPage"));
      assertEquals(List.of(version), result.getRequest().getAttribute("versionList"));
      Map<Long, User> userMap = (Map<Long, User>) result.getRequest().getAttribute("userMap");
      assertEquals(author, userMap.get(7L));
    }
  }

  @Test
  void executeSetsAnErrorWhenTheWebPageIsNotFound() {
    addQueryParameter(widgetContext, "webPageId", "999");

    try (MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class)) {
      pageRepo.when(() -> WebPageRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new WebPageVersionsListWidget().execute(widgetContext);

      assertEquals("Web page was not found", result.getErrorMessage());
    }
  }

  @Test
  void postRestoresTheDraftFromTheSelectedVersion() throws Exception {
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "webPageId", "3");
    addQueryParameter(widgetContext, "webPageVersionId", "10");
    WebPageVersion version = version(10L, 3L, "<xml>old</xml>", 7L);
    WebPage page = webPage(3L);

    try (MockedStatic<WebPageVersionRepository> versionRepo = mockStatic(WebPageVersionRepository.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class)) {
      versionRepo.when(() -> WebPageVersionRepository.findById(10L)).thenReturn(version);
      pageRepo.when(() -> WebPageRepository.findById(3L)).thenReturn(page);
      pageRepo.when(() -> WebPageRepository.restoreDraftFromVersion(3L, "<xml>old</xml>")).thenReturn(true);
      versionRepo.when(() -> WebPageVersionRepository.findByWebPageId(eq(3L), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      WidgetContext result = new WebPageVersionsListWidget().post(widgetContext);

      pageRepo.verify(() -> WebPageRepository.restoreDraftFromVersion(3L, "<xml>old</xml>"));
      assertEquals("The version was restored to the draft. Publish it to make it live.", result.getSuccessMessage());
    }
  }

  @Test
  void postSetsAnErrorWhenTheVersionIsNotFound() throws Exception {
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "webPageId", "3");
    addQueryParameter(widgetContext, "webPageVersionId", "404");
    WebPage page = webPage(3L);

    try (MockedStatic<WebPageVersionRepository> versionRepo = mockStatic(WebPageVersionRepository.class);
        MockedStatic<WebPageRepository> pageRepo = mockStatic(WebPageRepository.class)) {
      versionRepo.when(() -> WebPageVersionRepository.findById(404L)).thenReturn(null);
      pageRepo.when(() -> WebPageRepository.findById(3L)).thenReturn(page);
      versionRepo.when(() -> WebPageVersionRepository.findByWebPageId(eq(3L), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      WidgetContext result = new WebPageVersionsListWidget().post(widgetContext);

      assertEquals("The selected version was not found", result.getErrorMessage(),
          "the fallthrough execute() reload must not clobber the error set here (needs webPageId to succeed)");
      pageRepo.verify(() -> WebPageRepository.restoreDraftFromVersion(anyLong(), any()), never());
    }
  }

  @Test
  void postIgnoresAnyActionOtherThanRestore() throws Exception {
    addQueryParameter(widgetContext, "action", "somethingElse");

    WidgetContext result = new WebPageVersionsListWidget().post(widgetContext);

    assertNull(result);
  }
}
