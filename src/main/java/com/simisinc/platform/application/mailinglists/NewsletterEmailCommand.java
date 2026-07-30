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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.application.email.EmailTemplateCommand;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.ImageHtmlEmail;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.ServletContext;

/**
 * Sends a single newsletter email to a single recipient. Every call prepares its own, independent
 * ImageHtmlEmail with exactly one addTo -- unlike SendAdminEmailCommand/SendCommunityManagerEmailCommand
 * /SendEcommerceManagerEmailCommand, which all add every intended recipient to the SAME email's To:
 * field before a single send(). That shared-object pattern is fine for a small trusted internal
 * role-based list, but would leak every subscriber's address to every other subscriber if reused
 * here for a public newsletter -- so this intentionally does not follow it.
 *
 * @author SimIS Inc.
 */
public class NewsletterEmailCommand {

  private static final String BLOG_POST_TEMPLATE = "mailinglists/newsletter-blog-post";

  public static void sendBlogPostNotification(String toEmail, BlogPost blogPost, String unsubscribeUrl)
      throws EmailException, DataException {

    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String html = renderBlogPostHtml(blogPost, unsubscribeUrl);

    ImageHtmlEmail email = EmailCommand.prepareNewEmail(siteUrl);
    email.addTo(toEmail);
    email.setSubject(blogPost.getTitle());
    email.setHtmlMsg(html);
    email.setTextMsg(HtmlCommand.text(html));
    email.send();
  }

  /**
   * Renders the blog-post notification template to an HTML string, without sending anything. Used
   * for a direct per-recipient SMTP send (unsubscribeUrl is a real per-member token link) as well
   * as for a MailChimp Campaign's content (unsubscribeUrl is MailChimp's own "*|UNSUB|*" merge
   * tag, so MailChimp substitutes a personalized link per recipient at delivery).
   */
  public static String renderBlogPostHtml(BlogPost blogPost, String unsubscribeUrl) throws DataException {
    ServletContext servletContext = SchedulerManager.getServletContext();
    if (servletContext == null) {
      throw new DataException("Servlet context is not available");
    }

    JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
    WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setPrefix("/WEB-INF/email-templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setCacheable(true);
    templateResolver.setCacheTTLMs(3600000L);

    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);

    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");

    Context ctx = EmailTemplateCommand.createSiteContext();
    ctx.setVariable("blogPost", blogPost);
    ctx.setVariable("blogPostUrl", siteUrl + blogPost.getLink());
    ctx.setVariable("unsubscribeUrl", unsubscribeUrl);

    String html = templateEngine.process(BLOG_POST_TEMPLATE, ctx);
    if (StringUtils.isBlank(html)) {
      throw new DataException("Newsletter email template did not render: " + BLOG_POST_TEMPLATE);
    }
    return html;
  }
}
