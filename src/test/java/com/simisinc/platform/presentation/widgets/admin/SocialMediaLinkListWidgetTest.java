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

package com.simisinc.platform.presentation.widgets.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;

/**
 * @author SimIS Inc.
 */
class SocialMediaLinkListWidgetTest extends WidgetBase {

  @Test
  void executeListsAllLinks() {
    SocialMediaLink link = new SocialMediaLink();
    link.setId(1L);
    link.setPlatformName("Instagram");
    List<SocialMediaLink> links = List.of(link);

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(SocialMediaLinkRepository::findAll).thenReturn(links);

      setRoles(widgetContext, ADMIN);
      new SocialMediaLinkListWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SocialMediaLinkListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals(links, request.getAttribute("socialMediaLinkList"));
  }

  @Test
  void deleteRemovesTheRecordAndRecordsAnAuditEvent() {
    SocialMediaLink link = new SocialMediaLink();
    link.setId(5L);
    link.setPlatformName("Facebook");

    addQueryParameter(widgetContext, "socialMediaLinkId", "5");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(() -> SocialMediaLinkRepository.findById(5L)).thenReturn(link);
      repository.when(() -> SocialMediaLinkRepository.remove(link)).thenReturn(true);

      new SocialMediaLinkListWidget().delete(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.remove(link));
    }

    Assertions.assertNotNull(widgetContext.getSuccessMessage());
  }

  @Test
  void deleteWithoutAValidIdDoesNothing() {
    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      new SocialMediaLinkListWidget().delete(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.remove(any()), never());
    }

    Assertions.assertNull(widgetContext.getSuccessMessage());
  }
}
