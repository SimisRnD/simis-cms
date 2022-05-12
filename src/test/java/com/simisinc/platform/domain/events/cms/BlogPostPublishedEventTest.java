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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class BlogPostPublishedEventTest {

  @Test
  void checkEvent() {
    BlogPost blogPost = new BlogPost();
    blogPost.setId(1L);

    try (MockedStatic<BlogPostRepository> blogPostRepositoryMockedStatic = mockStatic(BlogPostRepository.class)) {
      blogPostRepositoryMockedStatic.when(() -> BlogPostRepository.findById(anyLong())).thenReturn(blogPost);

      BlogPostPublishedEvent event = new BlogPostPublishedEvent(blogPost.getId());
      Assertions.assertEquals(blogPost.getId(), event.getBlogPostId());
      Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
      Assertions.assertEquals(BlogPostPublishedEvent.ID, event.getDomainEventType());
    }
  }
}