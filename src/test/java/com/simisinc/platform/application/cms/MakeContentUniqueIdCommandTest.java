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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class MakeContentUniqueIdCommandTest {

  @Test
  void parseToValidValue() {
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test"));
    Assertions.assertEquals("this-is-a-test-example", MakeContentUniqueIdCommand.parseToValidValue("This is a test/example"));
    Assertions.assertEquals("test-and-example", MakeContentUniqueIdCommand.parseToValidValue("Test & Example"));
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test?"));
    Assertions.assertEquals("this-is-a-test-1", MakeContentUniqueIdCommand.parseToValidValue("this-is-a-test-1"));
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test-"));
  }
}