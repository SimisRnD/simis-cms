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

import java.io.IOException;
import java.sql.Timestamp;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Write endpoints for mailing list members (issue #412 PR3): {@code POST
 * /mailing-list-members} creates or reactivates a membership, {@code PUT
 * /mailing-list-members/{memberId}} updates one. Both require Bearer token auth and the same
 * admin/community-manager role gate {@link
 * com.simisinc.platform.presentation.widgets.mailinglists.MailingListMembersWidget} enforces for
 * the equivalent admin-UI actions.
 * <p>
 * Reuses the real save logic ({@link SaveEmailCommand}, {@link MailingListMemberRepository})
 * throughout rather than reimplementing validation or state transitions -- see each method's
 * comments for where and why.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberService {

  private static Log LOG = LogFactory.getLog(MailingListMemberService.class);

  // POST /mailing-list-members (issue #412 PR3)
  public ServiceResponse post(ServiceContext context) {

    // RestRequestFilter demotes an unauthenticated (no Bearer token) caller to a role-less guest
    // rather than rejecting outright, since guest reads are legitimate -- but a write never is.
    if (context.getUserId() <= UserSession.GUEST_ID) {
      ServiceResponse response = new ServiceResponse(401);
      response.getError().put("title", "Authentication required");
      return response;
    }
    // No shared permission command exists for mailing lists (unlike EditorPermissionCommand for
    // content) -- this mirrors MailingListMembersWidget.post()'s own inline check exactly.
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      ServiceResponse response = new ServiceResponse(403);
      response.getError().put("title", "Not authorized to manage mailing list members");
      return response;
    }

    MailingListMemberCreateRequest createRequest;
    try {
      createRequest = context.readJsonBody(MailingListMemberCreateRequest.class);
    } catch (IOException | RuntimeException e) {
      LOG.debug("Could not parse the request body: " + e.getMessage());
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Request body must be valid JSON");
      return response;
    }
    if (createRequest == null || StringUtils.isBlank(createRequest.getEmail())) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "email is required");
      return response;
    }

    // Resolved explicitly here rather than reusing SaveEmailCommand's private resolveMailingList(),
    // which auto-creates a "Newsletter" list on first use -- reasonable as an internal default for
    // trusted call sites, but an external API caller mistyping mailingListName should get a clear
    // 400, not a silently-created new mailing list.
    MailingList mailingList = resolveMailingList(createRequest);
    if (mailingList == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Mailing list not found");
      return response;
    }

    Email emailBean = new Email();
    emailBean.setEmail(createRequest.getEmail());
    emailBean.setFirstName(createRequest.getFirstName());
    emailBean.setLastName(createRequest.getLastName());
    emailBean.setOrganization(createRequest.getOrganization());
    emailBean.setSource("REST API");
    emailBean.setCreatedBy(context.getUserId());
    emailBean.setModifiedBy(context.getUserId());
    emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));
    // Deliberately NOT setting ipAddress from the API caller's own remote address: that's the
    // integration's network location, not the subscriber's, and SaveEmailCommand GeoIP-tags the
    // record whenever ipAddress is set -- doing so here would misattribute the member's location.

    Email savedEmail;
    try {
      savedEmail = SaveEmailCommand.saveEmail(emailBean, mailingList);
    } catch (DataException e) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
    if (savedEmail == null) {
      ServiceResponse response = new ServiceResponse(500);
      response.getError().put("title", "Mailing list member could not be saved");
      return response;
    }

    // saveEmail() always creates or updates the (list, email) membership row -- including the
    // existing quarantine-reactivation-blocked path, which still leaves the row in place with
    // is_valid=false rather than omitting it -- so this should always find a row.
    MailingListMember member = MailingListMemberRepository.findByListAndEmail(mailingList.getId(), savedEmail.getId());
    if (member == null) {
      ServiceResponse response = new ServiceResponse(500);
      response.getError().put("title", "Mailing list member could not be saved");
      return response;
    }

    String actorUsername = context.getUser() != null ? context.getUser().getEmail() : null;
    String sourceIp = context.getRequest() != null ? context.getRequest().getRemoteAddr() : null;
    SaveAuditEventCommand.recordAdminEvent("configuration", "mailing_list_member.create", "success",
        context.getUserId(), actorUsername, sourceIp, null,
        "mailing_list_members", String.valueOf(member.getId()), savedEmail.getEmail(), null);

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "mailing-list-member");
    response.setData(new MailingListMemberResponse(member, savedEmail));
    return response;
  }

  // PUT /mailing-list-members/{memberId} (issue #412 PR3)
  //
  // Not listed as its own <service method="put" .../> row in rest-services.xml -- RestServlet
  // dispatches purely by reflecting for a method named after the HTTP verb; see the comment on the
  // POST-capable content/{contentUniqueId} entry there for the same pattern.
  public ServiceResponse put(ServiceContext context) {

    if (context.getUserId() <= UserSession.GUEST_ID) {
      ServiceResponse response = new ServiceResponse(401);
      response.getError().put("title", "Authentication required");
      return response;
    }
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      ServiceResponse response = new ServiceResponse(403);
      response.getError().put("title", "Not authorized to manage mailing list members");
      return response;
    }

    String memberIdParam = context.getPathParam();
    if (!StringUtils.isNumeric(memberIdParam)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "A numeric member id must be specified");
      return response;
    }
    long memberId = Long.parseLong(memberIdParam);

    MailingListMember member = MailingListMemberRepository.findById(memberId);
    if (member == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Mailing list member was not found");
      return response;
    }

    MailingListMemberUpdateRequest updateRequest;
    try {
      updateRequest = context.readJsonBody(MailingListMemberUpdateRequest.class);
    } catch (IOException | RuntimeException e) {
      LOG.debug("Could not parse the request body: " + e.getMessage());
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Request body must be valid JSON");
      return response;
    }
    if (updateRequest == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Request body must be valid JSON");
      return response;
    }
    if (Boolean.FALSE.equals(updateRequest.getUnsubscribed())) {
      // Reactivating a membership is a bigger decision than a raw field flip: it has to go through
      // the same consent-aware path everyone else does (addEmailToList via SaveEmailCommand),
      // which also respects the quarantine-reactivation block (issue #564). Flipping is_valid back
      // on directly here would bypass that protection.
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "unsubscribed cannot be set to false here; resubscribe by POSTing a new member instead");
      return response;
    }

    Email emailRecord = EmailRepository.findById(member.getEmailId());
    if (emailRecord == null) {
      ServiceResponse response = new ServiceResponse(500);
      response.getError().put("title", "The associated email record was not found");
      return response;
    }

    boolean nameFieldsChanged = updateRequest.getFirstName() != null || updateRequest.getLastName() != null
        || updateRequest.getOrganization() != null;
    if (nameFieldsChanged) {
      if (updateRequest.getFirstName() != null) {
        emailRecord.setFirstName(updateRequest.getFirstName());
      }
      if (updateRequest.getLastName() != null) {
        emailRecord.setLastName(updateRequest.getLastName());
      }
      if (updateRequest.getOrganization() != null) {
        emailRecord.setOrganization(updateRequest.getOrganization());
      }
      emailRecord.setModifiedBy(context.getUserId());
      // These fields live on the shared emails table, not this one list membership -- the change
      // is visible on every list (and any ecommerce customer record) this address has, not just
      // this member row. See MailingListMemberResponse's javadoc for the same split.
      EmailRepository.update(emailRecord);
    }

    if (Boolean.TRUE.equals(updateRequest.getUnsubscribed())) {
      MailingList mailingList = MailingListRepository.findById(member.getListId());
      if (mailingList == null) {
        ServiceResponse response = new ServiceResponse(500);
        response.getError().put("title", "The associated mailing list was not found");
        return response;
      }
      MailingListMemberRepository.unsubscribe(mailingList, emailRecord, context.getUser());
      member.setUnsubscribed(new Timestamp(System.currentTimeMillis()));
      member.setIsValid(false);
    }

    String actorUsername = context.getUser() != null ? context.getUser().getEmail() : null;
    String sourceIp = context.getRequest() != null ? context.getRequest().getRemoteAddr() : null;
    SaveAuditEventCommand.recordAdminEvent("configuration", "mailing_list_member.update", "success",
        context.getUserId(), actorUsername, sourceIp, null,
        "mailing_list_members", String.valueOf(member.getId()), emailRecord.getEmail(), null);

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "mailing-list-member");
    response.setData(new MailingListMemberResponse(member, emailRecord));
    return response;
  }

  private MailingList resolveMailingList(MailingListMemberCreateRequest request) {
    if (request.getMailingListId() > -1) {
      return MailingListRepository.findById(request.getMailingListId());
    }
    String name = StringUtils.isNotBlank(request.getMailingListName()) ? request.getMailingListName() : "Newsletter";
    return MailingListRepository.findByName(name);
  }
}
