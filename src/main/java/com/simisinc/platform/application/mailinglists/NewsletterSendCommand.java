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
 * Queues a newsletter send: one batch header (mailing_list_history) plus one queued row
 * (mailing_list_sent) per currently-subscribed member of the list. The actual sending happens
 * later, asynchronously, via NewsletterQueueJob -- this only enqueues.
 *
 * <p>
 * Shared by both the manual admin page (issue #600) and the blog editor's "Notify subscribers"
 * checkbox (issue #500), so both paths enqueue identically.
 * </p>
 *
 * @author SimIS Inc.
 */
public class NewsletterSendCommand {

  public static int enqueueBlogPostNotification(MailingList mailingList, BlogPost blogPost, long actorUserId)
      throws DataException {
    if (mailingList == null) {
      throw new DataException("Mailing list was not found");
    }
    if (blogPost == null) {
      throw new DataException("Blog post was not found");
    }

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
