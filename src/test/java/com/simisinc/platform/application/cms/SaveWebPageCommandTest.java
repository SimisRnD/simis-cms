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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * Issue #570 -- solution-page tagging's validation and persistence through SaveWebPageCommand.
 */
class SaveWebPageCommandTest {

  private static WebPage webPageBean(String link, String solutionType) {
    WebPage bean = new WebPage();
    bean.setLink(link);
    bean.setCreatedBy(1L);
    bean.setSolutionType(solutionType);
    return bean;
  }

  @Test
  void savesANewPageWithARecognizedSolutionType() throws DataException {
    WebPage bean = webPageBean("/solutions/cmmc", "government-solution");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPage saved = SaveWebPageCommand.saveWebPage(bean);

      assertEquals("government-solution", saved.getSolutionType());
    }
  }

  @Test
  void savesANewPageWithNoSolutionType() throws DataException {
    WebPage bean = webPageBean("/about", null);

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      repository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPage saved = SaveWebPageCommand.saveWebPage(bean);

      assertNull(saved.getSolutionType());
    }
  }

  @Test
  void rejectsAnUnrecognizedSolutionType() {
    WebPage bean = webPageBean("/solutions/cmmc", "not-a-real-option");

    try (MockedStatic<WebPageRepository> repository = mockStatic(WebPageRepository.class)) {
      DataException exception = assertThrows(DataException.class, () -> SaveWebPageCommand.saveWebPage(bean));

      assertEquals(true, exception.getMessage().contains("Solution type choice is unavailable"));
      repository.verify(() -> WebPageRepository.save(any()), org.mockito.Mockito.never());
    }
  }
}
