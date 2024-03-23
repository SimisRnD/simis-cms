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

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays the title of the blog post
 *
 * @author matt rajkowski
 * @created 8/20/19 9:56 PM
 */
public class BlogPostNameWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-post-name.jsp";
  static String TEMPLATE = "/cms/blog-post-name.html";

  public WidgetContext execute(WidgetContext context) {

    // Determine the blog
    Blog blog = BlogPostWidget.retrieveValidatedBlogFromPreferences(context);
    if (blog == null) {
      return null;
    }
    context.getRequest().setAttribute("blog", blog);

    // Determine the blog post
    BlogPost blogPost = BlogPostWidget.retrieveValidatedBlogPostFromUrl(context, blog);
    if (blogPost == null) {
      return null;
    }
    context.getRequest().setAttribute("blogPost", blogPost);

    // Show the content
    context.setJsp(JSP);
    context.setTemplate(TEMPLATE);
    return context;
  }
}
