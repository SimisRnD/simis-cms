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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;

/**
 * Covers /admin/blog-posts' {@code execute()} building {@code blogMap} (a blog_id -&gt; Blog
 * lookup), added alongside the existing {@code blogList} so blog-post-list.jsp can resolve each
 * post row's blog in O(1) instead of re-looping the entire blogList once per post row -- an
 * O(posts x blogs) pattern in the JSP, bounded by page size but cheap to fix while pagination work
 * was already touching this same admin/cms area (the /admin/blogs guidance pass).
 *
 * @author SimIS Inc.
 */
class AdminBlogPostListWidgetTest extends WidgetBase {

  @Test
  void executeBuildsABlogIdToBlogMapForTheJspToUse() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminBlogPostList\"/>");

    Blog blogA = new Blog();
    blogA.setId(5L);
    blogA.setName("Press Releases");
    Blog blogB = new Blog();
    blogB.setId(9L);
    blogB.setName("Engineering Blog");
    List<Blog> blogList = new ArrayList<>();
    blogList.add(blogA);
    blogList.add(blogB);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(BlogRepository::findAll).thenReturn(blogList);

      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any(DataConstraints.class)))
            .thenReturn(new ArrayList<BlogPost>());

        AdminBlogPostListWidget widget = new AdminBlogPostListWidget();
        widget.execute(widgetContext);
      }
    }

    @SuppressWarnings("unchecked")
    Map<Long, Blog> blogMap = (Map<Long, Blog>) request.getAttribute("blogMap");
    Assertions.assertNotNull(blogMap);
    Assertions.assertEquals(2, blogMap.size());
    Assertions.assertEquals(blogA, blogMap.get(5L));
    Assertions.assertEquals(blogB, blogMap.get(9L));
  }

  @Test
  void executeSetsAPostOrderTheRepositoryCannotOverwrite() {
    // Issue 1604. This list asked for "start_date DESC NULLS LAST, post_id DESC" through
    // setDefaultColumnToSortBy, which belongs to the repository -- BlogPostRepository#findAll
    // writes "post_id" over it one line after receiving these constraints. So the screen an editor
    // uses to find recent work has been ordered by insertion id, oldest first, the whole time.
    //
    // Asserted on columnsToSortBy, not defaultColumnToSortBy: the old spelling would satisfy a
    // test written against the setter that was called, while the SQL kept its own order. That is
    // exactly how this class of bug survived in FeedServletTest.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminBlogPostList\"/>");
    ArgumentCaptor<DataConstraints> captor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<BlogRepository> blogRepositoryMockedStatic = mockStatic(BlogRepository.class)) {
      blogRepositoryMockedStatic.when(BlogRepository::findAll).thenReturn(new ArrayList<Blog>());

      try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
        blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findAll(any(), any(DataConstraints.class)))
            .thenReturn(new ArrayList<BlogPost>());

        new AdminBlogPostListWidget().execute(widgetContext);

        blogPostRepositoryMockedStatic.verify(() -> BlogPostRepository.findAll(any(), captor.capture()));
      }
    }

    DataConstraints constraints = captor.getValue();
    Assertions.assertNotNull(constraints.getColumnsToSortBy(),
        "the admin blog post list must set the application-facing sort, which the repository cannot overwrite");
    String sort = String.join(", ", constraints.getColumnsToSortBy());
    Assertions.assertTrue(sort.contains("start_date"), "the list must order by date, got: " + sort);
    Assertions.assertTrue(sort.toUpperCase().contains("DESC"), "newest first, got: " + sort);

    // Exactly what findAll does next. The order has to survive it.
    constraints.setDefaultColumnToSortBy("post_id");
    Assertions.assertEquals(sort, String.join(", ", constraints.getColumnsToSortBy()),
        "the repository's own default must not displace the list's order");
  }

}
