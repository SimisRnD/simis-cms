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

package com.simisinc.platform.infrastructure.workflow;

import com.simisinc.platform.domain.events.cms.BlogPostPublishedEvent;
import com.simisinc.platform.domain.model.cms.BlogPost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests for domain event processing
 *
 * @author matt rajkowski
 * @created 4/9/2021 4:36 PM
 */
class WorkflowEngineTest {

  @Test
  void domainEventTest() throws Exception {
    BlogPost blogPost = new BlogPost();
    blogPost.setStartDate(null);

    BlogPostPublishedEvent blogPostPublishedEvent = new BlogPostPublishedEvent(blogPost.getId());
    Assertions.assertEquals(blogPostPublishedEvent.getDomainEventType(), BlogPostPublishedEvent.ID);
    Assertions.assertTrue(blogPostPublishedEvent.getOccurred() > -1);

    TimeUnit.MILLISECONDS.sleep(1);

    BlogPostPublishedEvent blogPostPublishedEvent2 = new BlogPostPublishedEvent(blogPost.getId());
    Assertions.assertNotEquals(blogPostPublishedEvent.getOccurred(), blogPostPublishedEvent2.getOccurred());
    Assertions.assertTrue(blogPostPublishedEvent.getOccurred() < blogPostPublishedEvent2.getOccurred());
  }
}
