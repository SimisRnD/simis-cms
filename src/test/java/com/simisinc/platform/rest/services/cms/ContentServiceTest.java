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

package com.simisinc.platform.rest.services.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.DeltaContentCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link ContentService#post}: the guest-rejection and permission checks a REST write
 * needs that a read never did (issue #412 PR2), the format-mismatch guard that protects a Delta
 * (visual-editor) content block from being silently clobbered by an HTML write and vice versa, and
 * that governed publishing's real outcome (published vs. gated to draft) is both audited and
 * reported back to the caller truthfully.
 *
 * @author SimIS Inc.
 */
class ContentServiceTest {

  private static final String CONTENT_UNIQUE_ID = "homepage-intro";

  private User userWithRole(long id, String... roleCodes) {
    User user = new User();
    user.setId(id);
    user.setEmail("editor@example.com");
    List<Role> roles = new ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    user.setRoleList(roles);
    return user;
  }

  private ServiceContext contextFor(User user, String jsonBody) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(jsonBody)));
    when(request.getRemoteAddr()).thenReturn("203.0.113.5");
    ServiceContext context = new ServiceContext(request, response);
    context.setPathParam(CONTENT_UNIQUE_ID);
    context.setUser(user);
    return context;
  }

  @Test
  void postRejectsAGuestDemotedCallerWithNoBearerToken() throws Exception {
    // Matches RestRequestFilter's actual shape for a no-token request: a bare User at GUEST_ID,
    // not a null user -- reads are fine as a guest, writes never are.
    User guest = new User();
    guest.setId(UserSession.GUEST_ID);
    ServiceContext context = contextFor(guest, "{\"content\":\"<p>hi</p>\"}");

    ServiceResponse response = new ContentService().post(context);

    assertEquals(401, response.getStatus());
  }

  @Test
  void postRejectsAnAuthenticatedUserWithoutTheContentEditorRole() throws Exception {
    User user = userWithRole(42L, "data-manager");
    ServiceContext context = contextFor(user, "{\"content\":\"<p>hi</p>\"}");

    ServiceResponse response = new ContentService().post(context);

    assertEquals(403, response.getStatus());
  }

  @Test
  void postRejectsMalformedJson() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "not json");

    ServiceResponse response = new ContentService().post(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void postRejectsAMissingContentField() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"format\":\"html\"}");

    ServiceResponse response = new ContentService().post(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void postRejectsAnUnsupportedFormat() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"format\":\"markdown\"}");

    ServiceResponse response = new ContentService().post(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void postSavesAsDraftWhenPublishIsNotRequestedAndAuditsSaveDraft() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"publish\":false}");
    Content saved = new Content();
    saved.setId(7L);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(null);
      saveContent.when(() -> SaveContentCommand.saveSafeContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(false)))
          .thenReturn(saved);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(200, response.getStatus());
      ContentUpdateResponse data = (ContentUpdateResponse) response.getData();
      assertFalse(data.isPublished());
      assertFalse(data.isGated());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.saveDraft"), eq("success"),
          eq(42L), anyString(), anyString(), any(), eq("content"), eq("7"), eq(CONTENT_UNIQUE_ID), any()), times(1));
    }
  }

  @Test
  void postPublishesDirectlyWhenReviewIsNotRequired() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"publish\":true}");
    Content saved = new Content();
    saved.setId(7L);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required")).thenReturn(false);
      saveContent.when(() -> SaveContentCommand.saveSafeContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(true)))
          .thenReturn(saved);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(200, response.getStatus());
      ContentUpdateResponse data = (ContentUpdateResponse) response.getData();
      assertTrue(data.isPublished());
      assertFalse(data.isGated());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"), eq("success"),
          eq(42L), anyString(), anyString(), any(), eq("content"), eq("7"), eq(CONTENT_UNIQUE_ID), any()), times(1));
    }
  }

  @Test
  void postGatesAPublishRequestWhenReviewIsRequiredAndReportsAndAuditsTheGating() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"publish\":true}");
    Content saved = new Content();
    saved.setId(7L);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required")).thenReturn(true);
      // ContentService resolves governed publishing itself and passes the ALREADY-gated value
      // (false) to the save call -- it must never rely on SaveContentCommand to self-degrade a
      // raw publish=true, since not every save method does (see SaveContentCommand's own fix
      // alongside this PR for saveSafeDeltaContent, which was missing that internal check).
      saveContent.when(() -> SaveContentCommand.saveSafeContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(false)))
          .thenReturn(saved);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(200, response.getStatus());
      ContentUpdateResponse data = (ContentUpdateResponse) response.getData();
      assertFalse(data.isPublished());
      assertTrue(data.isGated());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"), eq("failure"),
          eq(42L), anyString(), anyString(), any(), eq("content"), eq("7"), eq(CONTENT_UNIQUE_ID),
          eq("gated: saved as a draft for review")), times(1));
    }
  }

  @Test
  void postSavesDeltaContentThroughTheDeltaSpecificSaveMethod() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"{\\\"ops\\\":[]}\",\"format\":\"delta\",\"publish\":false}");
    Content saved = new Content();
    saved.setId(7L);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(null);
      saveContent.when(() -> SaveContentCommand.saveSafeDeltaContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(false)))
          .thenReturn(saved);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(200, response.getStatus());
      saveContent.verify(() -> SaveContentCommand.saveSafeDeltaContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(false)), times(1));
      // Confirms dispatch actually went to the Delta-specific method, not the HTML one.
      saveContent.verify(() -> SaveContentCommand.saveSafeContent(anyString(), anyString(), anyLong(), anyBoolean()),
          org.mockito.Mockito.never());
    }
  }

  @Test
  void postRejectsAnHtmlWriteToAnExistingDeltaFormattedDraft() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"format\":\"html\",\"publish\":false}");
    Content existing = new Content();
    existing.setId(7L);
    existing.setDraftContent("{\"ops\":[]}");
    existing.setDraftContentFormat(DeltaContentCommand.DELTA_FORMAT_VERSION);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(existing);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(409, response.getStatus());
      saveContent.verifyNoInteractions();
    }
  }

  @Test
  void postAllowsAnHtmlWriteWhenTheExistingDraftSlotIsEmptyEvenIfThePublishedVersionIsDelta() throws Exception {
    // The published version and the draft carry independent format stamps -- an empty draft slot
    // must not be blocked just because the *published* content happens to be Delta.
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"<p>hi</p>\",\"format\":\"html\",\"publish\":false}");
    Content existing = new Content();
    existing.setId(7L);
    existing.setContent("{\"ops\":[]}");
    existing.setContentFormat(DeltaContentCommand.DELTA_FORMAT_VERSION);
    existing.setDraftContent(null);
    Content saved = new Content();
    saved.setId(7L);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(existing);
      saveContent.when(() -> SaveContentCommand.saveSafeContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), eq(false)))
          .thenReturn(saved);

      ServiceResponse response = new ContentService().post(context);

      assertEquals(200, response.getStatus());
    }
  }

  @Test
  void postReturns400WhenSaveContentCommandRejectsTheContent() throws Exception {
    User editor = userWithRole(42L, "content-editor");
    ServiceContext context = contextFor(editor, "{\"content\":\"{\\\"ops\\\":\\\"not-an-array\\\"}\",\"format\":\"delta\"}");

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class)) {

      repo.when(() -> ContentRepository.findByUniqueId(CONTENT_UNIQUE_ID)).thenReturn(null);
      saveContent.when(() -> SaveContentCommand.saveSafeDeltaContent(eq(CONTENT_UNIQUE_ID), anyString(), eq(42L), anyBoolean()))
          .thenThrow(new DataException("Content is not a valid editor document"));

      ServiceResponse response = new ContentService().post(context);

      assertEquals(400, response.getStatus());
      assertEquals("Content is not a valid editor document", response.getError().get("title"));
    }
  }
}
