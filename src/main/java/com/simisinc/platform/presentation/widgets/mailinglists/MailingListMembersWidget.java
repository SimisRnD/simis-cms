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

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.IpRangeCommand;
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.mailinglists.ProcessEmailCSVFileCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberDeletedEvent;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.*;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/19 10:17 PM
 */
public class MailingListMembersWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/mailing-list-members.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the mailing list
    long mailingListId = context.getParameterAsLong("mailingListId");
    MailingList mailingList = MailingListRepository.findById(mailingListId);
    if (mailingList == null) {
      //error
      return null;
    }
    context.getRequest().setAttribute("mailingList", mailingList);

    // Determine the search/filter criteria (GET, so it's bookmarkable and survives paging --
    // see recordPagingParams below, which must carry these or they'd silently reset on page 2+)
    String searchName = context.getParameter("searchName");
    String searchEmail = context.getParameter("searchEmail");
    String status = context.getParameter("status");
    context.getRequest().setAttribute("searchName", searchName);
    context.getRequest().setAttribute("searchEmail", searchEmail);
    context.getRequest().setAttribute("status", status);

    // Determine criteria
    MailingListMemberSpecification specification = new MailingListMemberSpecification();
    specification.setMailingListId(mailingList.getId());
    if (StringUtils.isNotBlank(searchName)) {
      specification.setMatchesName(searchName);
    }
    if (StringUtils.isNotBlank(searchEmail)) {
      specification.setMatchesEmail(searchEmail);
    }
    if (StringUtils.isNotBlank(status)) {
      specification.setStatus(status);
    }

    // Load the list's members
    List<MailingListMember> memberList = MailingListMemberRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("memberList", memberList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Determine the mailing list
    long mailingListId = context.getParameterAsLong("mailingListId");
    MailingList mailingList = MailingListRepository.findById(mailingListId);
    if (mailingList == null) {
      return context;
    }

    // Determine the action
    String command = context.getParameter("command");
    if ("uploadCSVFile".equals(command)) {
      return uploadCSVFileAction(context, mailingList);
    }

    if ("blockIP".equals(command)) {
      return blockIPAction(context, mailingList);
    }

    if ("downloadCSVFile".equals(command)) {
      LOG.debug("User is downloading a file...");
      // Create a specification
      MailingListMemberSpecification specification = new MailingListMemberSpecification();
      specification.setMailingListId(mailingList.getId());
      // Prepare to save the temporary file
      String extension = "csv";
      String displayFilename = "mailing-list-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
      File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
      try {
        // Export the data to the file
        MailingListMemberRepository.export(specification, null, tempFile);
        // Send it
        String mimeType = "text/csv";
        MultipartFileSender.fromFile(tempFile)
            .with(context.getRequest())
            .with(context.getResponse())
            .withMimeType(mimeType)
            .withFilename(displayFilename)
            .serveResource();
        // Record the export of mailing-list member PII (email, name, subscription state)
        AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.SUCCESS,
            "mailing_list_members", String.valueOf(mailingList.getId()), displayFilename, "format=" + extension);
      } catch (Exception e) {
        LOG.error("Download CSV Error", e);
        AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.FAILURE,
            "mailing_list_members", String.valueOf(mailingList.getId()), displayFilename, "format=" + extension);
      } finally {
        if (tempFile.exists()) {
          LOG.warn("Deleting a temporary file: " + tempFile.getAbsolutePath());
          tempFile.delete();
        }
      }
      context.setHandledResponse(true);
      return context;
    }

    // Default to adding an email
    return addEmailAction(context, mailingList);
  }

  private WidgetContext uploadCSVFileAction(WidgetContext context, MailingList mailingList) {
    LOG.info("User is uploading a mailing list file...");
    try {
      int memberCount = ProcessEmailCSVFileCommand.processCSV(context, mailingList);
      context.setSuccessMessage(memberCount + " email" + (memberCount != 1 ? "s" : "") + " added");
      // Record the import of mailing-list member PII (email, name), mirroring downloadCSVFile's
      // data.export below -- data.import didn't exist anywhere in the codebase before this
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.import", AuditEventCommand.SUCCESS,
          "mailing_list_members", String.valueOf(mailingList.getId()), mailingList.getName(), "memberCount=" + memberCount);
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.import", AuditEventCommand.FAILURE,
          "mailing_list_members", String.valueOf(mailingList.getId()), mailingList.getName(), e.getMessage());
    }
    // Determine the page to return to
    context.setRedirect("/admin/mailing-list-members?mailingListId=" + mailingList.getId());
    return context;
  }

  private WidgetContext addEmailAction(WidgetContext context, MailingList mailingList) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Email emailBean = new Email();
    BeanUtils.populate(emailBean, context.getParameterMap());
    if (context.getUserSession().isLoggedIn()) {
      emailBean.setCreatedBy(context.getUserId());
      emailBean.setModifiedBy(context.getUserId());
    }
    emailBean.setSource("Admin form");
    emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));

    // Save the Email
    try {
      SaveEmailCommand.saveEmail(emailBean, mailingList);
    } catch (DataException e) {
      LOG.error("Save email error", e);
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(emailBean);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Email was added");
    context.setRedirect("/admin/mailing-list-members?mailingListId=" + mailingList.getId());
    return context;
  }

  /**
   * Feeds a member's captured IP into the database-backed blocked-IP list (see docs/ip-blocking.md).
   * Blocks the IP only - the mailing-list member row is left untouched; use the separate Delete action
   * if the member itself should also be removed.
   */
  private WidgetContext blockIPAction(WidgetContext context, MailingList mailingList) {
    context.setRedirect("/admin/mailing-list-members?mailingListId=" + mailingList.getId());

    long emailId = context.getParameterAsLong("emailId");
    Email email = EmailRepository.findById(emailId);
    if (email == null) {
      context.setErrorMessage("Email record was not found");
      return context;
    }

    String ipAddress = email.getIpAddress();
    if (StringUtils.isBlank(ipAddress)) {
      context.setErrorMessage("No IP address is on file for this member");
      return context;
    }

    // Don't allow blocking your own current IP, whether an exact match or as part of a CIDR range
    if (IpRangeCommand.matches(ipAddress, context.getRequest().getRemoteAddr())) {
      context.setErrorMessage("Cannot block your own IP");
      return context;
    }

    // Skip if already blocked
    if (BlockedIPRepository.findByIpAddress(ipAddress) != null) {
      context.setWarningMessage("That IP is already blocked");
      return context;
    }

    BlockedIP blockedIPBean = new BlockedIP();
    blockedIPBean.setIpAddress(ipAddress);
    blockedIPBean.setReason("Blocked from mailing list member: " + email.getEmail());
    try {
      BlockedIP blockedIP = SaveBlockedIPCommand.save(blockedIPBean);
      if (blockedIP == null) {
        throw new AppException("The IP could not be blocked due to a system error. Please try again.");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "blocked_ip.add", AuditEventCommand.SUCCESS,
          "blocked_ip", String.valueOf(blockedIP.getId()), blockedIP.getIpAddress(), blockedIP.getReason());
      context.setSuccessMessage("IP " + ipAddress + " has been blocked");
    } catch (DataException | AppException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "blocked_ip.add", AuditEventCommand.FAILURE,
          "blocked_ip", null, ipAddress, e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Permission is required (mirrors post()'s check above) -- the UI only offers this action to
    // admins/community-managers, and the action must enforce the same restriction directly,
    // since a raw POST to this action bypasses whatever the UI chooses to render.
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      LOG.warn("Blocked mailing list member removal by an unauthorized user: " + context.getUserId());
      return context;
    }

    // Determine what's being removed
    long mailingListId = context.getParameterAsLong("mailingListId");
    long emailId = context.getParameterAsLong("emailId");
    if (mailingListId > -1 && emailId > -1) {
      MailingList mailingList = MailingListRepository.findById(mailingListId);
      if (mailingList == null) {
        context.setErrorMessage("Mailing list not found");
      }
      Email email = EmailRepository.findById(emailId);
      if (email == null) {
        context.setErrorMessage("Email address was not found");
      }
      // issue #452: capture a snapshot before removal -- the row won't exist to look up afterward
      MailingListMember member = MailingListMemberRepository.findByListAndEmail(mailingListId, emailId);
      MailingListMemberRepository.remove(email, mailingList);
      if (member != null && mailingList != null) {
        User actingUser = context.getUserSession() != null ? context.getUserSession().getUser() : null;
        WorkflowManager.triggerWorkflowForEvent(new MailingListMemberDeletedEvent(member, mailingList, actingUser));
      }
    }
    context.setRedirect("/admin/mailing-list-members?mailingListId=" + mailingListId);
    return context;
  }
}
