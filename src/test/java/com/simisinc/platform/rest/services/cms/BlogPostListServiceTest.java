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

package com.simisinc.platform.rest.services.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Verifies {@link BlogPostListService} (issue #412).
 *
 * @author SimIS Inc.
 */
class BlogPostListServiceTest {

  private ServiceContext contextFor(String blogUniqueId) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(blogUniqueId);
    context.setParameterMap(new HashMap<>());
    return context;
  }

  private ServiceContext contextFor(String blogUniqueId, String page, String size) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(blogUniqueId);
    HashMap<String, String[]> params = new HashMap<>();
    params.put("page", new String[] { page });
    params.put("size", new String[] { size });
    context.setParameterMap(params);
    return context;
  }

  private Blog enabledBlog(long id) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setEnabled(true);
    return blog;
  }

  @Test
  void getReturns404WhenTheBlogDoesNotExist() {
    ServiceContext context = contextFor("missing-blog");

    try (MockedStatic<BlogRepository> repo = mockStatic(BlogRepository.class)) {
      repo.when(() -> BlogRepository.findByUniqueId("missing-blog")).thenReturn(null);

      ServiceResponse response = new BlogPostListService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getReturns404WhenTheBlogIsDisabled() {
    ServiceContext context = contextFor("disabled-blog");
    Blog disabledBlog = enabledBlog(9L);
    disabledBlog.setEnabled(false);

    try (MockedStatic<BlogRepository> repo = mockStatic(BlogRepository.class)) {
      repo.when(() -> BlogRepository.findByUniqueId("disabled-blog")).thenReturn(disabledBlog);

      ServiceResponse response = new BlogPostListService().get(context);

      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void getOnlyRequestsPublishedPostsWithinTheDateWindow() {
    ServiceContext context = contextFor("news");
    Blog blog = enabledBlog(9L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findAll(any(BlogPostSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      new BlogPostListService().get(context);

      ArgumentCaptor<BlogPostSpecification> specCaptor = ArgumentCaptor.forClass(BlogPostSpecification.class);
      postRepo.verify(() -> BlogPostRepository.findAll(specCaptor.capture(), any(DataConstraints.class)));
      BlogPostSpecification spec = specCaptor.getValue();
      assertEquals(9L, spec.getBlogId());
      assertEquals(1, spec.getPublishedOnly());
      assertEquals(1, spec.getStartDateIsBeforeNow());
      assertEquals(1, spec.getIsWithinEndDate());
    }
  }

  @Test
  void getPassesTheRequestedPageAndSizeToDataConstraints() {
    ServiceContext context = contextFor("news", "2", "10");
    Blog blog = enabledBlog(9L);

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findAll(any(BlogPostSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      new BlogPostListService().get(context);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      postRepo.verify(() -> BlogPostRepository.findAll(any(BlogPostSpecification.class), constraintsCaptor.capture()));
      assertEquals(2, constraintsCaptor.getValue().getPageNumber());
      assertEquals(10, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void getReturns200WithMappedPosts() {
    ServiceContext context = contextFor("news");
    Blog blog = enabledBlog(9L);
    BlogPost post = new BlogPost();
    post.setUniqueId("hello-world");
    post.setTitle("Hello World");
    post.setSummary("A summary");

    try (MockedStatic<BlogRepository> blogRepo = mockStatic(BlogRepository.class);
        MockedStatic<BlogPostRepository> postRepo = mockStatic(BlogPostRepository.class)) {
      blogRepo.when(() -> BlogRepository.findByUniqueId("news")).thenReturn(blog);
      postRepo.when(() -> BlogPostRepository.findAll(any(BlogPostSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of(post));

      ServiceResponse response = new BlogPostListService().get(context);

      assertEquals(200, response.getStatus());
      @SuppressWarnings("unchecked")
      List<BlogPostResponse> data = (List<BlogPostResponse>) response.getData();
      assertEquals(1, data.size());
      assertEquals("hello-world", data.get(0).getUniqueId());
      assertEquals("Hello World", data.get(0).getTitle());
      assertEquals("A summary", data.get(0).getSummary());
    }
  }
}
