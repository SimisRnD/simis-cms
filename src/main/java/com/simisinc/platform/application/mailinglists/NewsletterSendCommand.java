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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListHistory;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListHistoryRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListSentRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sends (or queues, depending on the configured mailing-list service) a blog-post newsletter
 * notification to a mailing list's current subscribers, and records one mailing_list_history
 * batch header row either way.
 *
 * <p>
 * When MailChimp is the configured service (issue #600 rework), the notification goes out as a
 * real MailChimp Campaign targeting the members tagged with this list -- MailChimp fans out
 * delivery on its own infrastructure, so there is no local per-recipient queue for this path.
 * Otherwise, this falls back to the original local mechanism: one queued row (mailing_list_sent)
 * per currently-subscribed member, sent later, asynchronously, via NewsletterQueueJob.
 * </p>
 *
 * <p>
 * Shared by both the manual admin page (issue #600) and the blog editor's "Notify subscribers"
 * checkbox (issue #500), so both paths behave identically.
 * </p>
 *
 * @author SimIS Inc.
 */
public class NewsletterSendCommand {

  public static int sendBlogPostNotification(MailingList mailingList, BlogPost blogPost, long actorUserId)
      throws DataException {
    if (mailingList == null) {
      throw new DataException("Mailing list was not found");
    }
    if (blogPost == null) {
      throw new DataException("Blog post was not found");
    }

    if (MailChimpCommand.isEnabled()) {
      return sendViaMailChimp(mailingList, blogPost, actorUserId);
    }
    return enqueueViaSmtp(mailingList, blogPost, actorUserId);
  }

  private static int sendViaMailChimp(MailingList mailingList, BlogPost blogPost, long actorUserId)
      throws DataException {
    int recipientCount = MailChimpCommand.getTagMemberCount(mailingList);
    if (recipientCount <= 0) {
      return 0;
    }

    String html = NewsletterEmailCommand.renderBlogPostHtml(blogPost, "*|UNSUB|*");

    String campaignId = MailChimpCommand.createCampaign(mailingList, blogPost.getTitle());
    if (campaignId == null) {
      throw new DataException("Could not create the MailChimp campaign");
    }
    if (!MailChimpCommand.setCampaignContent(campaignId, html)) {
      throw new DataException("Could not set the MailChimp campaign content");
    }
    if (!MailChimpCommand.sendCampaign(campaignId)) {
      throw new DataException("Could not send the MailChimp campaign");
    }

    MailingListHistory history = new MailingListHistory();
    history.setListId(mailingList.getId());
    history.setCreatedBy(actorUserId);
    history.setService("mailchimp");
    history.setEmailCount(recipientCount);
    history.setSubject(blogPost.getTitle());
    history.setBlogPostId(blogPost.getId());
    history.setMailchimpCampaignId(campaignId);

    try (Connection connection = DB.getConnection()) {
      if (MailingListHistoryRepository.add(connection, history) == null) {
        throw new DataException("Could not create the send batch record");
      }
    } catch (SQLException se) {
      throw new DataException("Could not record the newsletter send: " + se.getMessage());
    }

    return recipientCount;
  }

  private static int enqueueViaSmtp(MailingList mailingList, BlogPost blogPost, long actorUserId)
      throws DataException {
    List<MailingListMember> members = MailingListMemberRepository.findActiveMembersForList(mailingList.getId());
    if (members.isEmpty()) {
      return 0;
    }

    try (Connection connection = DB.getConnection();
        AutoStartTransaction a = new AutoStartTransaction(connection);
        AutoRollback transaction = new AutoRollback(connection)) {

      MailingListHistory history = new MailingListHistory();
      history.setListId(mailingList.getId());
      history.setCreatedBy(actorUserId);
      history.setService("smtp");
      history.setEmailCount(members.size());
      history.setSubject(blogPost.getTitle());
      history.setBlogPostId(blogPost.getId());
      history = MailingListHistoryRepository.add(connection, history);
      if (history == null) {
        throw new DataException("Could not create the send batch record");
      }

      List<Long> emailIds = members.stream()
          .map(MailingListMember::getEmailId)
          .collect(Collectors.toList());
      MailingListSentRepository.enqueue(connection, history.getId(), mailingList.getId(), emailIds);

      transaction.commit();
      return members.size();
    } catch (SQLException se) {
      throw new DataException("Could not enqueue the newsletter send: " + se.getMessage());
    }
  }
}
