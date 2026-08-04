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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * #420: publishing or updating a web page never triggered any AFD cache purge -- these tests
 * confirm SaveWebPageCommand, the real command a page save/publish reaches, actually calls the
 * purge hooks (as opposed to unit-testing PublishEventCachePurgeHandler in isolation).
 */
class SaveWebPageCommandTest {

  private static WebPage newPageBean(String link) {
    WebPage bean = new WebPage();
    bean.setLink(link);
    bean.setCreatedBy(1L);
    return bean;
  }

  @Test
  void savingABrandNewPageTriggersOnPagePublished() throws Exception {
    WebPage bean = newPageBean("/about");
    // id == -1 and no modified date -- SaveWebPageCommand's own isNewWebPage check

    WebPage saved = new WebPage();
    saved.setId(5L);
    saved.setLink("/about");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      repository.when(() -> WebPageRepository.save(any())).thenReturn(saved);

      WebPage result = SaveWebPageCommand.saveWebPage(bean);

      org.junit.jupiter.api.Assertions.assertEquals(5L, result.getId());
      purge.verify(() -> PublishEventCachePurgeHandler.onPagePublished(saved));
      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(any()), never());
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
    }
  }

  @Test
  void savingAnExistingPageTriggersOnPageUpdatedEvenWhenRecentlyModified() throws Exception {
    // An existing record, modified moments ago -- SaveWebPageCommand's activity-feed debounce
    // (DateCommand.isHoursOld(modified, 10)) means the WorkflowManager "updated" event does NOT
    // fire for this case, but the AFD purge must fire regardless: the live page changed either
    // way, and a stale edge/browser cache doesn't care how recently the page was last touched.
    WebPage bean = newPageBean("/about");
    bean.setId(5L);
    bean.setModified(new Timestamp(System.currentTimeMillis()));

    WebPage existing = new WebPage();
    existing.setId(5L);
    existing.setLink("/about");
    existing.setModified(new Timestamp(System.currentTimeMillis()));

    WebPage saved = new WebPage();
    saved.setId(5L);
    saved.setLink("/about");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      repository.when(() -> WebPageRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> WebPageRepository.save(any())).thenReturn(saved);

      SaveWebPageCommand.saveWebPage(bean);

      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(saved));
      purge.verify(() -> PublishEventCachePurgeHandler.onPagePublished(any()), never());
      // The debounce that intentionally suppresses the activity-feed event must NOT suppress the purge
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void savePersistsModifiedByFromTheBeanNotCreatedBy() throws Exception {
    // createdBy and modifiedBy are set to different users -- e.g. an admin editing a page someone
    // else originally created -- so a save that conflates the two (persisting createdBy's value
    // into modifiedBy) is caught even though every current caller happens to set both to the same
    // value, which would otherwise mask the bug.
    WebPage bean = newPageBean("/about");
    bean.setCreatedBy(1L);
    bean.setModifiedBy(2L);

    WebPage saved = new WebPage();
    saved.setId(5L);
    saved.setLink("/about");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      repository.when(() -> WebPageRepository.save(any())).thenReturn(saved);

      SaveWebPageCommand.saveWebPage(bean);

      repository.verify(() -> WebPageRepository.save(argThat(page -> {
        assertEquals(1L, page.getCreatedBy());
        assertEquals(2L, page.getModifiedBy());
        return true;
      })));
    }
  }

  @Test
  void failedValidationNeverTriggersAPurge() {
    // No link -- SaveWebPageCommand rejects this before WebPageRepository or the purge handler
    // are ever touched.
    WebPage bean = new WebPage();

    try (MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

      purge.verifyNoInteractions();
    }
  }
}
