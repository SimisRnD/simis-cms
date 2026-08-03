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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.BeanUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.SaveBlogTagCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Add/edit form for a blog tag (issue #633), mirroring {@code CollectionTagFormWidget}
 * (issue #632).
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogTagFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908897L;

  static String JSP = "/admin/blog-tag-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean and parent blog
    long blogId = -1;
    BlogTag tag = (BlogTag) context.getRequestObject();
    if (tag != null) {
      context.getRequest().setAttribute("tag", tag);
      blogId = tag.getBlogId();
    } else {
      long tagId = context.getParameterAsLong("tagId");
      tag = BlogTagRepository.findById(tagId);
      if (tag != null) {
        context.getRequest().setAttribute("tag", tag);
        blogId = tag.getBlogId();
      }
    }

    // Determine the blog
    if (blogId == -1) {
      blogId = context.getParameterAsLong("blogId");
    }
    Blog blog = LoadBlogCommand.loadBlogById(blogId);
    if (blog == null) {
      context.setErrorMessage("Error. Blog was not found.");
      return context;
    }
    context.getRequest().setAttribute("blog", blog);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    BlogTag tagBean = new BlogTag();
    BeanUtils.populate(tagBean, context.getParameterMap());
    tagBean.setCreatedBy(context.getUserId());

    // Save the tag
    BlogTag tag = null;
    try {
      tag = SaveBlogTagCommand.saveTag(tagBean);
      if (tag == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(tagBean);
      if (tagBean.getId() > -1) {
        context.setWarningMessage("This name appears to be a duplicate. Please try again.");
        context.setRedirect("/admin/blog-tag?tagId=" + tagBean.getId());
      } else {
        context.setRedirect("/admin/blog-tags?blogId=" + tagBean.getBlogId());
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Tag was saved");
    context.setRedirect("/admin/blog-tags?blogId=" + tag.getBlogId());
    return context;
  }
}
