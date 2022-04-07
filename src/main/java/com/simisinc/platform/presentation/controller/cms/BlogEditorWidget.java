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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageTemplateRepository;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 10:47 AM
 */
public class BlogEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Determine the state of the blog post
    BlogPost blogPost = null;
    if (context.getRequestObject() != null) {
      blogPost = (BlogPost) context.getRequestObject();
      context.getRequest().setAttribute("blogPost", blogPost);
    } else {
      long blogPostId = context.getParameterAsLong("blogPostId");
      if (blogPostId > -1) {
        blogPost = LoadBlogPostCommand.loadBlogPostById(blogPostId);
        context.getRequest().setAttribute("blogPost", blogPost);
      }
    }

    // Determine the blog for this post
    Blog blog = null;
    String blogUniqueId = context.getParameter("blogUniqueId");
    if (StringUtils.isNotBlank(blogUniqueId)) {
      blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
    } else if (blogPost != null) {
      blog = LoadBlogCommand.loadBlogById(blogPost.getBlogId());
    }
    // Make sure the blog exists
    if (blog == null) {
      // Auto-create it if an admin
      if (context.getUserSession().hasRole("admin")) {
        // The Blog needs an administrative record
        Blog blogBean = new Blog();
        blogBean.setName(blogUniqueId);
        blogBean.setUniqueId(GenerateBlogUniqueIdCommand.generateUniqueId(null, blogBean));
        blogBean.setCreatedBy(context.getUserId());
        blogBean.setModifiedBy(context.getUserId());
        blogBean.setEnabled(true);
        blog = BlogRepository.save(blogBean);
        // The blog needs a page template
        WebPageTemplate template = WebPageTemplateRepository.findByName("Blog Post Article Page");
        if (template != null) {
          String link = "/" + blogUniqueId + "/*";
          WebPage webPage = WebPageRepository.findByLink(link);
          if (webPage == null) {
            String pageXml = template.getPageXml();
            pageXml = StringUtils.replace(pageXml, "${webPageName}", blogBean.getUniqueId());
            webPage = new WebPage(link, pageXml);
            webPage.setCreatedBy(context.getUserId());
            try {
              SaveWebPageCommand.saveWebPage(webPage);
            } catch (DataException e) {
              // No concern yet
            }
          }
        }
      }
      if (blog == null) {
        context.setErrorMessage("The related blog is required");
        LOG.error("The related blog is required");
        return context;
      }
    }
    context.getRequest().setAttribute("blog", blog);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    BlogPost blogPostBean = new BlogPost();
    BeanUtils.populate(blogPostBean, context.getParameterMap());
    blogPostBean.setCreatedBy(context.getUserId());
    blogPostBean.setModifiedBy(context.getUserId());

    String enabled = context.getParameter("enabled");
    if (StringUtils.isNotBlank(enabled)) {
      blogPostBean.setPublished(new Timestamp(System.currentTimeMillis()));
    } else {
      blogPostBean.setPublished(null);
    }

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the blog post
    BlogPost blogPost = null;
    try {
      blogPost = SaveBlogPostCommand.saveBlogPost(blogPostBean);
      if (blogPost == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(blogPostBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Blog post was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/" + blogPost.getUniqueId());
    }
    return context;
  }
}
