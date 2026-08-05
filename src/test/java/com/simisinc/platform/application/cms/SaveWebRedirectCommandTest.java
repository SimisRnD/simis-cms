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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;

/**
 * Verifies {@link SaveWebRedirectCommand}'s validation, including the duplicate-{@code from_path}
 * pre-check {@code WebRedirectRepository} itself deliberately leaves to this command (issue #408).
 */
class SaveWebRedirectCommandTest {

  private static WebRedirect bean(Long id, String fromPath, String toUrl, int statusCode, boolean enabled) {
    WebRedirect bean = new WebRedirect();
    bean.setId(id);
    bean.setFromPath(fromPath);
    bean.setToUrl(toUrl);
    bean.setStatusCode(statusCode);
    bean.setEnabled(enabled);
    bean.setCreatedBy(42L);
    bean.setModifiedBy(42L);
    return bean;
  }

  @Test
  void savingANewRedirectPersistsTheValidatedFields() throws DataException {
    WebRedirect bean = bean(-1L, "/old-page", "/new-page", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean);

      assertEquals("/old-page", saved.getFromPath());
      assertEquals("/new-page", saved.getToUrl());
      assertEquals(301, saved.getStatusCode());
      repository.verify(() -> WebRedirectRepository.save(argThat(r -> r.getCreatedBy() == 42L)));
    }
  }

  @Test
  void updatingAnExistingRedirectLoadsAndOverwritesThePersistedRecord() throws DataException {
    WebRedirect existing = new WebRedirect();
    existing.setId(5L);
    existing.setFromPath("/old-path");
    existing.setToUrl("/old-target");
    existing.setStatusCode(301);
    existing.setEnabled(true);
    existing.setCreatedBy(1L);

    WebRedirect editBean = bean(5L, "/old-path", "/new-target", 302, false);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-path")).thenReturn(existing);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(editBean);

      assertEquals("/new-target", saved.getToUrl());
      assertEquals(302, saved.getStatusCode());
      assertEquals(false, saved.getEnabled());
      assertEquals(1L, saved.getCreatedBy(), "createdBy must never change on update");
    }
  }

  @Test
  void aBlankFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aFromPathNotStartingWithASlashIsRejected() {
    WebRedirect bean = bean(-1L, "old-page", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().contains("start with a /"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void anExternalUrlAsTheFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "https://example.com/old-page", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aBlankToUrlIsRejected() {
    WebRedirect bean = bean(-1L, "/old-page", "", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aToUrlWithAnUnsafeSchemeIsRejected() {
    WebRedirect bean = bean(-1L, "/old-page", "javascript:alert(1)", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void anAbsoluteHttpsToUrlIsAccepted() throws DataException {
    WebRedirect bean = bean(-1L, "/old-page", "https://example.com/new-page", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean);

      assertEquals("https://example.com/new-page", saved.getToUrl());
    }
  }

  @Test
  void aRedirectCannotPointToItself() {
    WebRedirect bean = bean(-1L, "/same-page", "/same-page", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/same-page")).thenReturn(null);
      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().toLowerCase().contains("itself"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aStatusCodeOtherThan301Or302IsRejected() {
    WebRedirect bean = bean(-1L, "/old-page", "/target", 404, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().contains("301 or 302"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aNewRedirectCollidingWithAnExistingFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "/taken-path", "/target", 301, true);

    WebRedirect existing = new WebRedirect();
    existing.setId(9L);
    existing.setFromPath("/taken-path");

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/taken-path")).thenReturn(existing);

      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().contains("already exists"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void editingARedirectAndKeepingItsOwnFromPathIsNotTreatedAsACollision() throws DataException {
    WebRedirect existing = new WebRedirect();
    existing.setId(5L);
    existing.setFromPath("/unchanged-path");
    existing.setToUrl("/old-target");
    existing.setStatusCode(301);
    existing.setEnabled(true);

    WebRedirect editBean = bean(5L, "/unchanged-path", "/new-target", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findById(5L)).thenReturn(existing);
      // findByFromPath finds the record's own row -- this must not be flagged as a collision.
      repository.when(() -> WebRedirectRepository.findByFromPath("/unchanged-path")).thenReturn(existing);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(editBean);

      assertEquals("/new-target", saved.getToUrl());
    }
  }

  @Test
  void renamingAnExistingRedirectsFromPathToOneAnotherRecordAlreadyOwnsIsRejected() {
    WebRedirect existing = new WebRedirect();
    existing.setId(5L);
    existing.setFromPath("/current-path");

    WebRedirect otherRecord = new WebRedirect();
    otherRecord.setId(9L);
    otherRecord.setFromPath("/taken-path");

    WebRedirect editBean = bean(5L, "/taken-path", "/target", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> WebRedirectRepository.findByFromPath("/taken-path")).thenReturn(otherRecord);

      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(editBean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void editingAMissingRedirectThrows() {
    WebRedirect bean = bean(999L, "/old-page", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.findById(999L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void theUserSavingTheRedirectMustBeSet() {
    WebRedirect bean = bean(-1L, "/old-page", "/target", 301, true);
    bean.setCreatedBy(-1);
    bean.setModifiedBy(-1);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  // --- Reserved from_path denylist (issue #408 review: a content-manager could otherwise claim
  // /admin, /login, /logout, /api, etc. as a from_path -- WebRequestFilter's redirect check runs
  // before any role/authentication gate, so this validation is the only thing stopping it) ---

  @Test
  void anAdminConsolePathAsTheFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "/admin/web-redirects", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().contains("reserved system path"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void theLoginPathAsTheFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "/login", "https://evil.example.com/harvest", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().contains("reserved system path"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void theLogoutPathAsTheFromPathIsRejected() {
    WebRedirect bean = bean(-1L, "/logout", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aPathThatMerelyStartsWithAReservedWordIsNotTreatedAsReserved() throws DataException {
    // "/admincustomers" is not "/admin" or "/admin/..." -- a naive startsWith("/admin") check would
    // wrongly block an ordinary content path that happens to share a prefix
    WebRedirect bean = bean(-1L, "/admincustomers", "/target", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/admincustomers")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean);

      assertEquals("/admincustomers", saved.getFromPath());
    }
  }

  // --- Redirect loop detection (issue #408 review: only a direct self-loop was rejected, so a
  // two-record cycle -- A -> B, B -> A -- passed validation and sent browsers into an infinite
  // series of redirects) ---

  @Test
  void aTwoRecordCycleIsRejected() {
    // /a -> /b already exists; saving /b -> /a would complete the cycle
    WebRedirect existingAToB = new WebRedirect();
    existingAToB.setId(1L);
    existingAToB.setFromPath("/a");
    existingAToB.setToUrl("/b");

    WebRedirect bean = bean(-1L, "/b", "/a", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/b")).thenReturn(null);
      repository.when(WebRedirectRepository::findAll).thenReturn(List.of(existingAToB));

      DataException e = assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      assertTrue(e.getMessage().toLowerCase().contains("loop"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aLongerChainThatLoopsBackIsRejected() {
    // /a -> /b -> /c already exist; saving /c -> /a would complete a three-record cycle
    WebRedirect aToB = new WebRedirect();
    aToB.setId(1L);
    aToB.setFromPath("/a");
    aToB.setToUrl("/b");
    WebRedirect bToC = new WebRedirect();
    bToC.setId(2L);
    bToC.setFromPath("/b");
    bToC.setToUrl("/c");

    WebRedirect bean = bean(-1L, "/c", "/a", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/c")).thenReturn(null);
      repository.when(WebRedirectRepository::findAll).thenReturn(List.of(aToB, bToC));

      assertThrows(DataException.class, () -> SaveWebRedirectCommand.save(bean));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aChainThatTerminatesAtAnOrdinaryPageIsNotALoop() throws DataException {
    // /a -> /b already exists (a plain, non-cyclical chain); saving /start -> /a must be accepted
    WebRedirect existingAToB = new WebRedirect();
    existingAToB.setId(1L);
    existingAToB.setFromPath("/a");
    existingAToB.setToUrl("/b");

    WebRedirect bean = bean(-1L, "/start", "/a", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/start")).thenReturn(null);
      repository.when(WebRedirectRepository::findAll).thenReturn(List.of(existingAToB));
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean);

      assertEquals("/a", saved.getToUrl());
    }
  }

  // --- Admin-only external toUrl (issue #408 review: to_url had no destination restriction at all,
  // so a content-manager -- a lower-privileged role than admin -- could redirect any from_path to an
  // arbitrary external, attacker-controlled URL) ---

  @Test
  void aContentManagerCannotSaveAnExternalToUrl() {
    WebRedirect bean = bean(-1L, "/old-page", "https://example.com/new-page", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);

      DataException e = assertThrows(DataException.class,
          () -> SaveWebRedirectCommand.save(bean, false));
      assertTrue(e.getMessage().contains("administrators"));
      repository.verify(() -> WebRedirectRepository.save(any()), never());
    }
  }

  @Test
  void aContentManagerCanSaveASiteRelativeToUrl() throws DataException {
    WebRedirect bean = bean(-1L, "/old-page", "/new-page", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean, false);

      assertEquals("/new-page", saved.getToUrl());
    }
  }

  @Test
  void anAdminCanSaveAnExternalToUrl() throws DataException {
    WebRedirect bean = bean(-1L, "/old-page", "https://example.com/new-page", 301, true);
    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findByFromPath("/old-page")).thenReturn(null);
      repository.when(() -> WebRedirectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebRedirect saved = SaveWebRedirectCommand.save(bean, true);

      assertEquals("https://example.com/new-page", saved.getToUrl());
    }
  }
}
