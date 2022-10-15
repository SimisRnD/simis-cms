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