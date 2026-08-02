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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.SearchResult;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Returns search results for blog posts
 *
 * @author matt rajkowski
 * @created 7/24/2022 8:53 PM
 */
public class BlogPostSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/blog-search-results-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "15"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    String sortBy = context.getPreferences().get("sortBy");
    if ("new".equals(sortBy)) {
      constraints.setColumnToSortBy("created", "desc");
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the search term
    String query = context.getParameter("query");
    if (StringUtils.isBlank(query)) {
      return null;
    }

    // Determine criteria
    BlogPostSpecification specification = new BlogPostSpecification();
    specification.setPublishedOnly(true);
    specification.setSearchTerm(query);

    // Issue #633: optionally scope results to a single blog. Unlike BlogPostListWidget's
    // required blogUniqueId preference, this one is opt-in -- a site-wide search-results widget
    // instance must still work (searching across every blog) when no preference is set.
    String blogUniqueId = context.getPreferences().get("blogUniqueId");
    if (StringUtils.isNotBlank(blogUniqueId)) {
      Blog blog = LoadBlogCommand.loadBlogByUniqueId(blogUniqueId);
      if (blog != null) {
        specification.setBlogId(blog.getId());
      }
    }

    // Query the data
    List<BlogPost> blogPostList = BlogPostRepository.findAll(specification, constraints);
    SearchAnalyticsCommand.record(context, query, "blog", blogPostList == null ? 0 : blogPostList.size());
    if (blogPostList == null || blogPostList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("blogPostList", blogPostList);

    List<SearchResult> searchResultList = new ArrayList<>();
    for (BlogPost blogPost : blogPostList) {
      // Add the search result
      SearchResult searchResult = new SearchResult();
      searchResult.setPageTitle(blogPost.getTitle());
      if (StringUtils.isNotBlank(blogPost.getSummary())) {
        searchResult.setPageDescription(blogPost.getSummary());
      }
      searchResult.setLink(blogPost.getLink());

      // Include an excerpt
      String htmlContent = HtmlCommand.toHtml(blogPost.getHighlight());
      if (StringUtils.isNotBlank(htmlContent)) {
        htmlContent = StringUtils.replace(htmlContent, "${b}", "<strong>");
        htmlContent = StringUtils.replace(htmlContent, "${/b}", "</strong>");
        searchResult.setHtmlExcerpt(htmlContent);
      }
      searchResultList.add(searchResult);
    }
    context.getRequest().setAttribute("searchResultList", searchResultList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
    context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
