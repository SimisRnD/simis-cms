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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPagePreviewToken;
import com.simisinc.platform.infrastructure.persistence.cms.WebPagePreviewTokenRepository;

/**
 * Verifies {@link GeneratePreviewLinkCommand} (#419): the TTL resolver's defaulting behavior, and
 * that {@code generateFor} issues a plaintext token expiring per the configured
 * {@code security.previewLinkTtlHours} setting.
 *
 * @author elizabeth houser
 */
class GeneratePreviewLinkCommandTest {

  @Test
  void resolvePreviewLinkTtlHoursDefaultsForABlankValue() {
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours(null));
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours(""));
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours("   "));
  }

  @Test
  void resolvePreviewLinkTtlHoursDefaultsForANonNumericValue() {
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours("not-a-number"));
  }

  @Test
  void resolvePreviewLinkTtlHoursDefaultsForAZeroOrNegativeValue() {
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours("0"));
    assertEquals(24, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours("-5"));
  }

  @Test
  void resolvePreviewLinkTtlHoursHonorsAValidValue() {
    assertEquals(72, GeneratePreviewLinkCommand.resolvePreviewLinkTtlHours("72"));
  }

  @Test
  void generateForRejectsANullWebPage() {
    assertThrows(DataException.class, () -> GeneratePreviewLinkCommand.generateFor(null, "/page", 1L));
  }

  @Test
  void generateForRejectsAnUnsavedWebPage() {
    WebPage webPage = new WebPage();
    assertThrows(DataException.class, () -> GeneratePreviewLinkCommand.generateFor(webPage, "/page", 1L));
  }

  @Test
  void generateForRejectsABlankPagePath() {
    WebPage webPage = new WebPage();
    webPage.setId(42L);
    assertThrows(DataException.class, () -> GeneratePreviewLinkCommand.generateFor(webPage, "", 1L));
    assertThrows(DataException.class, () -> GeneratePreviewLinkCommand.generateFor(webPage, null, 1L));
  }

  @Test
  void generateForIssuesATokenExpiringPerTheConfiguredTtl() throws DataException {
    WebPage webPage = new WebPage();
    webPage.setId(42L);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> repository = mockStatic(WebPagePreviewTokenRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.previewLinkTtlHours")).thenReturn("2");
      repository.when(() -> WebPagePreviewTokenRepository.add(any(WebPagePreviewToken.class)))
          .thenAnswer(invocation -> {
            WebPagePreviewToken saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
          });

      Timestamp beforeCall = Timestamp.from(Instant.now().plusSeconds(2 * 3600 - 5));
      WebPagePreviewToken result = GeneratePreviewLinkCommand.generateFor(webPage, "/news/my-post", 7L);
      Timestamp afterCall = Timestamp.from(Instant.now().plusSeconds(2 * 3600 + 5));

      assertNotNull(result.getToken());
      assertEquals(42L, result.getWebPageId());
      assertEquals("/news/my-post", result.getPagePath());
      assertEquals(7L, result.getCreatedBy());
      assertTrue(result.getExpiresAt().after(beforeCall) && result.getExpiresAt().before(afterCall),
          "expiry should be roughly 2 hours (the configured TTL) from now");
    }
  }

  @Test
  void generateForThrowsWhenTheRepositoryFailsToSave() {
    WebPage webPage = new WebPage();
    webPage.setId(42L);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPagePreviewTokenRepository> repository = mockStatic(WebPagePreviewTokenRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.previewLinkTtlHours")).thenReturn("24");
      repository.when(() -> WebPagePreviewTokenRepository.add(any(WebPagePreviewToken.class))).thenReturn(null);

      assertThrows(DataException.class, () -> GeneratePreviewLinkCommand.generateFor(webPage, "/page", 7L));
    }
  }
}
