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

package com.simisinc.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.meanbean.test.BeanVerifier;
import org.meanbean.util.ClassPathUtils;

import java.sql.Timestamp;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class PersistenceTest {

  @Test
  void testSpecification() {
    Class<?>[] beanClasses = ClassPathUtils.findClassesIn("com.simisinc.platform.infrastructure.persistence");
    for (Class k : beanClasses) {
      String thisClass = k.getName();
      if (!thisClass.endsWith("Specification")) {
        continue;
      }
      // Specifications have getters and settings
      BeanVerifier.forClass(k)
          .editSettings()
          .registerFactory(Timestamp.class, () -> new Timestamp(System.currentTimeMillis()))
          .edited()
          .verifyGettersAndSetters();
    }
  }
}