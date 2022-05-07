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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.simisinc.platform.presentation.widgets.cms.BlogPostListWidget.JSP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class BlogPostListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("blogUniqueId", "news");

    // Widgets can have parameters
    //widgetContext.getParameterMap().put("name", new String[]{"value"});

    // Blog
    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    List<BlogPost> blogPostList = new ArrayList<>();
    for (long i = 1; i < 11; i++) {
      BlogPost blogPost = new BlogPost();
      blogPost.setId(i);
      blogPost.setBlogId(blog.getId());
      blogPost.setUniqueId("blog-post-" + i);
      blogPost.setTitle("This is blog post " + i);
      blogPostList.add(blogPost);
    }

    // Execute the widget
    try (MockedStatic<LoadBlogCommand> loadBlogCommandMockedStatic = mockStatic(LoadBlogCommand.class)) {
      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        loadBlogCommandMockedStatic.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
        blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(blogPostList);
        BlogPostListWidget widget = new BlogPostListWidget();
        widgetContext = widget.execute(widgetContext);
      }
    }

    DataConstraints constraints = (DataConstraints) widgetContext.getRequest().getAttribute(RequestConstants.RECORD_PAGING);
    Assertions.assertEquals(10, constraints.getPageSize());

    List<BlogPost> blogPostListRequest = (List) widgetContext.getRequest().getAttribute("blogPostList");
    Assertions.assertEquals(10, blogPostListRequest.size());

    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }
}