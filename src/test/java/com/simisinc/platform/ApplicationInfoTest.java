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

package com.simisinc.platform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.simisinc.platform.ApplicationInfo.VERSION;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class ApplicationInfoTest {

  @Test
  void version() {
    // "20220426.10000"
    Assertions.assertNotNull(VERSION);
    String version = VERSION;
    int dotIdx = version.indexOf(".");
    Assertions.assertEquals(8, dotIdx);

    int datePart = Integer.parseInt(version.substring(0, dotIdx));
    int buildPart = Integer.parseInt(version.substring(dotIdx + 1));

    Assertions.assertTrue(datePart >= 20220426);
    Assertions.assertTrue(buildPart >= 10000);
  }
}