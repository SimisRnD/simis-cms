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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists and retrieves mailing list member objects
 *
 * @author matt rajkowski
 * @created 3/25/19 9:10 PM
 */
public class MailingListMemberRepository {

  private static Log LOG = LogFactory.getLog(MailingListMemberRepository.class);

  private static String TABLE_NAME = "mailing_list_members";
  private static String JOIN =
      "LEFT JOIN emails ON (mailing_list_members.email_id = emails.email_id) " +
      "LEFT JOIN mailing_lists ON (mailing_list_members.list_id = mailing_lists.list_id)";
  private static String[] PRIMARY_KEY = new String[]{"member_id"};

  // Statuses ZeroBounce itself calls undeliverable/dangerous (issue #564). catch-all and unknown
  // are deliberately excluded -- ZeroBounce isn't claiming those are bad, just unresolved, so
  // quarantining them would risk archiving real subscribers ZeroBounce simply couldn't fully verify.
  private static final String QUARANTINE_TRIGGER_STATUSES_SQL = "('invalid', 'spamtrap', 'abuse', 'do_not_mail')";

  private static final int DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT = 10;
  private static final int DEFAULT_CONFIRMATION_EXPIRY_DAYS = 7;

  /** Whether {@link #addEmailToList} inserted a new member row or reactivated an existing one
   *  (issue #452 -- the caller needs this to fire a created vs. updated lifecycle event), plus the
   *  persisted row so the caller doesn't have to look it up separately. {@code
   *  previouslyUnsubscribed} distinguishes a genuine reactivation (the row existed and was
   *  unsubscribed) from a harmless re-add of an already-active member (the row existed and was
   *  never unsubscribed) -- both land in the {@code created == false} branch, but only the former
   *  is a real state change worth an event. */
  public static final class AddToListResult {
    private final boolean created;
    private final boolean previouslyUnsubscribed;
    private final boolean requiresConfirmation;
    private final boolean confirmationEmailNeeded;
    private final MailingListMember member;

    public AddToListResult(boolean created, boolean previouslyUnsubscribed, MailingListMember member) {
      this(created, previouslyUnsubscribed, false, false, member);
    }

    /** @param requiresConfirmation true when this call left the membership pending double
     *  opt-in confirmation instead of activating it -- see {@link #addEmailToList(Email,
     *  MailingList, boolean, Timestamp)}. */
    public AddToListResult(boolean created, boolean previouslyUnsubscribed, boolean requiresConfirmation,
        MailingListMember member) {
      this(created, previouslyUnsubscribed, requiresConfirmation, requiresConfirmation, member);
    }

    /** @param confirmationEmailNeeded true only when this call actually minted a fresh
     *  confirm_token (a brand-new pending row, or an existing one whose prior token had expired
     *  or never existed) -- false when an already-live, unexpired token was reused instead. This
     *  is what stops a resubmitted address from getting a fresh "please confirm" email on every
     *  request; see the "requiresConfirmation" branch of {@link #addEmailToList}. */
    public AddToListResult(boolean created, boolean previouslyUnsubscribed, boolean requiresConfirmation,
        boolean confirmationEmailNeeded, MailingListMember member) {
      this.created = created;
      this.previouslyUnsubscribed = previouslyUnsubscribed;
      this.requiresConfirmation = requiresConfirmation;
      this.confirmationEmailNeeded = confirmationEmailNeeded;
      this.member = member;
    }

    public boolean isCreated() {
      return created;
    }

    public boolean wasPreviouslyUnsubscribed() {
      return previouslyUnsubscribed;
    }

    public boolean requiresConfirmation() {
      return requiresConfirmation;
    }

    public boolean confirmationEmailNeeded() {
      return confirmationEmailNeeded;
    }

    public MailingListMember getMember() {
      return member;
    }
  }

  public static AddToListResult addEmailToList(Email email, MailingList mailingList) {
    return addEmailToList(email, mailingList, false, null);
  }

  /**
   * @param requiresConfirmation Double opt-in: public-facing signup paths must not activate a new
   *     membership, or reactivate a previously-unsubscribed one, until the address owner proves
   *     control by clicking the link {@link com.simisinc.platform.application.mailinglists.MailingListConfirmationCommand}
   *     emails them. Trusted paths -- CSV import, admin manual-add -- pass false and get the
   *     pre-existing immediate-activation behavior. Ignored (may be null) when false.
   * @param confirmTokenExpires when the freshly-issued confirm_token stops being honored by
   *     {@link #findByConfirmToken}; the caller resolves this from the configurable
   *     mailing-list.confirmation.expiryDays site property (see {@link
   *     #resolveConfirmationExpiryDays}) so every list in a single multi-list signup shares one
   *     expiry rather than drifting by milliseconds between DB calls.
   */
  public static AddToListResult addEmailToList(Email email, MailingList mailingList, boolean requiresConfirmation,
      Timestamp confirmTokenExpires) {
    // Capture prior state before mutating, so the caller can tell a genuine reactivation (was
    // unsubscribed) from a harmless re-add of an already-active member (issue #452)
    MailingListMember existingBefore = findByListAndEmail(mailingList.getId(), email.getId());
    boolean previouslyUnsubscribed = existingBefore != null && existingBefore.getUnsubscribed() != null;
    // A non-null quarantine_reason means quarantineFlaggedMembers() (issue #564) previously
    // archived this membership for a confirmed-bad deliverability status (spamtrap/invalid/abuse/
    // do_not_mail). That decision must not be silently reversed just because the same address
    // resubscribes -- via the public signup form, a MailChimp sync, or a replayed subscription job.
    boolean previouslyQuarantined = existingBefore != null && StringUtils.isNotBlank(existingBefore.getQuarantineReason());
    // Already a confirmed, active member -- a duplicate signup is a harmless no-op, not a fresh
    // consent event, so it never needs to (re)confirm or re-notify.
    boolean alreadyActiveMember = existingBefore != null && existingBefore.getIsValid() && !previouslyUnsubscribed
        && !previouslyQuarantined;

    // Determine if the email is already listed
    SqlUtils insertValues = new SqlUtils()
        .add("list_id", mailingList.getId())
        .add("email_id", email.getId())
        .addIfExists("created_by", email.getCreatedBy(), -1)
        .addIfExists("modified_by", email.getCreatedBy(), -1);
    if (requiresConfirmation) {
      applyPendingConfirmation(insertValues, confirmTokenExpires);
    } else {
      insertValues.add("is_valid", true);
    }
    long memberId = DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY);
    boolean created = memberId > -1;
    boolean confirmationEmailNeeded = created && requiresConfirmation;
    if (created) {
      // New member - Update the related count
      String set = "member_count = member_count + 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    } else if (previouslyQuarantined) {
      // Blocked reactivation: leave is_valid=false and the quarantined/quarantine_reason columns
      // untouched. A quarantined address only becomes eligible for sends again through deliberate
      // admin review (the member-management table, issue #763), never automatically on resubscribe.
      LOG.warn("Blocked reactivation of a quarantined mailing list member: listId=" + mailingList.getId() +
          ", emailId=" + email.getId() + ", quarantineReason=" + existingBefore.getQuarantineReason());
      SaveAuditEventCommand.recordAdminEvent("configuration", "mailing_list.reactivation_blocked", "success",
          -1L, "system", null, null, "mailing_list_members", String.valueOf(existingBefore.getId()),
          existingBefore.getEmailAddress(), "quarantine_reason=" + existingBefore.getQuarantineReason());
    } else if (alreadyActiveMember && requiresConfirmation) {
      // Nothing to do -- re-adding an already-confirmed, already-active member through a
      // confirmation-requiring path must not re-issue a token or re-send a confirmation email.
      // (requiresConfirmation=false falls through to the unconditional "make active" branch
      // below, exactly as it did before double opt-in existed -- a harmless no-op update.)
    } else if (requiresConfirmation) {
      // Existing but inactive (unsubscribed, or a stale never-confirmed pending signup) row being
      // re-added through a path that requires confirmation. Deliberately does NOT touch
      // unsubscribed/is_valid here -- only confirmByToken() does that, once they click.
      //
      // If a still-live (unexpired) token is already outstanding, reuse it instead of minting a
      // new one and sending another email -- without this, resubmitting the same address (the
      // public form has no login/ownership check) would re-send a real outbound email on every
      // single request, an unthrottled spam/mail-bomb primitive against an arbitrary address.
      // Capping to one send per still-valid token means the worst case is exactly what every
      // double opt-in system already accepts as normal: one confirmation email per address per
      // confirm-link lifetime, however many times someone resubmits it.
      boolean hasLiveToken = existingBefore != null && StringUtils.isNotBlank(existingBefore.getConfirmToken())
          && existingBefore.getConfirmTokenExpires() != null
          && existingBefore.getConfirmTokenExpires().after(new Timestamp(System.currentTimeMillis()));
      if (!hasLiveToken) {
        SqlUtils updateValues = new SqlUtils()
            .add("modified", new Timestamp(System.currentTimeMillis()))
            .addIfExists("modified_by", email.getModifiedBy(), -1);
        applyPendingConfirmation(updateValues, confirmTokenExpires);
        SqlUtils where = new SqlUtils()
            .add("list_id = ?", mailingList.getId())
            .add("email_id = ?", email.getId());
        DB.update(TABLE_NAME, updateValues, where);
        confirmationEmailNeeded = true;
      }
    } else {
      // Make sure email is set to subscribed. Also clears confirm_token/confirm_token_expires --
      // without this, a member who had an outstanding double opt-in confirmation pending (via a
      // public signup) and was then separately activated through a trusted path (CSV import,
      // admin manual-add) would keep a live, clickable confirm link after already being fully
      // active: clicking it would re-run confirmByToken() and fire a duplicate
      // MailingListMemberCreatedEvent, and the admin member list would mislabel them "Pending
      // Confirmation" indefinitely (see the "pending" status filter below, which relies on
      // confirm_token being cleared the moment a member is genuinely active).
      SqlUtils updateValues = new SqlUtils()
          .add("unsubscribed", (Timestamp) null)
          .add("modified", new Timestamp(System.currentTimeMillis()))
          .addIfExists("modified_by", email.getModifiedBy(), -1)
          .add("is_valid", true)
          .add("confirm_token", (String) null)
          .add("confirm_token_expires", (Timestamp) null);
      SqlUtils where = new SqlUtils()
          .add("list_id = ?", mailingList.getId())
          .add("email_id = ?", email.getId());
      DB.update(TABLE_NAME, updateValues, where);
    }
    boolean pendingConfirmation = requiresConfirmation && !alreadyActiveMember && !previouslyQuarantined;
    return new AddToListResult(created, previouslyUnsubscribed, pendingConfirmation, confirmationEmailNeeded,
        findByListAndEmail(mailingList.getId(), email.getId()));
  }

  private static SqlUtils applyPendingConfirmation(SqlUtils values, Timestamp confirmTokenExpires) {
    return values
        .add("is_valid", false)
        .add("confirm_token", UUID.randomUUID().toString())
        .add("confirm_token_expires", confirmTokenExpires);
  }

  public static void remove(Email email, MailingList mailingList) {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("email_id = ?", email.getId());
    deleteWhere.add("list_id = ?", mailingList.getId());
    int count = DB.deleteFrom(TABLE_NAME, deleteWhere);
    if (count > 0) {
      // Update the related count
      String set = "member_count = member_count - 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    }
  }

  public static void removeAll(Connection connection, MailingList mailingList) throws SQLException {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("list_id = ?", mailingList.getId());
    DB.deleteFrom(connection, TABLE_NAME, deleteWhere);
  }

  public static void unsubscribe(MailingList mailingList, Email email, User user) {
    // Make sure email is set to unsubscribed
    SqlUtils updateValues = new SqlUtils()
        .add("unsubscribed", new Timestamp(System.currentTimeMillis()))
        .add("unsubscribed_by", user.getId())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("modified_by", user.getId())
        .add("is_valid", false);
    SqlUtils where = new SqlUtils()
        .add("list_id = ?", mailingList.getId())
        .add("email_id = ?", email.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Every currently-subscribed, valid member of a list, for enqueueing a send. Generates and
   * persists an unsubscribe_token for any member who doesn't already have one -- a token is only
   * ever needed once a member is actually about to be emailed.
   */
  public static List<MailingListMember> findActiveMembersForList(long listId) {
    SqlUtils select = new SqlUtils().addNames("emails.email AS email_address");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils()
        .add("mailing_list_members.list_id = ?", listId)
        .add("mailing_list_members.is_valid = ?", true)
        .add("mailing_list_members.unsubscribed IS NULL");
    DataResult result = DB.selectAllFrom(TABLE_NAME, select, joins, where, null,
        new DataConstraints().setUseCount(false), MailingListMemberRepository::buildRecordWithEmail);
    List<MailingListMember> members = (List<MailingListMember>) result.getRecords();
    if (members == null) {
      return new ArrayList<>();
    }
    for (MailingListMember member : members) {
      ensureUnsubscribeToken(member);
    }
    return members;
  }

  /** Looks up a member by their numeric primary key, for the REST write endpoint (issue #412 PR3)
   *  to resolve a {@code memberId} path parameter -- every other existing single-row lookup here is
   *  keyed by (list, email) or a single-use token, not the raw id. */
  public static MailingListMember findById(long memberId) {
    SqlUtils select = new SqlUtils().addNames("emails.email AS email_address");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils().add("mailing_list_members.member_id = ?", memberId);
    return (MailingListMember) DB.selectRecordFrom(TABLE_NAME, select, joins, where,
        MailingListMemberRepository::buildRecordWithEmail);
  }

  /** Looks up a member by their single-use unsubscribe link token. */
  public static MailingListMember findByUnsubscribeToken(String token) {
    if (StringUtils.isBlank(token)) {
      return null;
    }
    SqlUtils select = new SqlUtils().addNames("emails.email AS email_address");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils().add("mailing_list_members.unsubscribe_token = ?", token);
    return (MailingListMember) DB.selectRecordFrom(TABLE_NAME, select, joins, where,
        MailingListMemberRepository::buildRecordWithEmail);
  }

  /**
   * Looks up a member by (list, email), for the send job to re-check current subscription status
   * and unsubscribe token immediately before sending -- the member may have unsubscribed, or never
   * had a token generated, since the row was enqueued.
   */
  public static MailingListMember findByListAndEmail(long listId, long emailId) {
    SqlUtils select = new SqlUtils().addNames("emails.email AS email_address");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils()
        .add("mailing_list_members.list_id = ?", listId)
        .add("mailing_list_members.email_id = ?", emailId);
    MailingListMember member = (MailingListMember) DB.selectRecordFrom(TABLE_NAME, select, joins, where,
        MailingListMemberRepository::buildRecordWithEmail);
    if (member != null) {
      ensureUnsubscribeToken(member);
    }
    return member;
  }

  private static void ensureUnsubscribeToken(MailingListMember member) {
    if (StringUtils.isNotBlank(member.getUnsubscribeToken())) {
      return;
    }
    String token = UUID.randomUUID().toString();
    SqlUtils updateValues = new SqlUtils().add("unsubscribe_token", token);
    SqlUtils memberWhere = new SqlUtils().add("member_id = ?", member.getId());
    DB.update(TABLE_NAME, updateValues, memberWhere);
    member.setUnsubscribeToken(token);
  }

  /**
   * Unsubscribes an anonymous recipient by their token (no logged-in User -- the token itself is
   * the authorization). Single-use: clears the token so a re-clicked link lands on a graceful
   * already-unsubscribed state instead of erroring, matching UserRepository's account-token flow.
   */
  public static void unsubscribeByToken(MailingListMember member) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("unsubscribed", now)
        .add("unsubscribed_by", -1, -1)
        .add("modified", now)
        .add("modified_by", -1, -1)
        .add("is_valid", false)
        .add("unsubscribe_token", (String) null);
    SqlUtils where = new SqlUtils().add("member_id = ?", member.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  /** Looks up a pending member by their confirm-subscription link token, only while it hasn't
   *  expired -- an expired token must behave exactly like an unknown one, matching
   *  UserRepository.findByAccountToken's expiry-checked-in-SQL precedent. */
  public static MailingListMember findByConfirmToken(String token) {
    if (StringUtils.isBlank(token)) {
      return null;
    }
    SqlUtils select = new SqlUtils().addNames("emails.email AS email_address");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils()
        .add("mailing_list_members.confirm_token = ?", token)
        .add("(mailing_list_members.confirm_token_expires IS NULL OR mailing_list_members.confirm_token_expires > NOW())");
    return (MailingListMember) DB.selectRecordFrom(TABLE_NAME, select, joins, where,
        MailingListMemberRepository::buildRecordWithEmail);
  }

  /**
   * Activates a pending membership once the address owner proves control by clicking the
   * confirm-subscription link (double opt-in). Single-use: clears the token so a re-clicked link
   * lands on a graceful already-confirmed state instead of erroring, mirroring
   * unsubscribeByToken()'s shape. Also clears unsubscribed -- a reconfirmed resubscribe is no
   * longer unsubscribed, whether this was a brand-new signup (already NULL) or a returning
   * subscriber re-proving consent after having left.
   */
  public static void confirmByToken(MailingListMember member) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("confirmed", now)
        .add("is_valid", true)
        .add("unsubscribed", (Timestamp) null)
        .add("modified", now)
        .add("confirm_token", (String) null)
        .add("confirm_token_expires", (Timestamp) null);
    SqlUtils where = new SqlUtils().add("member_id = ?", member.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Parses the configurable mailing-list.confirmation.expiryDays site property, falling back to
   * the default on a blank/unparseable value and clamping to a sane range -- mirrors
   * resolveQuarantineAlertThresholdPercent's exact shape.
   */
  public static int resolveConfirmationExpiryDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_CONFIRMATION_EXPIRY_DAYS;
    }
    int days;
    try {
      days = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_CONFIRMATION_EXPIRY_DAYS;
    }
    if (days < 1) {
      return 1;
    }
    if (days > 90) {
      return 90;
    }
    return days;
  }

  private static MailingListMember buildRecordWithEmail(ResultSet rs) {
    try {
      MailingListMember record = new MailingListMember();
      record.setId(rs.getLong("member_id"));
      record.setListId(rs.getLong("list_id"));
      record.setEmailId(rs.getLong("email_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setLastEmailed(rs.getTimestamp("last_emailed"));
      record.setUnsubscribed(rs.getTimestamp("unsubscribed"));
      record.setUnsubscribedBy(rs.getLong("unsubscribed_by"));
      record.setUnsubscribeReason(rs.getString("unsubscribe_reason"));
      record.setIsValid(rs.getBoolean("is_valid"));
      record.setQuarantined(rs.getTimestamp("quarantined"));
      record.setQuarantineReason(rs.getString("quarantine_reason"));
      record.setUnsubscribeToken(rs.getString("unsubscribe_token"));
      record.setConfirmed(rs.getTimestamp("confirmed"));
      record.setConfirmToken(rs.getString("confirm_token"));
      record.setConfirmTokenExpires(rs.getTimestamp("confirm_token_expires"));
      record.setEmailAddress(rs.getString("email_address"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecordWithEmail", se);
      return null;
    }
  }

  /** Like {@link #buildRecordWithEmail}, plus the emails-table display/classification columns
   *  needed to render a per-list member table (name, organization, IP, deliverability status). */
  private static MailingListMember buildRecordWithEmailDetails(ResultSet rs) {
    try {
      MailingListMember record = buildRecordWithEmail(rs);
      if (record == null) {
        return null;
      }
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setOrganization(rs.getString("organization"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setValidationStatus(rs.getString("validation_status"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecordWithEmailDetails", se);
      return null;
    }
  }

  /**
   * Members of a list for the admin member-management table (issue #763): supports the same
   * name/email search {@link com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository}
   * offers on the cross-list search flow, plus a status filter over this table's own
   * quarantined/unsubscribed columns (a property of this one list membership, not of the email
   * address globally -- see the quarantine migration's own comment on why that distinction matters).
   */
  public static List<MailingListMember> findAll(MailingListMemberSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils().addNames(
        "emails.email AS email_address", "first_name", "last_name", "organization", "ip_address", "validation_status");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    if (specification != null) {
      if (specification.getMailingListId() > -1) {
        where.add("mailing_list_members.list_id = ?", specification.getMailingListId());
      }
      if (StringUtils.isNotBlank(specification.getMatchesEmail())) {
        where.add("LOWER(emails.email) = LOWER(?)", specification.getMatchesEmail().trim());
      }
      if (StringUtils.isNotBlank(specification.getMatchesName())) {
        // Same escaped LIKE pattern as EmailRepository's cross-list name search
        String likeValue = specification.getMatchesName().trim()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
            .replace("[", "![");
        where.add("LOWER(concat_ws(' ', first_name, last_name)) LIKE LOWER(?) ESCAPE '!'", "%" + likeValue + "%");
      }
      if ("quarantined".equals(specification.getStatus())) {
        where.add("mailing_list_members.quarantined IS NOT NULL");
      } else if ("unsubscribed".equals(specification.getStatus())) {
        // Excludes a row that's unsubscribed but has a live reconfirmation token outstanding --
        // that's a "pending" row (see below), not a durably-unsubscribed one.
        where.add("mailing_list_members.unsubscribed IS NOT NULL");
        where.add("mailing_list_members.confirm_token IS NULL");
      } else if ("pending".equals(specification.getStatus())) {
        // Awaiting double opt-in confirmation -- a live (not-yet-consumed) confirm_token is what
        // distinguishes this from a CSV-imported/admin-added member, which never gets a
        // confirm_token at all (addEmailToList's trusted-path branch always clears it), and from
        // a durably-quarantined/unsubscribed member. Deliberately does NOT require
        // unsubscribed IS NULL -- a previously-unsubscribed member re-signing up on a
        // confirmation-required path keeps their unsubscribed timestamp until they actually
        // reconfirm (see addEmailToList), so that row must still surface here.
        where.add("mailing_list_members.quarantined IS NULL");
        where.add("mailing_list_members.confirm_token IS NOT NULL");
      } else if ("active".equals(specification.getStatus())) {
        // is_valid = true (not just quarantined/unsubscribed both NULL) so a pending-confirmation
        // row -- introduced by double opt-in -- doesn't misclassify as active; see "pending" above.
        where.add("mailing_list_members.quarantined IS NULL");
        where.add("mailing_list_members.unsubscribed IS NULL");
        where.add("mailing_list_members.is_valid = ?", true);
      }
    }
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("mailing_list_members.created desc");
    DataResult result = DB.selectAllFrom(TABLE_NAME, select, joins, where, null, constraints,
        MailingListMemberRepository::buildRecordWithEmailDetails);
    List<MailingListMember> members = (List<MailingListMember>) result.getRecords();
    return members != null ? members : new ArrayList<>();
  }

  /**
   * Distinct people subscribed to at least one list, ever (issue #562). mailing_list_members is
   * unique per (list_id, email_id), not per person -- someone on 3 lists has 3 rows, so this uses
   * COUNT(DISTINCT email_id), not COUNT(*), to avoid counting that person 3 times. Replaces the old
   * "Total Sign-ups" tile, which summed mailing_lists.member_count -- a counter that is never
   * decremented on unsubscribe() (only a hard delete decrements it), so it drifts upward over time.
   */
  public static long countDistinctSubscribers() {
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, null);
  }

  /** Distinct people with at least one currently-valid (not unsubscribed/invalidated) list membership. */
  public static long countActiveSubscribers() {
    SqlUtils where = new SqlUtils().add("is_valid = ?", true);
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /**
   * Distinct people who have unsubscribed from at least one list. Not the complement of
   * countActiveSubscribers(): a person can be actively subscribed to one list and unsubscribed from
   * another at the same time, so these two counts can overlap.
   */
  public static long countUnsubscribed() {
    SqlUtils where = new SqlUtils().add("unsubscribed IS NOT NULL");
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /** Day-bucketed new-subscription counts, zero-filled, mirroring UserRepository.findDailyUserRegistrations. */
  public static List<StatisticsData> findDailySubscriptions(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(member_id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN mailing_list_members ON DATE_TRUNC('day', created) = DATE_TRUNC('day', d.day) " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  /** Month-bucketed new-subscription counts, zero-filled, mirroring UserRepository.findMonthlyUserRegistrations. */
  public static List<StatisticsData> findMonthlySubscriptions(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, COUNT(member_id) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN mailing_list_members ON DATE_TRUNC('month', created) = DATE_TRUNC('month', month) " +
            "GROUP BY d.month " +
            "ORDER BY d.month";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  private static List<StatisticsData> queryDateBucketedCounts(String sqlQuery) {
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(sqlQuery);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong(2)));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Distinct subscribers grouped by deliverability classification (issue #562, feeds off #574's
   * emails.validation_status). NULL means never validated -- ZeroBounce is optional and the
   * classification job only works through a backlog over time, so an unconfigured or
   * still-classifying install legitimately shows most/all subscribers as "unclassified" rather
   * than omitting them from the breakdown.
   */
  public static List<StatisticsData> findClassificationBreakdown() {
    String SQL_QUERY =
        "SELECT COALESCE(emails.validation_status, 'unclassified') AS status, " +
            "COUNT(DISTINCT mailing_list_members.email_id) AS status_count " +
            "FROM " + TABLE_NAME + " " +
            JOIN + " " +
            "GROUP BY COALESCE(emails.validation_status, 'unclassified') " +
            "ORDER BY status_count DESC";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("status"));
        data.setValue(String.valueOf(rs.getLong("status_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * When the most recently-checked current subscriber was last run through deliverability
   * validation, or null if no subscriber has been classified yet. Scoped to subscribers (not a
   * plain MAX(validated_at) over all of emails) so it reflects the freshness of what
   * findClassificationBreakdown() actually shows, not unrelated non-subscriber addresses (emails
   * also serves ecommerce customers) the classification job's backlog happens to include.
   */
  public static Timestamp findLastClassifiedAt() {
    String SQL_QUERY =
        "SELECT MAX(emails.validated_at) AS last_validated " +
            "FROM " + TABLE_NAME + " " +
            JOIN;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getTimestamp("last_validated");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  /**
   * Quarantines (archives, does not delete) every currently-active membership whose linked email
   * has a confirmed-bad deliverability classification and isn't already quarantined (issue #564).
   * Sets is_valid = false, exactly as unsubscribe() does, so a quarantined membership
   * automatically drops out of countActiveSubscribers() -- but does NOT touch the unsubscribed
   * column, since quarantine is a distinct reason a membership stopped being active, not a person
   * choosing to leave.
   *
   * @return the number of memberships newly quarantined by this call
   */
  public static int quarantineFlaggedMembers() {
    String SQL_QUERY =
        "UPDATE " + TABLE_NAME + " SET is_valid = false, quarantined = CURRENT_TIMESTAMP, " +
            "quarantine_reason = emails.validation_status " +
            "FROM emails " +
            "WHERE " + TABLE_NAME + ".email_id = emails.email_id " +
            "AND " + TABLE_NAME + ".quarantined IS NULL " +
            "AND " + TABLE_NAME + ".is_valid = true " +
            "AND emails.validation_status IN " + QUARANTINE_TRIGGER_STATUSES_SQL;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return 0;
    }
  }

  /** Distinct people currently quarantined on at least one list. */
  public static long countQuarantined() {
    SqlUtils where = new SqlUtils().add("quarantined IS NOT NULL");
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /**
   * A 0-100 "mailing list quality" score: of the distinct subscribers who have actually been
   * classified, what percentage are NOT a quarantine-triggering status. Deliberately mirrors
   * QUARANTINE_TRIGGER_STATUSES_SQL rather than only counting "valid" as good, so catch-all/unknown
   * (which don't trigger quarantine either) don't drag the score down as if they were confirmed bad.
   * Returns 100 (no known problems) when nothing has been classified yet, rather than an undefined
   * or misleadingly alarming value -- e.g. before ZeroBounce is even configured.
   */
  public static double findQualityScorePercent() {
    String SQL_QUERY =
        "SELECT COUNT(*) FILTER (WHERE validation_status NOT IN " + QUARANTINE_TRIGGER_STATUSES_SQL + ") AS good_count, " +
            "COUNT(*) AS classified_count " +
            "FROM (SELECT DISTINCT emails.email_id, emails.validation_status " +
            "FROM " + TABLE_NAME + " " + JOIN + " " +
            "WHERE emails.validation_status IS NOT NULL) classified_subscribers";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        long classifiedCount = rs.getLong("classified_count");
        if (classifiedCount == 0) {
          return 100;
        }
        long goodCount = rs.getLong("good_count");
        return 100.0 * goodCount / classifiedCount;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 100;
  }

  /**
   * Parses the configurable mailing-list.quarantine.alertThresholdPercent site property, falling
   * back to the default on a blank or unparseable value and clamping to a sane 0-100 range --
   * mirrors AuditLogRepository.resolveRetentionDays's exact shape for the same reason: a bad or
   * missing config value must degrade to a safe default, never break the dashboard tile.
   */
  public static int resolveQuarantineAlertThresholdPercent(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT;
    }
    int percent;
    try {
      percent = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT;
    }
    if (percent < 0) {
      return 0;
    }
    if (percent > 100) {
      return 100;
    }
    return percent;
  }

  public static void export(MailingListMemberSpecification specification, DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "mailing_lists.name AS list",
            "email",
            "first_name",
            "last_name",
            "organization",
            "mailing_list_members.created AS subscribed",
            "mailing_list_members.unsubscribed AS unsubscribed",
            "emails.unsubscribed AS ref_unsubscribed",
            "is_valid");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    // Use the specification to filter results
    if (specification != null) {
      if (specification.getMailingListId() > -1) {
        where.add("mailing_list_members.list_id = ?", specification.getMailingListId());
      }
    }
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("mailing_list_members.created");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }
}
