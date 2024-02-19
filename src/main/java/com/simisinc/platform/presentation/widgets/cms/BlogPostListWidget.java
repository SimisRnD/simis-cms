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
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Displays a list of blog entries
 *
 * @author matt rajkowski
 * @created 8/7/18 11:55 AM
 */
public class BlogPostListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-post-list.jsp";
  static String OVERVIEW_JSP = "/cms/blog-post-list-overview.jsp";
  static String TITLES_JSP = "/cms/blog-post-list-titles.jsp";
  static String CARDS_JSP = "/cms/blog-post-list-cards.jsp";
  static String FEATURED_JSP = "/cms/blog-post-list-featured.jsp";
  static String MASONRY_JSP = "/cms/blog-post-list-masonry.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));

    // Preferences
    context.getRequest().setAttribute("showSort", context.getPreferences().getOrDefault("showSort", "false"));
    context.getRequest().setAttribute("showAuthor", context.getPreferences().getOrDefault("showAuthor", "true"));
    context.getRequest().setAttribute("showDate", context.getPreferences().getOrDefault("showDate", "true"));
    context.getRequest().setAttribute("addDateToTitle", context.getPreferences().getOrDefault("addDateToTitle", "false"));
    context.getRequest().setAttribute("showTags", context.getPreferences().getOrDefault("showTags", "false"));
    context.getRequest().setAttribute("showImage", context.getPreferences().getOrDefault("showImage", "true"));
    context.getRequest().setAttribute("showSummary", context.getPreferences().getOrDefault("showSummary", "true"));
    context.getRequest().setAttribute("showReadMore", context.getPreferences().getOrDefault("showReadMore", "true"));
    context.getRequest().setAttribute("readMoreText", context.getPreferences().getOrDefault("readMoreText", "Read more"));

    // Determine the blog
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
    context.getRequest().setAttribute("blog", blog);

    // Check for a type: recent
    String type = context.getPreferences().get("type");

    // Check for the view
    String view = context.getPreferences().getOrDefault("view", "default");

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    if ("masonry".equals(view)) {
      limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "50"));
    }
    int page = context.getParameterAsInt("page", 1);
    if ("recent".equals(type)) {
      page = 1;
    }
    int itemsPerPage = context.getParameterAsInt("items", limit);

    // Determine the sorting, use values for the request that are separate from the database values
    String sortByValue = context.getParameter("sortBy", "date");
    String sortOrderValue = context.getParameter("sortOrder", "newest");
    String pagingUri = "";
    if (!"date".equals(sortByValue) || !"newest".equals(sortOrderValue)) {
      pagingUri =
          "&sortBy=" + UrlCommand.encodeUri(sortByValue) +
              "&sortOrder=" + UrlCommand.encodeUri(sortOrderValue);
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, pagingUri);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortByValue);
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_ORDER, sortOrderValue);

    // Set the constraints
    String columnToSortBy = "start_date";
    if ("category".equals(sortByValue)) {
      // @todo can this be sorted on?
    }
    String columnSortOrder = "desc";
    if ("oldest".equals(sortOrderValue)) {
      columnSortOrder = "asc";
    } else if ("newest".equals(sortOrderValue)) {
      columnSortOrder = "desc";
    }
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, columnToSortBy, columnSortOrder);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria
    BlogPostSpecification blogPostSpecification = new BlogPostSpecification();
    blogPostSpecification.setBlogId(blog.getId());
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      blogPostSpecification.setPublishedOnly(true);
      blogPostSpecification.setStartDateIsBeforeNow(true);
      blogPostSpecification.setIsWithinEndDate(true);
    }

    // Load the blog posts
    List<BlogPost> blogPostList = BlogPostRepository.findAll(blogPostSpecification, constraints);
    context.getRequest().setAttribute("blogPostList", blogPostList);

    // See if an empty widget can be shown
    if (blogPostList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"))) {
        return null;
      }
    }

    // Show the editor
    if ("overview".equals(view)) {
      context.setJsp(OVERVIEW_JSP);
    } else if ("titles".equals(view)) {
      context.getRequest().setAttribute("showBullets", context.getPreferences().getOrDefault("showBullets", "false"));
      context.setJsp(TITLES_JSP);
    } else if ("cards".equals(view)) {

      // Determine the number of cards to use across
      String smallCardCount = context.getPreferences().getOrDefault("smallCardCount", "3");
      String mediumCardCount = context.getPreferences().get("mediumCardCount");
      String largeCardCount = context.getPreferences().get("largeCardCount");
      if (StringUtils.isBlank(mediumCardCount)) {
        mediumCardCount = smallCardCount;
      }
      if (StringUtils.isBlank(largeCardCount)) {
        largeCardCount = mediumCardCount;
      }
      context.getRequest().setAttribute("smallCardCount", smallCardCount);
      context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
      context.getRequest().setAttribute("largeCardCount", largeCardCount);
      context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));

      context.setJsp(CARDS_JSP);
    } else if ("masonry".equals(view)) {
      context.setJsp(MASONRY_JSP);
    } else if ("featured".equals(view)) {
      context.setJsp(FEATURED_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }
}
