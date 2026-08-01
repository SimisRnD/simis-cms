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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * deletePageViaPostCallsRepositoryAndAudits guards a real regression: the page editor's delete button submits
 * via a real HTTP POST (issue #358 moved state-changing admin actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() below -- action()'s "deletePage" dispatch
 * (and its admin-role check) was correct but unreachable, and post() never checked the action parameter, so
 * it fell through to the page-save logic instead. That logic reads an "id" parameter a deletePage request
 * never sends (it sends "webPageId"), so it built a blank WebPage, failed link validation, and replaced the
 * click with a "Please check the form and try again" error plus a bogus content.unpublish failure audit
 * record -- the page was never deleted. This test calls post() directly, the same method a real request now
 * reaches, so it fails if that dispatch gap reopens.
 */
class WebPageFormWidgetTest extends WidgetBase {

  @Test
  void deletePageViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);

    WebPage webPage = new WebPage();
    webPage.setId(7L);
    webPage.setLink("/about");
    webPage.setTitle("About Us");

    addQueryParameter(widgetContext, "webPageId", "7");
    addQueryParameter(widgetContext, "action", "deletePage");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(anyLong())).thenReturn(webPage);

      WidgetContext result = new WebPageFormWidget().post(widgetContext);

      webPageRepository.verify(() -> WebPageRepository.remove(webPage), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("web_page"), eq("7"), eq("About Us"), any()), times(1));
      Assertions.assertEquals("Page was deleted", result.getSuccessMessage());
      // #420: a deleted page must also drop out of the AFD edge cache, not just the DB
      purge.verify(() -> PublishEventCachePurgeHandler.onPageDeleted("/about"), times(1));
    }
  }
}
