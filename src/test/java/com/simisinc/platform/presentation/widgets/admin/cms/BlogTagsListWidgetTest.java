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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.DeleteBlogTagCommand;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;

/**
 * Verifies {@link BlogTagsListWidget} (issue #633), mirroring the coverage
 * {@code CollectionTagsListWidgetTest} applies to the equivalent Items widget (issue #632).
 *
 * @author SimIS Inc.
 */
class BlogTagsListWidgetTest extends WidgetBase {

  @Test
  void executeReturnsAnErrorWhenTheBlogIsMissing() {
    addQueryParameter(widgetContext, "blogId", "5");
    try (MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class)) {
      loadBlogCommand.when(() -> LoadBlogCommand.loadBlogById(5L)).thenReturn(null);

      new BlogTagsListWidget().execute(widgetContext);

      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void executeLoadsTheBlogsTagList() {
    addQueryParameter(widgetContext, "blogId", "5");
    Blog blog = new Blog();
    blog.setId(5L);
    BlogTag tag = new BlogTag();
    tag.setId(1L);
    tag.setName("Fiction");

    try (MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class)) {
      loadBlogCommand.when(() -> LoadBlogCommand.loadBlogById(5L)).thenReturn(blog);
      tagRepository.when(() -> BlogTagRepository.findAllByBlogId(5L)).thenReturn(List.of(tag));

      new BlogTagsListWidget().execute(widgetContext);

      assertEquals(List.of(tag), widgetContext.getRequest().getAttribute("tagList"));
    }
  }

  @Test
  void deleteRemovesTheTagAndRedirectsToTheBlogTagsPage() {
    addQueryParameter(widgetContext, "tagId", "1");
    BlogTag tag = new BlogTag();
    tag.setId(1L);
    tag.setBlogId(5L);

    try (MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class);
        MockedStatic<DeleteBlogTagCommand> deleteTagCommand = mockStatic(DeleteBlogTagCommand.class)) {
      tagRepository.when(() -> BlogTagRepository.findById(1L)).thenReturn(tag);

      new BlogTagsListWidget().delete(widgetContext);

      deleteTagCommand.verify(() -> DeleteBlogTagCommand.deleteTag(tag));
      assertEquals("/admin/blog-tags?blogId=5", widgetContext.getRedirect());
    }
  }

  @Test
  void deleteDoesNothingWhenNoTagIdWasSupplied() {
    try (MockedStatic<DeleteBlogTagCommand> deleteTagCommand = mockStatic(DeleteBlogTagCommand.class)) {
      new BlogTagsListWidget().delete(widgetContext);

      deleteTagCommand.verifyNoInteractions();
      assertNull(widgetContext.getRedirect());
    }
  }
}
