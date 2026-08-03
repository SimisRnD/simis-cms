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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.util.List;

import com.simisinc.platform.application.cms.DeleteBlogTagCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists the tags for a blog, with delete support (issue #633), mirroring
 * {@code CollectionTagsListWidget} (issue #632).
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogTagsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908896L;

  static String JSP = "/admin/blog-tags-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the parent blog
    long blogId = context.getParameterAsLong("blogId");
    Blog blog = LoadBlogCommand.loadBlogById(blogId);
    if (blog == null) {
      context.setErrorMessage("Error. Blog was not found.");
      return context;
    }
    context.getRequest().setAttribute("blog", blog);

    // Load the tags
    List<BlogTag> tagList = BlogTagRepository.findAllByBlogId(blog.getId());
    context.getRequest().setAttribute("tagList", tagList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long tagId = context.getParameterAsLong("tagId");
    if (tagId > -1) {
      BlogTag tag = BlogTagRepository.findById(tagId);
      try {
        DeleteBlogTagCommand.deleteTag(tag);
        context.setSuccessMessage("Tag deleted");
        context.setRedirect("/admin/blog-tags?blogId=" + tag.getBlogId());
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Tag could not be deleted.");
        return context;
      }
    }

    return context;
  }
}
