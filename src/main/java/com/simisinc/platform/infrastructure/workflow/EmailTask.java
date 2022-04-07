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
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.application.email.EmailTemplateCommand;
import com.simisinc.platform.application.xapi.WorkflowCommand;
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
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import javax.servlet.ServletContext;
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
  public static final String TO_EMAIL = "to-email";
  public static final String SUBJECT = "subject";
  public static final String TEMPLATE = "template";

  @Override
  public WorkReport execute(WorkContext workContext, TaskContext taskContext) {

    // Expressions decoded from the work context objects
    long toUserId = WorkflowCommand.getValueAsLong(workContext, taskContext, taskContext.get(TO_USER_ID));
    String toRoleList = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TO_ROLE_LIST));
    String toEmail = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TO_EMAIL));
    String subject = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(SUBJECT));
    String template = WorkflowCommand.getValue(workContext, taskContext, taskContext.get(TEMPLATE));

    // Validate the requirements
    if (StringUtils.isBlank(template)) {
      LOG.error("Message or Template is required");
      return new DefaultWorkReport(WorkStatus.FAILED, workContext);
    }
    if (toUserId == -1 && StringUtils.isBlank(toRoleList) && StringUtils.isBlank(toEmail)) {
      LOG.error("User Id, Role List, or Email Address is required");
      return new DefaultWorkReport(WorkStatus.FAILED, workContext);
    }

    try {
      // If using a template, set the objects in the object_list for the template engine
      ServletContext servletContext = SchedulerManager.getServletContext();
      if (servletContext == null) {
        return new DefaultWorkReport(WorkStatus.FAILED, workContext);
      }

      ServletContextTemplateResolver templateResolver = new ServletContextTemplateResolver(servletContext);
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
            ctx.setVariable("validateAccountUrl", siteUrl + "/validate-account?confirmation=" + UrlCommand.encodeUri(thisUser.getAccountToken()));
          }
        }
      }

      // Process and validate the HTML message
      String html = templateEngine.process(template, ctx);
      if (StringUtils.isBlank(html)) {
        LOG.error("Aborting email - Email Template not processed: " + template);
        return new DefaultWorkReport(WorkStatus.FAILED, workContext);
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

      if (toUserList.isEmpty() && StringUtils.isBlank(toEmail)) {
        LOG.error("Aborting email - No email addresses were found");
        return new DefaultWorkReport(WorkStatus.FAILED, workContext);
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
          email.addTo(toEmail);
        }

        // Set the content
        email.setSubject(subject);
        email.setHtmlMsg(html);
        email.setTextMsg(HtmlCommand.text(html));

        // Send the email
        String messageId = email.send();

        // @todo Store in an email log
        LOG.info("The message " + template + " was sent/queued: " + messageId);

      } catch (Exception e) {
        LOG.error("sendConfirmationToUser could not send mail", e);
      }
      return new DefaultWorkReport(WorkStatus.COMPLETED, workContext);
    } catch (Exception e) {
      LOG.error("Email", e);
    }
    return new DefaultWorkReport(WorkStatus.FAILED, workContext);
  }
}
