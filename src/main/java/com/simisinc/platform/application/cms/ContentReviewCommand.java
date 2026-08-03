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

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Reviewable;

/**
 * The governed publish path (Project #6, Phase 1; generalized beyond content blocks to web pages in
 * #407): the state machine that takes a draft through
 * <b>draft → submitted → (approved &amp; published | rejected)</b> with a named approver. Operates on
 * any {@link Reviewable} -- originally written for {@code Content} alone, generalized rather than
 * duplicated so every content type gets the same enforcement, including separation of duties.
 *
 * <p>This is the compliance mechanism for CMMC AC.L1-b.1.iv / NIST 800-171 3.1.22 (control of what is
 * posted to publicly accessible systems). It enforces the rules; the caller persists the result and
 * writes the append-only audit record that is the exportable assessment evidence. Two rules are
 * load-bearing and enforced here rather than in the UI, so no screen can bypass them:
 *
 * <ul>
 * <li><b>Separation of duties (AC-5).</b> The approver can never be the person who submitted the
 * content. This is a data rule, not merely a role rule, so it holds even between two users who both
 * hold the approver role.</li>
 * <li><b>Publish is gated on approval.</b> Only a submitted draft can be approved, and only an
 * approved draft should be published — {@link #isApproved(Reviewable)} is the gate the publish path
 * checks, so hot-editing a live page cannot skip review.</li>
 * </ul>
 *
 * <p>The transitions mutate the content's workflow fields; timestamps and the immutable record of who
 * did what and when are captured by the audit trail at the call site.
 *
 * @author elizabeth houser
 */
public class ContentReviewCommand {

  /** A draft being edited, or one sent back by a rejection: the author's to change. */
  public static final String STATUS_DRAFT = "draft";
  /** A draft submitted for review and awaiting an approver's decision. */
  public static final String STATUS_SUBMITTED = "submitted";

  private ContentReviewCommand() {
    // Static command
  }

  /**
   * The author submits the current draft for review. There must be a draft to submit.
   */
  public static void submitForReview(Reviewable content, long submitterId) throws DataException {
    if (content == null || !content.hasDraftContent()) {
      throw new DataException("There is no draft to submit for review");
    }
    if (submitterId <= 0) {
      throw new DataException("A valid submitter is required");
    }
    content.setDraftStatus(STATUS_SUBMITTED);
    content.setSubmittedBy(submitterId);
    // A fresh submission carries no prior approval.
    content.setApprovedBy(-1);
    content.setReleaseReference(null);
  }

  /**
   * An approver approves a submitted draft, recording the release-authority reference. Enforces
   * separation of duties. After this returns, {@link #isApproved(Reviewable)} is true and the publish
   * path may promote the draft to live.
   */
  public static void approve(Reviewable content, long approverId, String releaseReference) throws DataException {
    requireSubmitted(content);
    requireApproverIsNotSubmitter(content, approverId);
    String reference = StringUtils.trimToNull(releaseReference);
    // The column is VARCHAR(255); reject an over-long value rather than letting the database throw
    // mid-approval, which would leave the approver without a clear reason.
    if (reference != null && reference.length() > MAX_RELEASE_REFERENCE_LENGTH) {
      throw new DataException(
          "The release authority reference must be " + MAX_RELEASE_REFERENCE_LENGTH + " characters or fewer");
    }
    content.setApprovedBy(approverId);
    content.setReleaseReference(reference);
  }

  /** Matches the {@code release_reference} column width. */
  public static final int MAX_RELEASE_REFERENCE_LENGTH = 255;

  /**
   * An approver rejects a submitted draft, sending it back to the author to revise and resubmit.
   * Enforces separation of duties.
   */
  public static void reject(Reviewable content, long approverId) throws DataException {
    requireSubmitted(content);
    requireApproverIsNotSubmitter(content, approverId);
    content.setDraftStatus(STATUS_DRAFT);
    content.setApprovedBy(-1);
  }

  /** @return true if the content has a draft awaiting an approver's decision. */
  public static boolean isPendingReview(Reviewable content) {
    return content != null && STATUS_SUBMITTED.equals(content.getDraftStatus());
  }

  /**
   * @return true if the submitted draft has been approved and may be published. The publish path must
   *         check this so an unreviewed change can never reach the live page.
   */
  public static boolean isApproved(Reviewable content) {
    return isPendingReview(content) && content.getApprovedBy() > 0;
  }

  /**
   * The publish gate. When governed publishing is not enabled ({@code reviewRequired} false) a draft may
   * be published directly, as it always could -- backward compatible. When it is enabled, only an
   * approved draft may go live, and there is no other path: this is the enforcement of the "no
   * visual-editor bypass" rule, so hot-editing a live page cannot skip review.
   *
   * @param reviewRequired the {@code content.review.required} site setting
   * @return whether this content may be published now
   */
  public static boolean mayPublish(Reviewable content, boolean reviewRequired) {
    if (!reviewRequired) {
      return true;
    }
    return isApproved(content);
  }

  /**
   * Which review action the given viewer should be offered for this content, so the decision lives here
   * rather than in a JSP expression. Returns one of the {@code OFFER_*} constants.
   *
   * <p>The separation-of-duties rule is reflected in the UI as well as enforced on the action: a
   * submitter looking at their own pending draft is told it is awaiting someone else, never offered the
   * approve button. Offering a button the action would reject is a worse experience and a worse control
   * story than not offering it.
   */
  public static String offerFor(Reviewable content, long viewerId, boolean reviewRequired) {
    if (content == null || !content.hasDraftContent()) {
      return OFFER_NONE;
    }
    if (!reviewRequired) {
      // Ungoverned: the classic direct-publish affordance.
      return OFFER_PUBLISH;
    }
    if (!isPendingReview(content)) {
      // A draft that has not been submitted yet -- its author sends it for review.
      return OFFER_SUBMIT;
    }
    if (viewerId > 0 && viewerId == content.getSubmittedBy()) {
      // Pending, but this viewer submitted it: they cannot decide their own submission.
      return OFFER_AWAITING_OTHER;
    }
    return OFFER_DECIDE;
  }

  /**
   * Whether a save may publish straight to the live page, skipping review. When governed publishing is
   * on the answer is always no: the only route to live is submit → approve, so an editor's Save /
   * Publish Immediately button degrades to a draft save rather than becoming a bypass. Hot-editing a
   * live page is exactly the hole that would otherwise defeat the whole control.
   */
  public static boolean mayPublishDirectly(boolean reviewRequired) {
    return !reviewRequired;
  }

  /** No draft, so nothing to offer. */
  public static final String OFFER_NONE = "none";
  /** Governed publishing is off: offer the direct publish button. */
  public static final String OFFER_PUBLISH = "publish";
  /** A draft not yet submitted: offer "submit for review". */
  public static final String OFFER_SUBMIT = "submit";
  /** Pending review, viewed by its submitter: show status only (separation of duties). */
  public static final String OFFER_AWAITING_OTHER = "awaiting";
  /** Pending review, viewed by someone else: offer approve / reject. */
  public static final String OFFER_DECIDE = "decide";

  private static void requireSubmitted(Reviewable content) throws DataException {
    if (!isPendingReview(content)) {
      throw new DataException("Only a draft submitted for review can be approved or rejected");
    }
  }

  private static void requireApproverIsNotSubmitter(Reviewable content, long approverId) throws DataException {
    if (approverId <= 0) {
      throw new DataException("A valid approver is required");
    }
    if (approverId == content.getSubmittedBy()) {
      throw new DataException(
          "The approver must be different from the person who submitted the content (separation of duties)");
    }
  }

  /** No draft in progress: {@code content.getContent()} is exactly what visitors see. */
  public static final String LIST_STATUS_LIVE = "Live";
  /** A draft was submitted and approved, but has not yet been promoted to live (publish is a separate action). */
  public static final String LIST_STATUS_APPROVED = "Approved";
  /** A draft was submitted for review and awaits an approver's decision. */
  public static final String LIST_STATUS_PENDING_REVIEW = "Pending Review";
  /** A draft is being edited, not yet submitted -- or was sent back by {@link #reject}. */
  public static final String LIST_STATUS_DRAFT = "Draft";

  /**
   * The record's own workflow state, for a bird's-eye list (e.g. {@code /admin/content-list}) that
   * needs to show many blocks' status at a glance. One of the {@code LIST_STATUS_*} constants.
   *
   * <p>Unlike {@link #offerFor}, this is <b>viewer-independent</b> -- it says what state the record
   * is in, not which action one particular viewer should be offered -- and it deliberately ignores
   * the {@code content.review.required} site property, so a list label reflects the record's actual
   * state regardless of whether governed publishing happens to be toggled on right now.
   *
   * <p>There is no separate "Rejected" label: {@link #reject} resets {@code draftStatus} back to
   * {@link #STATUS_DRAFT}, so a rejected-and-not-yet-fixed draft correctly reports as {@link
   * #LIST_STATUS_DRAFT} here too -- per this class's javadoc on {@code STATUS_DRAFT}, it is "the
   * author's to change" either way.
   */
  public static String listStatusLabel(Reviewable content) {
    if (content == null || !content.hasDraftContent()) {
      return LIST_STATUS_LIVE;
    }
    if (isApproved(content)) {
      // Cleared for publish, but publish is a separate, subsequent action -- surface that gap.
      return LIST_STATUS_APPROVED;
    }
    if (isPendingReview(content)) {
      return LIST_STATUS_PENDING_REVIEW;
    }
    // draftStatus is null or STATUS_DRAFT: being edited, or sent back by reject().
    return LIST_STATUS_DRAFT;
  }
}
