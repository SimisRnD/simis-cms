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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.SaveBlogCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Widget for displaying a system administration form to add/update blogs
 *
 * @author matt rajkowski
 * @created 8/7/18 10:47 AM
 */
public class BlogFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/blog-form.jsp";

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

    // Form bean
    Blog blog = null;
    if (context.getRequestObject() != null) {
      blog = (Blog) context.getRequestObject();
      context.getRequest().setAttribute("blog", blog);
    } else {
      long blogId = context.getParameterAsLong("blogId");
      if (blogId > -1) {
        blog = LoadBlogCommand.loadBlogById(blogId);
        context.getRequest().setAttribute("blog", blog);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Blog blogBean = new Blog();
    BeanUtils.populate(blogBean, context.getParameterMap());
    blogBean.setCreatedBy(context.getUserId());
    blogBean.setModifiedBy(context.getUserId());

    // Determine additional settings
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the blog
    Blog blog = null;
    try {
      blog = SaveBlogCommand.saveBlog(blogBean);
      if (blog == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(blogBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Blog was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/blogs");
    }
    return context;
  }
}
