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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class GenerateBlogUniqueIdCommandTest {

  @Test
  void generateUniqueIdForNewBlog() {
    try (MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class)) {
      blogRepository.when(() -> BlogRepository.findByUniqueId(anyString())).thenReturn(null);

      Blog blog = new Blog();
      blog.setName("News Blog");
      String uniqueId = GenerateBlogUniqueIdCommand.generateUniqueId(null, blog);
      Assertions.assertEquals("news-blog", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForUpdatedBlog() {
    try (MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class)) {
      blogRepository.when(() -> BlogRepository.findByUniqueId(anyString())).thenReturn(null);

      Blog previousBlog = new Blog();
      previousBlog.setName("News Blog");
      Blog blog = new Blog();
      blog.setName("Tech Blog");
      String uniqueId = GenerateBlogUniqueIdCommand.generateUniqueId(previousBlog, blog);
      Assertions.assertEquals("tech-blog", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForDuplicateBlog() {
    String existingUniqueId = "news-blog";
    Blog existingBlog = new Blog();
    existingBlog.setUniqueId(existingUniqueId);

    try (MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class)) {
      blogRepository.when(() -> BlogRepository.findByUniqueId(eq(existingUniqueId))).thenReturn(existingBlog);

      Blog blog = new Blog();
      blog.setName("News Blog");
      String uniqueId = GenerateBlogUniqueIdCommand.generateUniqueId(null, blog);
      Assertions.assertEquals("news-blog-2", uniqueId);
    }
  }
}