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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FormDataJSONCommandTests {

  @Test
  void createJSONString() {

    String nameValue = "My Name";
    String descriptionValue = "This is a text area";

    List<FormField> formFieldList = new ArrayList<>();
    {
      FormField formField = new FormField();
      formField.setName("Name");
      formField.setType("text");
      formField.setUserValue(nameValue);
      formFieldList.add(formField);
    }
    {
      FormField formField = new FormField();
      formField.setName("Description");
      formField.setType("textarea");
      formField.setUserValue(descriptionValue);
      formFieldList.add(formField);
    }

    FormData formData = new FormData();
    formData.setFormFieldList(formFieldList);

    String jsonValue = FormDataJSONCommand.createJSONString(formData);
    String expectedValue = "[{\"id\":1,\"label\":\"\",\"name\":\"Name\",\"type\":\"text\",\"value\":\"My Name\"},{\"id\":2,\"label\":\"\",\"name\":\"Description\",\"type\":\"textarea\",\"value\":\"This is a text area\"}]";
    Assertions.assertEquals(expectedValue, jsonValue);

  }

  @Test
  void populateFromJSONString() throws SQLException {

    String nameValue = "My Name";
    String descriptionValue = "This is a text area";
    String jsonValue = "[{\"id\":1,\"label\":\"\",\"name\":\"Name\",\"type\":\"text\",\"value\":\"" + nameValue + "\"},{\"id\":2,\"label\":\"\",\"name\":\"Description\",\"type\":\"textarea\",\"value\":\"" + descriptionValue + "\"}]";

    FormData formData = new FormData();
    FormDataJSONCommand.populateFromJSONString(formData, jsonValue);

    Assertions.assertEquals(2, formData.getFormFieldList().size());
    boolean foundName = false;
    boolean foundDescription = false;
    for (FormField formField : formData.getFormFieldList()) {
      if ("Name".equals(formField.getName())) {
        foundName = true;
        Assertions.assertEquals(nameValue, formField.getUserValue());
      } else if ("Description".equals(formField.getName())) {
        foundDescription = true;
        Assertions.assertEquals(descriptionValue, formField.getUserValue());
      }
    }
    Assertions.assertTrue(foundName);
    Assertions.assertTrue(foundDescription);
  }
}