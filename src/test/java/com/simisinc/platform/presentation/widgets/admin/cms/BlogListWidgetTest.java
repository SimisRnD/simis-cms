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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class BlogListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"blogList\">\n" +
            "  <title>Blogs</title>\n" +
            "</widget>");

    List<Blog> blogList = new ArrayList<>();
    Blog blog = new Blog();
    blog.setId(1L);
    blog.setUniqueId("blog");
    blogList.add(blog);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(BlogRepository::findAll).thenReturn(blogList);

      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findCount(any())).thenReturn(8L);

        // Execute the widget
        BlogListWidget widget = new BlogListWidget();
        widget.execute(widgetContext);
      }
    }

    // Verify the request
    Assertions.assertEquals(BlogListWidget.JSP, widgetContext.getJsp());

    List<Blog> blogListRequest = (List) request.getAttribute("blogList");
    Assertions.assertEquals(blogList.size(), blogListRequest.size());

    Map<Long, Long> blogPostCount = (Map) request.getAttribute("blogPostCount");
    Assertions.assertEquals(8L, blogPostCount.get(blog.getId()));
  }

  @Test
  void deleteError() {
    // Set query parameters
    addQueryParameter(widgetContext, "id", "1");

    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"blogList\">\n" +
            "  <title>Blogs</title>\n" +
            "</widget>");

    Blog blog = new Blog();
    blog.setId(1L);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(() -> BlogRepository.findById(blog.getId())).thenReturn(blog);

      // Execute the widget
      BlogListWidget widget = new BlogListWidget();
      WidgetContext result = widget.delete(widgetContext);

      // Verify without Admin role
      Assertions.assertNotNull(widgetContext.getWarningMessage());
      Assertions.assertNotNull(result);
    }
  }

  @Test
  void deleteSuccess() {
    // Set query parameters
    addQueryParameter(widgetContext, "id", "1");

    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"blogList\">\n" +
            "  <title>Blogs</title>\n" +
            "</widget>");

    Blog blog = new Blog();
    blog.setId(1L);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(() -> BlogRepository.findById(blog.getId())).thenReturn(blog);
      blogRepositoryMockedStatic.when(() -> BlogRepository.remove(blog)).thenReturn(true);

      // Run as Admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      BlogListWidget widget = new BlogListWidget();
      WidgetContext result = widget.delete(widgetContext);

      // Verify
      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getWarningMessage());
      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNotNull(widgetContext.getSuccessMessage());
    }
  }
}