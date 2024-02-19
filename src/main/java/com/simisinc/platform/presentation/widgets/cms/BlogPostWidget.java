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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Displays a single blog post using an article format
 *
 * @author matt rajkowski
 * @created 8/7/18 11:55 AM
 */
public class BlogPostWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-post-details.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("showTitle", context.getPreferences().getOrDefault("showTitle", "true"));
    context.getRequest().setAttribute("showAuthor", context.getPreferences().getOrDefault("showAuthor", "true"));
    context.getRequest().setAttribute("showDate", context.getPreferences().getOrDefault("showDate", "true"));
    context.getRequest().setAttribute("link", context.getPreferences().get("link"));

    // Determine the blog
    Blog blog = retrieveValidatedBlogFromPreferences(context);
    if (blog == null) {
      return null;
    }
    context.getRequest().setAttribute("blog", blog);

    // Determine the blog post
    BlogPost blogPost = retrieveValidatedBlogPostFromUrl(context, blog);
    if (blogPost == null) {
      return null;
    }
    context.getRequest().setAttribute("blogPost", blogPost);

    // Set the HTML page title
    context.setPageTitle(blogPost.getTitle() + " - " + blog.getName());
    if (StringUtils.isNotBlank(blogPost.getSummary())) {
      context.setPageDescription(blogPost.getSummary());
    }
    if (StringUtils.isNotBlank(blogPost.getKeywords())) {
      context.setPageKeywords(blogPost.getKeywords());
    }

    // Show the formatted content
    context.setJsp(JSP);
    return context;
  }

  public static Blog retrieveValidatedBlogFromPreferences(WidgetContext context) {
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
    if (blogUniqueId == null) {
      return null;
    }
    Blog blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
    if (blog == null) {
      return null;
    }
    if (!blog.getEnabled() &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    return blog;
  }

  public static BlogPost retrieveValidatedBlogPostFromUrl(WidgetContext context, Blog blog) {
    if (blog == null) {
      LOG.debug("Requires a blog by specifying a blogUniqueId preference");
      return null;
    }
    String blogPostUniqueId = context.getUri().substring(context.getUri().lastIndexOf("/") + 1);
    BlogPost blogPost = LoadBlogPostCommand.loadBlogPostByUniqueId(blog.getId(), blogPostUniqueId);
    if (blogPost == null) {
      LOG.debug("Blog post not found: " + blog.getId() + " " + blogPostUniqueId);
      return null;
    }
    if (blogPost.getPublished() == null &&
        !(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return null;
    }
    return blogPost;
  }

  public WidgetContext action(WidgetContext context) {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      return context;
    }
    // Find the blog record
    long blogPostId = context.getParameterAsLong("blogPostId");
    BlogPost blogPost = LoadBlogPostCommand.loadBlogPostById(blogPostId);
    if (blogPost == null) {
      context.setErrorMessage("The record was not found");
      return context;
    }
    Blog blog = LoadBlogCommand.loadBlogById(blogPost.getBlogId());
    // Execute the action
    context.setRedirect("/" + blog.getUniqueId());
    String action = context.getParameter("action");
    if ("deletePost".equals(action)) {
      return deletePost(context, blogPost);
    }
    return context;
  }

  private WidgetContext deletePost(WidgetContext context, BlogPost blogPost) {
    // Attempt to delete the blog
    try {
      BlogPostRepository.remove(blogPost);
      context.setSuccessMessage("Post was deleted");
    } catch (Exception e) {
      context.setErrorMessage("The post could not be deleted: " + e.getMessage());
    }
    return context;
  }
}
