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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.SaveBlogTagCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;

/**
 * Verifies {@link BlogTagFormWidget} (issue #633), mirroring the coverage
 * {@code CollectionTagFormWidgetTest} applies to the equivalent Items widget (issue #632).
 *
 * @author SimIS Inc.
 */
class BlogTagFormWidgetTest extends WidgetBase {

  @Test
  void postSavesANewTagAndRedirectsToTheTagList() throws Exception {
    addQueryParameter(widgetContext, "name", "Fiction");
    addQueryParameter(widgetContext, "blogId", "5");

    try (MockedStatic<SaveBlogTagCommand> saveTagCommand = mockStatic(SaveBlogTagCommand.class)) {
      BlogTag saved = new BlogTag();
      saved.setId(1L);
      saved.setBlogId(5L);
      saveTagCommand.when(() -> SaveBlogTagCommand.saveTag(any())).thenReturn(saved);

      new BlogTagFormWidget().post(widgetContext);

      assertEquals("/admin/blog-tags?blogId=5", widgetContext.getRedirect());
      assertNotNull(widgetContext.getSuccessMessage());
    }
  }

  @Test
  void postOnADuplicateNameRedirectsBackToTheExistingTagWithAWarning() throws Exception {
    addQueryParameter(widgetContext, "id", "1");
    addQueryParameter(widgetContext, "name", "Fiction");
    addQueryParameter(widgetContext, "blogId", "5");

    try (MockedStatic<SaveBlogTagCommand> saveTagCommand = mockStatic(SaveBlogTagCommand.class)) {
      saveTagCommand.when(() -> SaveBlogTagCommand.saveTag(any()))
          .thenThrow(new DataException("A unique name is required"));

      new BlogTagFormWidget().post(widgetContext);

      assertEquals("/admin/blog-tag?tagId=1", widgetContext.getRedirect());
      assertNotNull(widgetContext.getWarningMessage());
    }
  }

  @Test
  void executeReturnsAnErrorWhenTheBlogIsMissing() {
    addQueryParameter(widgetContext, "blogId", "5");
    try (MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class)) {
      loadBlogCommand.when(() -> LoadBlogCommand.loadBlogById(5L)).thenReturn(null);

      new BlogTagFormWidget().execute(widgetContext);

      assertNotNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void executeLoadsAnExistingTagByTagIdParameter() {
    addQueryParameter(widgetContext, "tagId", "1");
    BlogTag tag = new BlogTag();
    tag.setId(1L);
    tag.setBlogId(5L);
    tag.setName("Fiction");
    Blog blog = new Blog();
    blog.setId(5L);

    try (MockedStatic<BlogTagRepository> tagRepository = mockStatic(BlogTagRepository.class);
        MockedStatic<LoadBlogCommand> loadBlogCommand = mockStatic(LoadBlogCommand.class)) {
      tagRepository.when(() -> BlogTagRepository.findById(1L)).thenReturn(tag);
      loadBlogCommand.when(() -> LoadBlogCommand.loadBlogById(5L)).thenReturn(blog);

      new BlogTagFormWidget().execute(widgetContext);

      assertEquals(tag, widgetContext.getRequest().getAttribute("tag"));
    }
  }
}
