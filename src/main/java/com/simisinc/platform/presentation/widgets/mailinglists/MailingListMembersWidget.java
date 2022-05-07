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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.mailinglists.ProcessEmailCSVFileCommand;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.mailinglists.*;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

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

    // Determine criteria
    EmailSpecification specification = new EmailSpecification();
    specification.setMailingListId(mailingList.getId());

    // Load the email addresses
    List<Email> emailList = EmailRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("emailList", emailList);

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
      } catch (Exception e) {
        LOG.error("Download CSV Error", e);
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
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
    }
    // Determine the page to return to
    context.setRedirect("/admin/users");
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

  public WidgetContext delete(WidgetContext context) {
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
      MailingListMemberRepository.remove(email, mailingList);
    }
    context.setRedirect("/admin/mailing-list-members?mailingListId=" + mailingListId);
    return context;
  }
}
