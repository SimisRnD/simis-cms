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

package com.simisinc.platform.infrastructure.workflow;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SendCommunityManagerEmailCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.infrastructure.persistence.workflow.WorkflowNotificationSentRepository;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.application.email.EmailTemplateCommand;
import com.simisinc.platform.application.workflow.WorkflowCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;
import org.jeasy.flows.work.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A workflow task to send email
 *
 * @author matt rajkowski
 * @created 4/29/21 5:32 PM
 */
public class EmailTask implements Work {

  private static Log LOG = LogFactory.getLog(EmailTask.class);

  // Task Context
  public static final String TO_USER_ID = "to-user";
  public static final String TO_ROLE_LIST = "to-role";
  public static final String TO_CAPABILITY = "to-capability";
  public static final String TO_EMAIL = "to-email";
  public static final String SUBJECT = "subject";
  public static final String TEMPLATE = "template";
  /**
   * Optional. When set, this email is sent at most once for this key however many times the
   * playbook runs -- see WorkflowNotificationSentRepository and issue 1643.
   */
  public static final String ONCE_KEY = "once-key";

  @Override
  public WorkReport execute(WorkContext workContext, TaskContext taskContext) {

    // Expressions decoded from the work context objects
    long toUserId = WorkflowCommand.getValueAsLong(workContext, taskContext, taskContext.get(TO_USER_ID));
    String toRoleList = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TO_ROLE_LIST));
    String toCapability = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TO_CAPABILITY));
    String toEmail = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TO_EMAIL));
    String subject = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(SUBJECT));
    String template = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TEMPLATE));
    String onceKey = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(ONCE_KEY));

    // Validate the requirements
    if (StringUtils.isBlank(template)) {
      LOG.error("Message or Template is required");
      return TaskReports.failure(workContext, "Message or Template is required");
    }
    if (toUserId == -1 && StringUtils.isBlank(toRoleList) && StringUtils.isBlank(toCapability)
        && StringUtils.isBlank(toEmail)) {
      LOG.error("User Id, Role List, Capability, or Email Address is required");
      return TaskReports.failure(workContext, "User Id, Role List, Capability, or Email Address is required");
    }

    try {
      // If using a template, set the objects in the object_list for the template engine
      ServletContext servletContext = SchedulerManager.getServletContext();
      if (servletContext == null) {
        return TaskReports.failure(workContext, "The servlet context was not available to render the email");
      }

      JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
      WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
      templateResolver.setTemplateMode(TemplateMode.HTML);
      templateResolver.setPrefix("/WEB-INF/email-templates/");
      templateResolver.setSuffix(".html");
      templateResolver.setCacheTTLMs(Long.valueOf(3600000L));
      templateResolver.setCacheable(true);

      TemplateEngine templateEngine = new TemplateEngine();
      templateEngine.setTemplateResolver(templateResolver);

      // Values for the email
      String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
      String ecommerceFromName = LoadSitePropertyCommand.loadByName("ecommerce.from.name");
      String ecommerceFromEmail = LoadSitePropertyCommand.loadByName("ecommerce.from.email");

      // Prepare the HTML Message Context
      Context ctx = EmailTemplateCommand.createSiteContext();

      // Use the specified event variables
      Set<Map.Entry<String, Object>> entrySet = workContext.getEntrySet();
      for (Map.Entry<String, Object> entry : entrySet) {
        // Skip default keys
        if (entry.getKey().equals("site")) {
          continue;
        }
        // Share the work context key/value
        ctx.setVariable(entry.getKey(), entry.getValue());
        if (entry.getKey().equals("user")) {
          // Set user specific site links
          User thisUser = (User) entry.getValue();
          if (thisUser != null && StringUtils.isNotBlank(thisUser.getAccountToken())) {
            ctx.setVariable("validateAccountUrl", siteUrl + "/validate-account/" + UrlCommand.encodeUri(thisUser.getAccountToken()));
          }
        }
      }

      // Process and validate the HTML message
      String html = templateEngine.process(template, ctx);
      if (StringUtils.isBlank(html)) {
        LOG.error("Aborting email - Email Template not processed: " + template);
        return TaskReports.failure(workContext, "Email template not processed: " + template);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("HTML Message: " + html);
      }

      // Determine who will receive the email
      List<User> toUserList = new ArrayList<>();
      if (toUserId > -1) {
        // A userId is specified
        User toUser = LoadUserCommand.loadUser(toUserId);
        if (toUser != null) {
          toUserList.add(toUser);
        }
      }
      if (toRoleList != null) {
        // Determine the users based on role
        List<User> roleUserList = SendCommunityManagerEmailCommand.getUserList(toRoleList);
        if (roleUserList != null && !roleUserList.isEmpty()) {
          toUserList.addAll(roleUserList);
        }
      }

      if (StringUtils.isNotBlank(toCapability)) {
        // Determine the users by what they are permitted to do, rather than which role they sit in.
        // Role membership and capability are different questions: System Administrator holds
        // community:manage, yet a mail addressed to the community-manager role only ever reached an
        // admin through an empty-role fallback. Addressing the capability asks what the permission
        // model actually expresses, and picks up a time-limited direct grant for exactly as long as
        // it is valid.
        //
        // Added to the list rather than replacing it, and de-duplicated by id, so a workflow may
        // name both a role and a capability without mailing anyone in both twice.
        for (User capabilityUser : LoadUserCommand.loadUsersHoldingCapability(toCapability)) {
          boolean alreadyListed = false;
          for (User existing : toUserList) {
            if (existing.getId() == capabilityUser.getId()) {
              alreadyListed = true;
              break;
            }
          }
          if (!alreadyListed) {
            toUserList.add(capabilityUser);
          }
        }
      }

      if (toUserList.isEmpty() && StringUtils.isBlank(toEmail)) {
        LOG.error("Aborting email - No email addresses were found");
        return TaskReports.failure(workContext, "No email addresses were found");
      }

      // Prepare the email
      try {

        // Site info/from
        ImageHtmlEmail email = EmailCommand.prepareNewEmail(siteUrl);
        if (StringUtils.isNotBlank(ecommerceFromEmail)) {
          if (StringUtils.isNotBlank(ecommerceFromName)) {
            email.setFrom(ecommerceFromEmail, ecommerceFromName);
          } else {
            email.setFrom(ecommerceFromEmail);
          }
        }

        // Determine who will receive the email
        for (User user : toUserList) {
          email.addTo(user.getEmail(), user.getFullName());
        }
        if (StringUtils.isNotBlank(toEmail)) {
          String[] listOfEmails = toEmail.split(",");
          for (String thisEmail : listOfEmails) {
            email.addTo(thisEmail.trim());
          }
        }

        // Set the content
        email.setSubject(subject);
        email.setHtmlMsg(html);
        email.setTextMsg(HtmlCommand.text(html));

        // Claim the one-time key immediately before the send, not earlier: a failure while
        // building the message must not consume the claim and suppress a legitimate retry. If the
        // key is already taken this message has been delivered by an earlier run of this playbook,
        // so report COMPLETED -- the work is done, and reporting failure would trigger yet another
        // retry of a workflow that has nothing left to do.
        if (StringUtils.isNotBlank(onceKey) && !WorkflowNotificationSentRepository.claim(onceKey)) {
          LOG.info("Skipping the '" + template + "' email; already sent for: " + onceKey);
          return new DefaultWorkReport(WorkStatus.COMPLETED, workContext);
        }

        // Send the email
        String messageId;
        try {
          messageId = email.send();
        } catch (Exception sendException) {
          // The send did not happen, so give the key back or the retry this failure is meant to
          // trigger would find the notification already claimed and skip it.
          if (StringUtils.isNotBlank(onceKey)) {
            WorkflowNotificationSentRepository.release(onceKey);
          }
          throw sendException;
        }

        // @todo Store in an email log
        LOG.info("The message " + template + " was sent/queued: " + messageId);

      } catch (Exception e) {
        // Names the template and recipients, because this line is what a duplicate-delivery
        // investigation reads: the enclosing job replays the WHOLE workflow on retry, so a
        // failure reported here after the message already reached the server sends it twice.
        // "sendConfirmationToUser" was copied from a caller that no longer exists -- this task
        // sends every workflow email, admin notifications included, and the old wording sent
        // anyone searching the log toward the submitter confirmation instead.
        LOG.error("Could not send the '" + template + "' email to " + describeRecipients(toUserList, toEmail), e);
        // A FAILED report (not COMPLETED) is required for WorkflowManager.findAndRunWorkflow() to
        // let this propagate to the enclosing JobRunr job's retries=1 (issue #1124) -- returning
        // COMPLETED here regardless of outcome is what made that retry dead code.
        return TaskReports.failure(workContext, "The email could not be sent", e);
      }
      return new DefaultWorkReport(WorkStatus.COMPLETED, workContext);
    } catch (Exception e) {
      // Reached before a send is attempted -- template resolution, recipient lookup, or building
      // the message. Distinguished from the send failure above so the two are not confused in a
      // log: this one delivered nothing, that one may have delivered and still reported failure.
      LOG.error("Could not build the '" + template + "' email", e);
    }
    return TaskReports.failure(workContext, "Could not build the '" + template + "' email");
  }

  /** Recipients for a log line: named accounts and any literal addresses, never more than a few */
  private static String describeRecipients(List<User> toUserList, String toEmail) {
    List<String> addresses = new ArrayList<>();
    if (toUserList != null) {
      for (User user : toUserList) {
        addresses.add(user.getEmail());
      }
    }
    if (StringUtils.isNotBlank(toEmail)) {
      for (String thisEmail : toEmail.split(",")) {
        addresses.add(thisEmail.trim());
      }
    }
    return addresses.isEmpty() ? "(no recipients)" : String.join(", ", addresses);
  }

}
