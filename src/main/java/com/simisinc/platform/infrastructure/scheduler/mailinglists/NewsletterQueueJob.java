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

package com.simisinc.platform.infrastructure.scheduler.mailinglists;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.mailinglists.NewsletterEmailCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.mailinglists.MailingListHistory;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.domain.model.mailinglists.MailingListSent;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListHistoryRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListSentRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends queued newsletter emails a batch at a time. Modeled on
 * OrderManagementProcessNewOrders, with two additions that template doesn't demonstrate: a real
 * claim-before-work step (MailingListSentRepository.claimBatch flips queued rows to processing
 * before this job touches them) and a bounded retry count, since neither exists anywhere else in
 * this codebase's scheduled jobs to copy.
 *
 * <p>
 * The batch size (rather than draining the whole queue in one run) keeps a single execution well
 * under the lock duration below, so a slow run can't overlap the next minutely tick.
 * </p>
 *
 * @author SimIS Inc.
 */
public class NewsletterQueueJob {

  private static Log LOG = LogFactory.getLog(NewsletterQueueJob.class);

  private static final int BATCH_SIZE = 25;
  private static final int MAX_ATTEMPTS = 3;

  @Job(name = "Send queued newsletter emails")
  public static void execute() {

    // Distributed lock -- comfortably longer than a 25-email batch should ever take, so an
    // overrunning execution blocks the next tick rather than racing it
    String lock = LockManager.lock(SchedulerManager.NEWSLETTER_QUEUE_JOB, Duration.ofMinutes(5));
    if (lock == null) {
      return;
    }

    List<MailingListSent> batch = MailingListSentRepository.claimBatch(BATCH_SIZE);
    if (batch.isEmpty()) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Newsletter items claimed for sending: " + batch.size());
    }

    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");

    // A batch is very likely dominated by rows from the same send -- avoid re-loading the same
    // history/blog post record once per recipient
    Map<Long, MailingListHistory> historyCache = new HashMap<>();
    Map<Long, BlogPost> blogPostCache = new HashMap<>();

    for (MailingListSent item : batch) {
      try {
        MailingListHistory history = historyCache.computeIfAbsent(item.getHistoryId(),
            MailingListHistoryRepository::findById);
        if (history == null || history.getBlogPostId() == -1) {
          MailingListSentRepository.markFailedOrRequeue(item, "Send batch record was not found", MAX_ATTEMPTS);
          continue;
        }

        BlogPost blogPost = blogPostCache.computeIfAbsent(history.getBlogPostId(), BlogPostRepository::findById);
        if (blogPost == null) {
          MailingListSentRepository.markFailedOrRequeue(item, "The blog post no longer exists", MAX_ATTEMPTS);
          continue;
        }

        // Re-check current status immediately before sending -- the recipient may have
        // unsubscribed, or their row may have been removed, since this was enqueued
        MailingListMember member = MailingListMemberRepository.findByListAndEmail(item.getListId(), item.getEmailId());
        if (member == null || member.getUnsubscribed() != null || !member.getIsValid()
            || StringUtils.isBlank(member.getEmailAddress())) {
          MailingListSentRepository.markSkipped(item);
          continue;
        }

        String unsubscribeUrl = siteUrl + "/unsubscribe?token=" + UrlCommand.encodeUri(member.getUnsubscribeToken());
        NewsletterEmailCommand.sendBlogPostNotification(member.getEmailAddress(), blogPost, unsubscribeUrl);
        MailingListSentRepository.markSent(item);
      } catch (Exception e) {
        LOG.error("Newsletter send error for item " + item.getId() + ": " + e.getMessage(), e);
        MailingListSentRepository.markFailedOrRequeue(item, StringUtils.left(e.getMessage(), 500), MAX_ATTEMPTS);
      }
    }
  }
}
