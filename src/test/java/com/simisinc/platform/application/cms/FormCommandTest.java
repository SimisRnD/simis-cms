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

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests FormCommand functions
 *
 * @author matt rajkowski
 * @created 3/22/22 5:10 PM
 */
public class FormCommandTest {

  @Test
  void testSpamPositive() {

    List<String> spamList = new ArrayList<>();
    spamList.add("100% off");
    FormCommand.setList(FormCommand.SPAM_LIST, spamList);

    List<FormField> formFieldList = new ArrayList<>();
    FormField formField = new FormField();
    formField.setName("Name");
    formField.setType("textarea");
    formField.setUserValue("Get 100% off today");
    formFieldList.add(formField);

    FormData formData = new FormData();
    formData.setFormFieldList(formFieldList);

    assertTrue(FormCommand.checkNotificationRules(formData));
    assertTrue(formData.getFlaggedAsSpam());
  }

  @Test
  void testSpamNegative() {

    List<String> spamList = new ArrayList<>();
    spamList.add("100% off");
    FormCommand.setList(FormCommand.SPAM_LIST, spamList);

    List<FormField> formFieldList = new ArrayList<>();
    FormField formField = new FormField();
    formField.setName("Name");
    formField.setType("textarea");
    formField.setUserValue("Get 90% off today");
    formFieldList.add(formField);

    FormData formData = new FormData();
    formData.setFormFieldList(formFieldList);

    assertFalse(FormCommand.checkNotificationRules(formData));
    assertFalse(formData.getFlaggedAsSpam());
  }

}
