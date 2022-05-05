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

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

public class GenerateBlogPostUniqueIdCommandTest {

  @Test
  void generateUniqueIdForNewBlogPost() {
    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), anyString())).thenReturn(null);

      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("SimIS CMS Has Been Updated With Tests");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(null, blogPost);
      Assertions.assertEquals("simis-cms-has-been-updated-with-tests", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForUpdatedBlogPost() {
    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), anyString())).thenReturn(null);

      BlogPost previousBlogPost = new BlogPost();
      previousBlogPost.setTitle("SimIS CMS Has Just Been Updated With Tests");
      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("SimIS CMS Has Been Updated With Tests");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(previousBlogPost, blogPost);
      Assertions.assertEquals("simis-cms-has-been-updated-with-tests", uniqueId);
    }
  }

  @Test
  void generateUniqueIdForDuplicateBlogPost() {
    String existingUniqueId = "monthly-update";
    BlogPost existingPost = new BlogPost();
    existingPost.setUniqueId(existingUniqueId);

    try (MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class)) {
      blogPostRepository.when(() -> BlogPostRepository.findByUniqueId(anyLong(), eq(existingUniqueId))).thenReturn(existingPost);

      BlogPost blogPost = new BlogPost();
      blogPost.setTitle("Monthly Update");
      String uniqueId = GenerateBlogPostUniqueIdCommand.generateUniqueId(null, blogPost);
      Assertions.assertEquals("monthly-update-2", uniqueId);
    }
  }

}