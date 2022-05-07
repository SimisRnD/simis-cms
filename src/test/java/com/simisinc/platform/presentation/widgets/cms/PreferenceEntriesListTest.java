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

package com.simisinc.platform.presentation.widgets.cms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/4/2022 7:00 PM
 */
class PreferenceEntriesListTest {

  @Test
  void readEntries() {
    String data =
        "link|/login||name|Login||role|guest||rule|site.login|||" +
            "link|/login||name|Register||role|guest||rule|site.registrations|||" +
            "link|/my-page||name|My Profile||role|users|||" +
            "link|/logout||name|Log Out||role|users";
    PreferenceEntriesList entriesList = new PreferenceEntriesList(data);
    Assertions.assertEquals(4, entriesList.size());
  }
}