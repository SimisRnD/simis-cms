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

package com.simisinc.platform.rest.services.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link MailingListMemberService#post} and {@link MailingListMemberService#put}: the
 * guest-rejection and role checks a REST write needs, that a create/update reuses the real save
 * logic ({@link SaveEmailCommand}, {@link MailingListMemberRepository}) rather than reimplementing
 * it, and that a PUT cannot flip {@code unsubscribed} back to {@code false} (issue #412 PR3).
 *
 * @author SimIS Inc.
 */
class MailingListMemberServiceTest {

  private User userWithRole(long id, String... roleCodes) {
    User user = new User();
    user.setId(id);
    user.setEmail("integration@example.com");
    List<Role> roles = new ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    user.setRoleList(roles);
    return user;
  }

  private ServiceContext contextFor(User user, String jsonBody, String pathParam) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(jsonBody)));
    when(request.getRemoteAddr()).thenReturn("203.0.113.5");
    ServiceContext context = new ServiceContext(request, response);
    context.setPathParam(pathParam);
    context.setUser(user);
    return context;
  }

  // --- POST ---

  @Test
  void postRejectsAGuestDemotedCallerWithNoBearerToken() throws Exception {
    User guest = new User();
    guest.setId(UserSession.GUEST_ID);
    ServiceContext context = contextFor(guest, "{\"email\":\"a@example.com\"}", null);

    ServiceResponse response = new MailingListMemberService().post(context);

    assertEquals(401, response.getStatus());
  }

  @Test
  void postRejectsAnAuthenticatedUserWithoutTheRequiredRole() throws Exception {
    User user = userWithRole(42L, "data-manager");
    ServiceContext context = contextFor(user, "{\"email\":\"a@example.com\"}", null);

    ServiceResponse response = new MailingListMemberService().post(context);

    assertEquals(403, response.getStatus());
  }

  @Test
  void postRejectsMalformedJson() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "not json", null);

    ServiceResponse response = new MailingListMemberService().post(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void postRejectsAMissingEmailField() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"firstName\":\"Jo\"}", null);

    ServiceResponse response = new MailingListMemberService().post(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void postRejectsAnUnresolvableMailingList() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"email\":\"a@example.com\",\"mailingListName\":\"Nonexistent\"}", null);

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName("Nonexistent")).thenReturn(null);

      ServiceResponse response = new MailingListMemberService().post(context);

      assertEquals(400, response.getStatus());
    }
  }

  @Test
  void postCreatesAMemberAgainstTheDefaultNewsletterListAndAuditsTheCreate() throws Exception {
    User communityManager = userWithRole(42L, "community-manager");
    ServiceContext context = contextFor(communityManager, "{\"email\":\"a@example.com\",\"firstName\":\"Jo\"}", null);

    MailingList newsletter = new MailingList();
    newsletter.setId(9L);
    newsletter.setName("Newsletter");

    Email savedEmail = new Email();
    savedEmail.setId(100L);
    savedEmail.setEmail("a@example.com");
    savedEmail.setFirstName("Jo");

    MailingListMember member = new MailingListMember();
    member.setId(500L);
    member.setListId(9L);
    member.setEmailId(100L);
    member.setIsValid(true);

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(newsletter);
      saveEmail.when(() -> SaveEmailCommand.saveEmail(any(Email.class), eq(newsletter))).thenReturn(savedEmail);
      memberRepo.when(() -> MailingListMemberRepository.findByListAndEmail(9L, 100L)).thenReturn(member);

      ServiceResponse response = new MailingListMemberService().post(context);

      assertEquals(200, response.getStatus());
      MailingListMemberResponse data = (MailingListMemberResponse) response.getData();
      assertEquals(500L, data.getMemberId());
      assertEquals("a@example.com", data.getEmail());
      assertTrue(data.isValid());
      assertFalse(data.isUnsubscribed());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("configuration"), eq("mailing_list_member.create"),
          eq("success"), eq(42L), anyString(), anyString(), any(), eq("mailing_list_members"), eq("500"),
          eq("a@example.com"), any()), times(1));
    }
  }

  @Test
  void postReturns400WhenSaveEmailCommandRejectsTheAddress() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"email\":\"not-an-email\"}", null);

    MailingList newsletter = new MailingList();
    newsletter.setId(9L);
    newsletter.setName("Newsletter");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveEmailCommand> saveEmail = mockStatic(SaveEmailCommand.class)) {

      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(newsletter);
      saveEmail.when(() -> SaveEmailCommand.saveEmail(any(Email.class), eq(newsletter)))
          .thenThrow(new DataException("Please check the email address and try again"));

      ServiceResponse response = new MailingListMemberService().post(context);

      assertEquals(400, response.getStatus());
      assertEquals("Please check the email address and try again", response.getError().get("title"));
    }
  }

  // --- PUT ---

  @Test
  void putRejectsAGuestDemotedCallerWithNoBearerToken() throws Exception {
    User guest = new User();
    guest.setId(UserSession.GUEST_ID);
    ServiceContext context = contextFor(guest, "{\"firstName\":\"Jo\"}", "500");

    ServiceResponse response = new MailingListMemberService().put(context);

    assertEquals(401, response.getStatus());
  }

  @Test
  void putRejectsAnAuthenticatedUserWithoutTheRequiredRole() throws Exception {
    User user = userWithRole(42L, "data-manager");
    ServiceContext context = contextFor(user, "{\"firstName\":\"Jo\"}", "500");

    ServiceResponse response = new MailingListMemberService().put(context);

    assertEquals(403, response.getStatus());
  }

  @Test
  void putRejectsANonNumericMemberId() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"firstName\":\"Jo\"}", "not-a-number");

    ServiceResponse response = new MailingListMemberService().put(context);

    assertEquals(400, response.getStatus());
  }

  @Test
  void putReturns404ForAnUnknownMember() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"firstName\":\"Jo\"}", "999");

    try (MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      memberRepo.when(() -> MailingListMemberRepository.findById(999L)).thenReturn(null);

      ServiceResponse response = new MailingListMemberService().put(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void putRejectsSettingUnsubscribedToFalse() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"unsubscribed\":false}", "500");

    MailingListMember member = new MailingListMember();
    member.setId(500L);
    member.setListId(9L);
    member.setEmailId(100L);

    try (MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      memberRepo.when(() -> MailingListMemberRepository.findById(500L)).thenReturn(member);

      ServiceResponse response = new MailingListMemberService().put(context);

      assertEquals(400, response.getStatus());
    }
  }

  @Test
  void putUpdatesNameFieldsOnTheSharedEmailRecordAndAuditsTheUpdate() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"firstName\":\"Jo\",\"organization\":\"Acme\"}", "500");

    MailingListMember member = new MailingListMember();
    member.setId(500L);
    member.setListId(9L);
    member.setEmailId(100L);
    member.setIsValid(true);

    Email existingEmail = new Email();
    existingEmail.setId(100L);
    existingEmail.setEmail("a@example.com");
    existingEmail.setFirstName("Old");

    try (MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      memberRepo.when(() -> MailingListMemberRepository.findById(500L)).thenReturn(member);
      emailRepo.when(() -> EmailRepository.findById(100L)).thenReturn(existingEmail);

      ServiceResponse response = new MailingListMemberService().put(context);

      assertEquals(200, response.getStatus());
      MailingListMemberResponse data = (MailingListMemberResponse) response.getData();
      assertEquals("Jo", data.getFirstName());
      assertEquals("Acme", data.getOrganization());
      emailRepo.verify(() -> EmailRepository.update(existingEmail), times(1));
      // No unsubscribe was requested -- must not touch membership status.
      memberRepo.verify(() -> MailingListMemberRepository.unsubscribe(any(), any(), any()), org.mockito.Mockito.never());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("configuration"), eq("mailing_list_member.update"),
          eq("success"), eq(42L), anyString(), anyString(), any(), eq("mailing_list_members"), eq("500"),
          eq("a@example.com"), any()), times(1));
    }
  }

  @Test
  void putUnsubscribesThroughTheExistingRepositoryTransitionAndAuditsTheUpdate() throws Exception {
    User admin = userWithRole(42L, "admin");
    ServiceContext context = contextFor(admin, "{\"unsubscribed\":true}", "500");

    MailingListMember member = new MailingListMember();
    member.setId(500L);
    member.setListId(9L);
    member.setEmailId(100L);
    member.setIsValid(true);

    Email existingEmail = new Email();
    existingEmail.setId(100L);
    existingEmail.setEmail("a@example.com");

    MailingList mailingList = new MailingList();
    mailingList.setId(9L);
    mailingList.setName("Newsletter");

    try (MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {

      memberRepo.when(() -> MailingListMemberRepository.findById(500L)).thenReturn(member);
      emailRepo.when(() -> EmailRepository.findById(100L)).thenReturn(existingEmail);
      listRepo.when(() -> MailingListRepository.findById(9L)).thenReturn(mailingList);

      ServiceResponse response = new MailingListMemberService().put(context);

      assertEquals(200, response.getStatus());
      MailingListMemberResponse data = (MailingListMemberResponse) response.getData();
      assertTrue(data.isUnsubscribed());
      assertFalse(data.isValid());
      memberRepo.verify(() -> MailingListMemberRepository.unsubscribe(mailingList, existingEmail, admin), times(1));
      // No name fields were supplied -- must not touch the shared emails row for those.
      emailRepo.verify(() -> EmailRepository.update(any()), org.mockito.Mockito.never());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("configuration"), eq("mailing_list_member.update"),
          eq("success"), eq(42L), anyString(), anyString(), any(), eq("mailing_list_members"), eq("500"),
          eq("a@example.com"), any()), times(1));
    }
  }
}
