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

package com.simisinc.platform.domain.model;

import org.junit.jupiter.api.Test;
import org.meanbean.test.BeanVerifier;
import org.meanbean.util.ClassPathUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class DomainModelTest {
  @Test
  void testSettersAndGetters() {

    // Verify complex classes separately
    List<String> complexClasses = new ArrayList<>();

    // ProductSku setter enforces changes for BeanUtils usage
    complexClasses.add("com.simisinc.platform.domain.model.ecommerce.ProductSku");

    // WebhookSubscription.eventTypes/eventTypeList are two views of the same backing field
    // (a CSV string and its parsed List<String>), so setting one is expected to change the other
    complexClasses.add("com.simisinc.platform.domain.model.webhooks.WebhookSubscription");

    // Immutable value objects (all-final fields, no setters, no no-arg constructor) for the
    // issue #455 integration registry -- same shape as WebhookEventTypeCommand.WebhookEventType,
    // deliberately not a mutable JavaBean, so MeanBean cannot reflectively instantiate/verify them.
    complexClasses.add("com.simisinc.platform.domain.model.integrations.IntegrationDefinition");
    complexClasses.add("com.simisinc.platform.domain.model.integrations.CredentialField");

    // Computed rather than persisted, and immutable for the same reason as the two above: a
    // holiday's name and dates are derived together from the statute, so there is no state a
    // setter could meaningfully change.
    complexClasses.add("com.simisinc.platform.domain.model.FederalHoliday");

    // Test getters and setters for the domain model
    Class<?>[] beanClasses = ClassPathUtils.findClassesIn("com.simisinc.platform.domain.model");
    for (Class<?> k : beanClasses) {
      String thisClass = k.getName();
      if (complexClasses.contains(thisClass)) {
        continue;
      }
      BeanVerifier.forClass(k)
          .editSettings()
          .registerFactory(Timestamp.class, () -> new Timestamp(System.currentTimeMillis()))
          .edited()
          .verifyGettersAndSetters();
    }
  }
}