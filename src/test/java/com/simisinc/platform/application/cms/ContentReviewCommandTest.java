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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Content;

/**
 * Tests the governed publish state machine, with emphasis on the two load-bearing controls:
 * separation of duties (approver != submitter) and publish-gated-on-approval.
 *
 * @author elizabeth houser
 */
class ContentReviewCommandTest {

  private static final long AUTHOR = 10L;
  private static final long APPROVER = 20L;

  private static Content draft() {
    Content content = new Content();
    content.setUniqueId("page-block");
    content.setDraftContent("<p>proposed change</p>");
    return content;
  }

  @Test
  void submitMovesDraftToSubmitted() throws DataException {
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    assertEquals(ContentReviewCommand.STATUS_SUBMITTED, content.getDraftStatus());
    assertEquals(AUTHOR, content.getSubmittedBy());
    assertTrue(ContentReviewCommand.isPendingReview(content));
    assertFalse(ContentReviewCommand.isApproved(content));
  }

  @Test
  void submitRequiresADraft() {
    Content content = new Content(); // no draft content
    assertThrows(DataException.class, () -> ContentReviewCommand.submitForReview(content, AUTHOR));
  }

  @Test
  void approveBySomeoneElseRecordsApproverAndReference() throws DataException {
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    ContentReviewCommand.approve(content, APPROVER, "  cleared per PA case 2026-114  ");
    assertEquals(APPROVER, content.getApprovedBy());
    assertEquals("cleared per PA case 2026-114", content.getReleaseReference()); // trimmed
    assertTrue(ContentReviewCommand.isApproved(content));
  }

  @Test
  void theSubmitterCannotApproveTheirOwnContent() throws DataException {
    // Separation of duties (AC-5) -- the heart of the control. Enforced on the data, not the role, so
    // it holds even if the author also holds the approver role.
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    DataException e = assertThrows(DataException.class,
        () -> ContentReviewCommand.approve(content, AUTHOR, "self-approved"));
    assertTrue(e.getMessage().toLowerCase().contains("separation of duties"), e.getMessage());
    // The content is not approved by the failed attempt.
    assertFalse(ContentReviewCommand.isApproved(content));
  }

  @Test
  void aDraftThatWasNotSubmittedCannotBeApproved() {
    // Publish-gated-on-approval: nothing can be approved (and thus published) without first being
    // submitted for review -- no bypass of the workflow.
    Content content = draft();
    assertThrows(DataException.class, () -> ContentReviewCommand.approve(content, APPROVER, null));
    assertFalse(ContentReviewCommand.isApproved(content));
  }

  @Test
  void rejectSendsItBackToTheAuthor() throws DataException {
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    ContentReviewCommand.reject(content, APPROVER);
    assertEquals(ContentReviewCommand.STATUS_DRAFT, content.getDraftStatus());
    assertFalse(ContentReviewCommand.isApproved(content));
    assertFalse(ContentReviewCommand.isPendingReview(content));
  }

  @Test
  void theSubmitterCannotRejectTheirOwnContent() throws DataException {
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    assertThrows(DataException.class, () -> ContentReviewCommand.reject(content, AUTHOR));
  }

  @Test
  void approvalRequiresAValidApprover() throws DataException {
    Content content = draft();
    ContentReviewCommand.submitForReview(content, AUTHOR);
    assertThrows(DataException.class, () -> ContentReviewCommand.approve(content, -1L, null));
  }
}
