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

package com.simisinc.platform.presentation.widgets.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.application.mailinglists.ProcessEmailCSVFileCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberDeletedEvent;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberSpecification;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * The "Block IP" row action (#646) feeds a mailing-list member's captured IP into the database-backed
 * blocked-IP list. Blocks the IP only - the member row is untouched. These tests call post() directly,
 * the method a real confirmPostAction()-submitted request actually reaches (see #658/PR #659, found
 * while building this feature: the sibling delete links on the blocked/allowed IP list pages had used
 * the same confirmPostAction() convention but their post() never checked for the matching command, so
 * every click silently no-opped).
 *
 * @author elizabeth houser
 */
class MailingListMembersWidgetTest extends WidgetBase {

  private static MailingList mailingList() {
    MailingList list = new MailingList();
    list.setId(1L);
    list.setName("Newsletter");
    return list;
  }

  private static Email emailWithIp(String ip) {
    Email email = new Email();
    email.setId(2L);
    email.setEmail("spammer@example.com");
    email.setIpAddress(ip);
    return email;
  }

  private static User adminUser() {
    User user = new User();
    user.setId(1L);
    user.setUsername("admin");
    return user;
  }

  @Test
  void blockIPSuccessfullyBlocksTheMembersIp() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    Email target = emailWithIp("203.0.113.20");
    BlockedIP saved = new BlockedIP();
    saved.setId(42L);
    saved.setIpAddress("203.0.113.20");
    saved.setReason("Blocked from mailing list member: spammer@example.com");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<BlockedIPRepository> blockedIpRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(target);
      blockedIpRepo.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.20")).thenReturn(null);
      saveCommand.when(() -> SaveBlockedIPCommand.save(any(BlockedIP.class))).thenReturn(saved);

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      saveCommand.verify(() -> SaveBlockedIPCommand.save(any(BlockedIP.class)));
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("blocked_ip.add"),
          eq(AuditEventCommand.SUCCESS), eq("blocked_ip"), eq("42"), eq("203.0.113.20"), any()));
      assertEquals("IP 203.0.113.20 has been blocked", result.getSuccessMessage());
    }
  }

  @Test
  void blockIPRefusesToBlockTheAdminsOwnCurrentIp() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");
    when(request.getRemoteAddr()).thenReturn("203.0.113.20");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp("203.0.113.20"));

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      saveCommand.verify(() -> SaveBlockedIPCommand.save(any()), never());
      assertEquals("Cannot block your own IP", result.getErrorMessage());
    }
  }

  @Test
  void blockIPWarnsInsteadOfDuplicatingAnAlreadyBlockedIp() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    BlockedIP existing = new BlockedIP();
    existing.setId(7L);
    existing.setIpAddress("203.0.113.20");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<BlockedIPRepository> blockedIpRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp("203.0.113.20"));
      blockedIpRepo.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.20")).thenReturn(existing);

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      saveCommand.verify(() -> SaveBlockedIPCommand.save(any()), never());
      assertEquals("That IP is already blocked", result.getWarningMessage());
    }
  }

  @Test
  void blockIPFailsClearlyWhenNoIpIsOnFile() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp(null));

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      saveCommand.verify(() -> SaveBlockedIPCommand.save(any()), never());
      assertEquals("No IP address is on file for this member", result.getErrorMessage());
    }
  }

  @Test
  void blockIPDoesNotRemoveTheMailingListMember() throws Exception {
    // The whole point of this action's scope decision: block the IP, leave the subscriber record alone.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    BlockedIP saved = new BlockedIP();
    saved.setId(42L);
    saved.setIpAddress("203.0.113.20");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<BlockedIPRepository> blockedIpRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp("203.0.113.20"));
      blockedIpRepo.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.20")).thenReturn(null);
      saveCommand.when(() -> SaveBlockedIPCommand.save(any(BlockedIP.class))).thenReturn(saved);

      new MailingListMembersWidget().post(widgetContext);

      memberRepo.verify(() -> MailingListMemberRepository.remove(any(), any()), never());
    }
  }

  @Test
  void uploadCSVFileRedirectsBackToTheMailingListMembersPage() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "uploadCSVFile");
    addQueryParameter(widgetContext, "mailingListId", "1");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<ProcessEmailCSVFileCommand> processCsv = mockStatic(ProcessEmailCSVFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      processCsv.when(() -> ProcessEmailCSVFileCommand.processCSV(any(), any())).thenReturn(3);

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      assertEquals("/admin/mailing-list-members?mailingListId=1", result.getRedirect());
      assertEquals("3 emails added", result.getSuccessMessage());
      // #763: data.import didn't exist anywhere in the codebase before this -- CSV imports of
      // member PII must be as traceable as the existing data.export download already is.
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("data.import"), eq(AuditEventCommand.SUCCESS),
          eq("mailing_list_members"), eq("1"), eq("Newsletter"), eq("memberCount=3")));
    }
  }

  @Test
  void uploadCSVFileRedirectsBackToTheMailingListMembersPageEvenWhenProcessingFails() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "uploadCSVFile");
    addQueryParameter(widgetContext, "mailingListId", "1");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<ProcessEmailCSVFileCommand> processCsv = mockStatic(ProcessEmailCSVFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      processCsv.when(() -> ProcessEmailCSVFileCommand.processCSV(any(), any()))
          .thenThrow(new DataException("Valid file not found"));

      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      assertEquals("/admin/mailing-list-members?mailingListId=1", result.getRedirect());
      assertEquals("Valid file not found", result.getErrorMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("data.import"), eq(AuditEventCommand.FAILURE),
          eq("mailing_list_members"), eq("1"), eq("Newsletter"), eq("Valid file not found")));
    }
  }

  // #763: search/filter on the per-list member table, reusing the same matchesEmail/matchesName
  // shape EmailSpecification already offered on the cross-list search flow (MailingListsWidget),
  // plus a status filter over this table's own quarantined/unsubscribed columns.

  @Test
  void executePassesSearchAndStatusParamsToTheSpecification() {
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "searchName", "Jane Doe");
    addQueryParameter(widgetContext, "searchEmail", "jane@example.com");
    addQueryParameter(widgetContext, "status", "quarantined");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      memberRepo.when(() -> MailingListMemberRepository.findAll(any(), any())).thenReturn(new java.util.ArrayList<>());

      new MailingListMembersWidget().execute(widgetContext);

      ArgumentCaptor<MailingListMemberSpecification> captor = ArgumentCaptor.forClass(MailingListMemberSpecification.class);
      memberRepo.verify(() -> MailingListMemberRepository.findAll(captor.capture(), any()));
      MailingListMemberSpecification specification = captor.getValue();
      assertEquals(1L, specification.getMailingListId());
      assertEquals("Jane Doe", specification.getMatchesName());
      assertEquals("jane@example.com", specification.getMatchesEmail());
      assertEquals("quarantined", specification.getStatus());
    }
  }

  @Test
  void executeLeavesFiltersUnsetWhenNoSearchParamsAreGiven() {
    addQueryParameter(widgetContext, "mailingListId", "1");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      memberRepo.when(() -> MailingListMemberRepository.findAll(any(), any())).thenReturn(new java.util.ArrayList<>());

      new MailingListMembersWidget().execute(widgetContext);

      ArgumentCaptor<MailingListMemberSpecification> captor = ArgumentCaptor.forClass(MailingListMemberSpecification.class);
      memberRepo.verify(() -> MailingListMemberRepository.findAll(captor.capture(), any()));
      MailingListMemberSpecification specification = captor.getValue();
      assertNull(specification.getMatchesName());
      assertNull(specification.getMatchesEmail());
      assertNull(specification.getStatus());
    }
  }

  @Test
  void deleteFiresAMailingListMemberDeletedEventWithASnapshotTakenBeforeRemoval() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    MailingListMember member = new MailingListMember();
    member.setId(9L);
    member.setEmailAddress("spammer@example.com");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp("203.0.113.20"));
      memberRepo.when(() -> MailingListMemberRepository.findByListAndEmail(1L, 2L)).thenReturn(member);
      loadUser.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());

      new MailingListMembersWidget().delete(widgetContext);

      memberRepo.verify(() -> MailingListMemberRepository.findByListAndEmail(1L, 2L));
      memberRepo.verify(() -> MailingListMemberRepository.remove(any(), any()));
      ArgumentCaptor<MailingListMemberDeletedEvent> eventCaptor = ArgumentCaptor.forClass(
          MailingListMemberDeletedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals(member, eventCaptor.getValue().getMember());
    }
  }

  @Test
  void deleteDoesNotFireAnEventWhenTheMemberWasAlreadyGone() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    try (MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      mailingListRepo.when(() -> MailingListRepository.findById(1L)).thenReturn(mailingList());
      emailRepo.when(() -> EmailRepository.findById(2L)).thenReturn(emailWithIp("203.0.113.20"));
      // findByListAndEmail left unstubbed -- default null, as if the row was already gone

      new MailingListMembersWidget().delete(widgetContext);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void blockIPIsRefusedWithoutAdminOrCommunityManagerRole() throws Exception {
    setRoles(widgetContext); // logged in, no relevant role
    addQueryParameter(widgetContext, "command", "blockIP");
    addQueryParameter(widgetContext, "mailingListId", "1");
    addQueryParameter(widgetContext, "emailId", "2");

    try (MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class)) {
      WidgetContext result = new MailingListMembersWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
      assertNull(result.getSuccessMessage());
    }
  }
}
