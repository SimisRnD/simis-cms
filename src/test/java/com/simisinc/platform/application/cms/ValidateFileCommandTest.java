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

import java.io.File;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class ValidateFileCommandTest {

  @Test
  void checkFile() {

  }

  @Test
  void testCheckFile() {

  }

  @Test
  void getMimeType() {
    File file = new File("missing-file");
    Assertions.assertNull(ValidateFileCommand.getMimeType(file, null));
    Assertions.assertEquals("application/msword", ValidateFileCommand.getMimeType(file, "doc"));
  }

  @Test
  void getFileType() {
    Assertions.assertEquals("PDF", ValidateFileCommand.getFileType("application/pdf", null));
  }
}