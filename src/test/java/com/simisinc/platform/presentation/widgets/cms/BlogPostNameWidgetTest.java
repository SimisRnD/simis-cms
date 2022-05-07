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
import com.simisinc.platform.application.cms.LoadBlogPostCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Timestamp;

import static com.simisinc.platform.presentation.widgets.cms.BlogPostNameWidget.JSP;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BlogPostNameWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("blogUniqueId", "news");

    // Widgets can use the URI
    when(request.getRequestURI()).thenReturn("news/this-is-the-news");

    // Widgets can have parameters
    //widgetContext.getParameterMap().put("name", new String[]{"value"});

    // Blog
    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("news");
    blog.setName("News");
    blog.setEnabled(true);

    // Blog Post
    BlogPost blogPost = new BlogPost();
    blogPost.setId(2L);
    blogPost.setBlogId(blog.getId());
    blogPost.setUniqueId("this-is-the-news");
    blogPost.setTitle("This is the news");
    blogPost.setPublished(new Timestamp(System.currentTimeMillis()));

    // Execute the widget
    try (MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class)) {
      try (MockedStatic<LoadBlogPostCommand> loadBlogPostCommand = mockStatic(LoadBlogPostCommand.class)) {
        loadBlogCommand.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq(blog.getUniqueId()))).thenReturn(blog);
        loadBlogPostCommand.when(() -> LoadBlogPostCommand.loadBlogPostByUniqueId(eq(blog.getId()), eq(blogPost.getUniqueId()))).thenReturn(blogPost);
        BlogPostNameWidget widget = new BlogPostNameWidget();
        widgetContext = widget.execute(widgetContext);
      }
    }

    Blog blogRequest = (Blog) widgetContext.getRequest().getAttribute("blog");
    Assertions.assertEquals(1L, blogRequest.getId());

    BlogPost blogPostRequest = (BlogPost) widgetContext.getRequest().getAttribute("blogPost");
    Assertions.assertEquals(2L, blogPostRequest.getId());
    Assertions.assertNotNull(blogPostRequest.getTitle());

    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }
}