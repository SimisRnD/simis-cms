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

package com.simisinc.platform.application.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListHistory;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListHistoryRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListSentRepository;

/**
 * Verifies {@link NewsletterSendCommand}'s dispatch between the two send mechanisms (issue #600
 * rework): a real MailChimp Campaign when {@link MailChimpCommand#isEnabled()}, otherwise the
 * original local SMTP send queue. All collaborators are statically mocked.
 *
 * @author SimIS Inc.
 */
class NewsletterSendCommandTest {

  private static MailingList mailingList(long id, String name) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setName(name);
    return mailingList;
  }

  private static BlogPost blogPost(long id, String title) {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(id);
    blogPost.setTitle(title);
    return blogPost;
  }

  private static MailingListMember member(long emailId) {
    MailingListMember member = new MailingListMember();
    member.setEmailId(emailId);
    return member;
  }

  @Test
  void rejectsANullMailingList() {
    assertThrows(DataException.class,
        () -> NewsletterSendCommand.sendBlogPostNotification(null, blogPost(1L, "Title"), 9L));
  }

  @Test
  void rejectsANullBlogPost() {
    assertThrows(DataException.class,
        () -> NewsletterSendCommand.sendBlogPostNotification(mailingList(1L, "News"), null, 9L));
  }

  // --- MailChimp path ---

  @Test
  void sendsAMailChimpCampaignWhenMailChimpIsEnabled() throws DataException {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");
    Connection connection = mock(Connection.class);

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<NewsletterEmailCommand> emailCommand = mockStatic(NewsletterEmailCommand.class);
        MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<MailingListHistoryRepository> historyRepo = mockStatic(MailingListHistoryRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListSentRepository> sentRepo = mockStatic(MailingListSentRepository.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.getTagMemberCount(list)).thenReturn(42);
      emailCommand.when(() -> NewsletterEmailCommand.renderBlogPostHtml(post, "*|UNSUB|*"))
          .thenReturn("<p>rendered</p>");
      mailChimp.when(() -> MailChimpCommand.createCampaign(list, "Big Announcement")).thenReturn("campaign-1");
      mailChimp.when(() -> MailChimpCommand.setCampaignContent("campaign-1", "<p>rendered</p>")).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.sendCampaign("campaign-1")).thenReturn(true);
      db.when(DB::getConnection).thenReturn(connection);
      historyRepo.when(() -> MailingListHistoryRepository.add(eq(connection), any(MailingListHistory.class)))
          .thenAnswer(invocation -> invocation.getArgument(1));

      int recipientCount = NewsletterSendCommand.sendBlogPostNotification(list, post, 9L);

      assertEquals(42, recipientCount);

      ArgumentCaptor<MailingListHistory> historyCaptor = ArgumentCaptor.forClass(MailingListHistory.class);
      historyRepo.verify(() -> MailingListHistoryRepository.add(eq(connection), historyCaptor.capture()));
      MailingListHistory recorded = historyCaptor.getValue();
      assertEquals("mailchimp", recorded.getService());
      assertEquals(42, recorded.getEmailCount());
      assertEquals("campaign-1", recorded.getMailchimpCampaignId());
      assertEquals(5L, recorded.getBlogPostId());
      assertEquals(1L, recorded.getListId());
      assertEquals(9L, recorded.getCreatedBy());

      // The local send-queue path must never be touched on this branch
      memberRepo.verifyNoInteractions();
      sentRepo.verifyNoInteractions();
    }
  }

  @Test
  void mailChimpPathReturnsZeroAndSkipsEverythingWhenThereAreNoTaggedMembers() throws DataException {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<NewsletterEmailCommand> emailCommand = mockStatic(NewsletterEmailCommand.class);
        MockedStatic<MailingListHistoryRepository> historyRepo = mockStatic(MailingListHistoryRepository.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.getTagMemberCount(list)).thenReturn(-1);

      int recipientCount = NewsletterSendCommand.sendBlogPostNotification(list, post, 9L);

      assertEquals(0, recipientCount);
      emailCommand.verifyNoInteractions();
      mailChimp.verify(() -> MailChimpCommand.createCampaign(any(), anyString()), never());
      historyRepo.verifyNoInteractions();
    }
  }

  @Test
  void mailChimpPathThrowsWhenCampaignCreationFails() {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<NewsletterEmailCommand> emailCommand = mockStatic(NewsletterEmailCommand.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.getTagMemberCount(list)).thenReturn(42);
      emailCommand.when(() -> NewsletterEmailCommand.renderBlogPostHtml(post, "*|UNSUB|*"))
          .thenReturn("<p>rendered</p>");
      mailChimp.when(() -> MailChimpCommand.createCampaign(list, "Big Announcement")).thenReturn(null);

      assertThrows(DataException.class, () -> NewsletterSendCommand.sendBlogPostNotification(list, post, 9L));

      mailChimp.verify(() -> MailChimpCommand.setCampaignContent(anyString(), anyString()), never());
      mailChimp.verify(() -> MailChimpCommand.sendCampaign(anyString()), never());
    }
  }

  @Test
  void mailChimpPathThrowsWhenSettingContentFails() {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<NewsletterEmailCommand> emailCommand = mockStatic(NewsletterEmailCommand.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.getTagMemberCount(list)).thenReturn(42);
      emailCommand.when(() -> NewsletterEmailCommand.renderBlogPostHtml(post, "*|UNSUB|*"))
          .thenReturn("<p>rendered</p>");
      mailChimp.when(() -> MailChimpCommand.createCampaign(list, "Big Announcement")).thenReturn("campaign-1");
      mailChimp.when(() -> MailChimpCommand.setCampaignContent("campaign-1", "<p>rendered</p>")).thenReturn(false);

      assertThrows(DataException.class, () -> NewsletterSendCommand.sendBlogPostNotification(list, post, 9L));

      mailChimp.verify(() -> MailChimpCommand.sendCampaign(anyString()), never());
    }
  }

  @Test
  void mailChimpPathThrowsWhenSendFails() {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<NewsletterEmailCommand> emailCommand = mockStatic(NewsletterEmailCommand.class);
        MockedStatic<MailingListHistoryRepository> historyRepo = mockStatic(MailingListHistoryRepository.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.getTagMemberCount(list)).thenReturn(42);
      emailCommand.when(() -> NewsletterEmailCommand.renderBlogPostHtml(post, "*|UNSUB|*"))
          .thenReturn("<p>rendered</p>");
      mailChimp.when(() -> MailChimpCommand.createCampaign(list, "Big Announcement")).thenReturn("campaign-1");
      mailChimp.when(() -> MailChimpCommand.setCampaignContent("campaign-1", "<p>rendered</p>")).thenReturn(true);
      mailChimp.when(() -> MailChimpCommand.sendCampaign("campaign-1")).thenReturn(false);

      assertThrows(DataException.class, () -> NewsletterSendCommand.sendBlogPostNotification(list, post, 9L));

      historyRepo.verifyNoInteractions();
    }
  }

  // --- SMTP fallback path ---

  @Test
  void enqueuesViaSmtpWhenMailChimpIsNotEnabled() throws DataException {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");
    List<MailingListMember> members = List.of(member(101L), member(102L), member(103L));
    Connection connection = mock(Connection.class);

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<MailingListHistoryRepository> historyRepo = mockStatic(MailingListHistoryRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListSentRepository> sentRepo = mockStatic(MailingListSentRepository.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(false);
      memberRepo.when(() -> MailingListMemberRepository.findActiveMembersForList(1L)).thenReturn(members);
      db.when(DB::getConnection).thenReturn(connection);
      historyRepo.when(() -> MailingListHistoryRepository.add(eq(connection), any(MailingListHistory.class)))
          .thenAnswer(invocation -> {
            MailingListHistory history = invocation.getArgument(1);
            history.setId(77L);
            return history;
          });

      int recipientCount = NewsletterSendCommand.sendBlogPostNotification(list, post, 9L);

      assertEquals(3, recipientCount);

      ArgumentCaptor<MailingListHistory> historyCaptor = ArgumentCaptor.forClass(MailingListHistory.class);
      historyRepo.verify(() -> MailingListHistoryRepository.add(eq(connection), historyCaptor.capture()));
      assertEquals("smtp", historyCaptor.getValue().getService());
      assertEquals(3, historyCaptor.getValue().getEmailCount());

      sentRepo.verify(() -> MailingListSentRepository.enqueue(eq(connection), eq(77L), eq(1L),
          eq(List.of(101L, 102L, 103L))));

      // The MailChimp path must never be touched on this branch
      mailChimp.verify(() -> MailChimpCommand.createCampaign(any(), anyString()), never());
    }
  }

  @Test
  void smtpPathReturnsZeroWithoutCreatingAHistoryRecordWhenThereAreNoActiveMembers() throws DataException {
    MailingList list = mailingList(1L, "Newsletter");
    BlogPost post = blogPost(5L, "Big Announcement");

    try (MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListHistoryRepository> historyRepo = mockStatic(MailingListHistoryRepository.class)) {
      mailChimp.when(MailChimpCommand::isEnabled).thenReturn(false);
      memberRepo.when(() -> MailingListMemberRepository.findActiveMembersForList(1L)).thenReturn(List.of());

      int recipientCount = NewsletterSendCommand.sendBlogPostNotification(list, post, 9L);

      assertEquals(0, recipientCount);
      historyRepo.verifyNoInteractions();
    }
  }
}
