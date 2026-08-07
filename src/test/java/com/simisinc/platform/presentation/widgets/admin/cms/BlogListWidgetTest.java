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
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

    Map<Long, Long> blogPostCountMap = new HashMap<>();
    blogPostCountMap.put(1L, 8L);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(() -> BlogRepository.findAll(any(), any())).thenReturn(blogList);

      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        blogPostRepositoryMockedStatic.when(BlogPostRepository::countGroupedByBlogId).thenReturn(blogPostCountMap);

        // Execute the widget
        BlogListWidget widget = new BlogListWidget();
        widget.execute(widgetContext);

        // Proves the N+1 per-row query was replaced with a single batched call -- not just that the
        // new batched method exists alongside the old one still being used
        blogPostRepositoryMockedStatic.verify(() -> BlogPostRepository.findCount(any()), never());
        blogPostRepositoryMockedStatic.verify(BlogPostRepository::countGroupedByBlogId, times(1));
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
  void executeHonorsPageAndItemsParameters() {
    // Set query parameters
    addQueryParameter(widgetContext, "page", "3");
    addQueryParameter(widgetContext, "items", "10");

    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"blogList\">\n" +
            "  <title>Blogs</title>\n" +
            "</widget>");

    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(() -> BlogRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(new ArrayList<>());

      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        blogPostRepositoryMockedStatic.when(BlogPostRepository::countGroupedByBlogId).thenReturn(new HashMap<>());

        // Execute the widget
        BlogListWidget widget = new BlogListWidget();
        widget.execute(widgetContext);
      }

      // Verify the page/items parameters reached the DataConstraints passed to the repository
      blogRepositoryMockedStatic.verify(() -> BlogRepository.findAll(any(), constraintsCaptor.capture()));
    }

    DataConstraints usedConstraints = constraintsCaptor.getValue();
    Assertions.assertEquals(3, usedConstraints.getPageNumber());
    Assertions.assertEquals(10, usedConstraints.getPageSize());

    // Verify the same DataConstraints instance was also published for paging_control.jspf
    DataConstraints recordPaging = (DataConstraints) request.getAttribute(RequestConstants.RECORD_PAGING);
    Assertions.assertNotNull(recordPaging);
    Assertions.assertEquals(3, recordPaging.getPageNumber());
    Assertions.assertEquals(10, recordPaging.getPageSize());
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