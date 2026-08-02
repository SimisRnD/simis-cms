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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadBlogCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;

/**
 * Verifies the optional blogUniqueId preference added for issue #633: when set, results are
 * scoped to that blog; when unset (the pre-existing behavior), the search stays site-wide.
 *
 * @author elizabeth houser
 */
class BlogPostSearchResultsWidgetTest extends WidgetBase {

  @Test
  void aConfiguredBlogUniqueIdScopesTheSearchToThatBlog() {
    preferences.put("blogUniqueId", "news");
    addQueryParameter(widgetContext, "query", "widget");

    Blog blog = new Blog();
    blog.setId(5L);
    blog.setUniqueId("news");

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("news"))).thenReturn(blog);
      repository.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new BlogPostSearchResultsWidget().execute(widgetContext);

      ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
      repository.verify(() -> BlogPostRepository.findAll(specCaptor.capture(), any()));
      assertEquals(5L, specCaptor.getValue().getBlogId());
    }
  }

  @Test
  void noBlogUniqueIdPreferenceSearchesAllBlogs() {
    addQueryParameter(widgetContext, "query", "widget");

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      repository.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new BlogPostSearchResultsWidget().execute(widgetContext);

      ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
      repository.verify(() -> BlogPostRepository.findAll(specCaptor.capture(), any()));
      assertEquals(-1L, specCaptor.getValue().getBlogId(), "no blog preference set -- results should not be scoped");
      loadBlog.verifyNoInteractions();
    }
  }

  @Test
  void anUnrecognizedBlogUniqueIdIsIgnoredRatherThanErroring() {
    preferences.put("blogUniqueId", "does-not-exist");
    addQueryParameter(widgetContext, "query", "widget");

    List<BlogPost> results = new ArrayList<>();

    try (MockedStatic<LoadBlogCommand> loadBlog = mockStatic(LoadBlogCommand.class);
        MockedStatic<BlogPostRepository> repository = mockStatic(BlogPostRepository.class);
        MockedStatic<SearchAnalyticsCommand> analytics = mockStatic(SearchAnalyticsCommand.class)) {
      loadBlog.when(() -> LoadBlogCommand.loadBlogByUniqueId(eq("does-not-exist"))).thenReturn(null);
      repository.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(results);

      new BlogPostSearchResultsWidget().execute(widgetContext);

      ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
      repository.verify(() -> BlogPostRepository.findAll(specCaptor.capture(), any()));
      assertEquals(-1L, specCaptor.getValue().getBlogId());
    }
  }
}
