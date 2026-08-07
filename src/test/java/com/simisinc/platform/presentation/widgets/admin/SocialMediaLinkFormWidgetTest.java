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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;

/**
 * @author SimIS Inc.
 */
class SocialMediaLinkFormWidgetTest extends WidgetBase {

  @Test
  void executeShowsANewBlankRecordByDefault() {
    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      new SocialMediaLinkFormWidget().execute(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.findById(any(Long.class)), never());
    }

    SocialMediaLink socialMediaLink = (SocialMediaLink) request.getAttribute("socialMediaLink");
    Assertions.assertNotNull(socialMediaLink);
    Assertions.assertEquals(-1L, (long) socialMediaLink.getId());
  }

  @Test
  void executePopulatesTheFormWhenEditingAnExistingRecord() {
    SocialMediaLink link = new SocialMediaLink();
    link.setId(7L);
    link.setPlatformName("Mastodon");
    link.setUrl("https://mastodon.social/@simis");

    addQueryParameter(widgetContext, "socialMediaLinkId", "7");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(() -> SocialMediaLinkRepository.findById(7L)).thenReturn(link);

      new SocialMediaLinkFormWidget().execute(widgetContext);
    }

    Assertions.assertEquals(link, request.getAttribute("socialMediaLink"));
    Assertions.assertEquals("Edit Platform", request.getAttribute("title"));
  }

  @Test
  void postSavesAValidLink() throws Exception {
    addQueryParameter(widgetContext, "platformName", "Discord");
    addQueryParameter(widgetContext, "url", "https://discord.gg/simis");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(() -> SocialMediaLinkRepository.save(any())).thenAnswer(invocation -> {
        SocialMediaLink saved = invocation.getArgument(0);
        saved.setId(1L);
        return saved;
      });

      new SocialMediaLinkFormWidget().post(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.save(any()));
    }

    Assertions.assertNotNull(widgetContext.getSuccessMessage());
    Assertions.assertNull(widgetContext.getErrorMessage());
  }

  @Test
  void postRejectsABlankPlatformNameWithoutSaving() throws Exception {
    addQueryParameter(widgetContext, "platformName", "");
    addQueryParameter(widgetContext, "url", "https://example.com");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      new SocialMediaLinkFormWidget().post(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void postRejectsAUrlWithoutAValidScheme() throws Exception {
    addQueryParameter(widgetContext, "platformName", "Instagram");
    addQueryParameter(widgetContext, "url", "javascript:alert(1)");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      new SocialMediaLinkFormWidget().post(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void postRejectsAPlatformThatAlreadyHasALinkWithoutSaving() throws Exception {
    SocialMediaLink existing = new SocialMediaLink();
    existing.setId(3L);
    existing.setPlatformName("Instagram");

    addQueryParameter(widgetContext, "platformName", "instagram");
    addQueryParameter(widgetContext, "url", "https://instagram.com/anotherAccount");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(() -> SocialMediaLinkRepository.findByPlatformName("instagram")).thenReturn(existing);

      new SocialMediaLinkFormWidget().post(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void postAllowsEditingARecordWithoutTriggeringItsOwnDuplicateCheck() throws Exception {
    SocialMediaLink existing = new SocialMediaLink();
    existing.setId(3L);
    existing.setPlatformName("Instagram");

    addQueryParameter(widgetContext, "id", "3");
    addQueryParameter(widgetContext, "platformName", "Instagram");
    addQueryParameter(widgetContext, "url", "https://instagram.com/SimISInc");

    try (MockedStatic<SocialMediaLinkRepository> repository = mockStatic(SocialMediaLinkRepository.class)) {
      repository.when(() -> SocialMediaLinkRepository.findByPlatformName("Instagram")).thenReturn(existing);
      repository.when(() -> SocialMediaLinkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new SocialMediaLinkFormWidget().post(widgetContext);

      repository.verify(() -> SocialMediaLinkRepository.save(any()));
    }

    Assertions.assertNotNull(widgetContext.getSuccessMessage());
    Assertions.assertNull(widgetContext.getErrorMessage());
  }
}
