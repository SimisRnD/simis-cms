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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Widget to display a list of blogs to the system administrators
 *
 * @author matt rajkowski
 * @created 8/7/18 10:38 AM
 */
public class BlogListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/blog-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the blogs
    List<Blog> blogList = BlogRepository.findAll();
    context.getRequest().setAttribute("blogList", blogList);

    // Determine the post count
    BlogPostSpecification blogPostSpecification = new BlogPostSpecification();
    Map<Long, Long> blogPostCount = new HashMap<>();
    for (Blog blog : blogList) {
      blogPostSpecification.setBlogId(blog.getId());
      long count = BlogPostRepository.findCount(blogPostSpecification);
      blogPostCount.put(blog.getId(), count);
    }
    context.getRequest().setAttribute("blogPostCount", blogPostCount);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Permission is required
    if (!context.hasRole("admin")) {
      context.setWarningMessage("Must be an admin");
      return context;
    }

    // Determine what's being deleted
    long blogId = context.getParameterAsLong("id");
    if (blogId > -1) {
      Blog blog = BlogRepository.findById(blogId);
      if (blog == null) {
        context.setErrorMessage("Blog was not found");
      } else {
        if (BlogRepository.remove(blog)) {
          context.setSuccessMessage("Blog was deleted");
        } else {
          context.setWarningMessage("Blog could not be deleted");
        }
      }
    }
    context.setRedirect(context.getUri());
    return context;
  }
}
