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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.CustomField;

/**
 * Test custom fields
 *
 * @author matt rajkowski
 * @created 7/23/2022 8:00 AM
 */
public class CustomFieldListMergeCommandTest {
    @Test
    void testTwoLists() {

        Map<String, CustomField> mainList = new LinkedHashMap<>();
        CustomField originalField = new CustomField();
        originalField.setName("name");
        mainList.put("name", originalField);

        Map<String, CustomField> secondaryList = new LinkedHashMap<>();
        CustomField valueField = new CustomField();
        valueField.setName("name");
        valueField.setValue("Value");
        secondaryList.put("name", valueField);

        Map<String, CustomField> newList = CustomFieldListMergeCommand.mergeCustomFieldLists(mainList, secondaryList);

        assertTrue(!newList.isEmpty());
        assertTrue(newList.size() == 1);
        assertEquals("Value", newList.get("name").getValue());
    }

    @Test
    void testTwoListsWithDifferences() {

        Map<String, CustomField> mainList = new LinkedHashMap<>();
        CustomField originalField = new CustomField();
        originalField.setName("name");
        mainList.put(originalField.getName(), originalField);

        Map<String, CustomField> secondaryList = new LinkedHashMap<>();
        CustomField valueField = new CustomField();
        valueField.setName("name2");
        valueField.setValue("Value");
        secondaryList.put(valueField.getName(), valueField);

        Map<String, CustomField> mergedList = CustomFieldListMergeCommand.mergeCustomFieldLists(mainList, secondaryList);

        assertTrue(!mergedList.isEmpty());
        assertTrue(mergedList.size() == 2);
        assertEquals("Value", mergedList.get("name2").getValue());
    }

    @Test
    void testTwoListsWithOptions() {

        Map<String, String> listOfOptions = new LinkedHashMap<>();
        listOfOptions.put("option1", "Option 1");
        listOfOptions.put("option2", "Option 2");
        listOfOptions.put("option-3", "Option 3");

        Map<String, CustomField> mainList = new LinkedHashMap<>();
        CustomField originalField = new CustomField();
        originalField.setName("optionList");
        originalField.setType("list");
        originalField.setListOfOptions(listOfOptions);
        mainList.put(originalField.getName(), originalField);

        Map<String, CustomField> secondaryList = new LinkedHashMap<>();
        CustomField valueField = new CustomField();
        valueField.setName("optionList");
        valueField.setValue("Option 3");
        secondaryList.put(valueField.getName(), valueField);

        Map<String, CustomField> mergedList = CustomFieldListMergeCommand.mergeCustomFieldLists(mainList, secondaryList);

        assertTrue(!mergedList.isEmpty());
        assertTrue(mergedList.size() == 1);
        assertEquals("Option 3", mergedList.get("optionList").getValue());
    }

    @Test
    void testTwoListsWithAlternateList() {

        // The Template List
        Map<String, CustomField> mainList = new LinkedHashMap<>();
        CustomField originalField = new CustomField();
        originalField.setName("optionList");
        originalField.setType("list");

        Map<String, String> listOfOptions = new LinkedHashMap<>();
        listOfOptions.put("option1", "Option 1");
        listOfOptions.put("option2", "Option 2");
        listOfOptions.put("option-3", "Option 3");
        originalField.setListOfOptions(listOfOptions);
        mainList.put("selection", originalField);

        // The In-Use List
        Map<String, CustomField> secondaryList = new LinkedHashMap<>();
        CustomField valueField = new CustomField();
        valueField.setName("optionList");
        valueField.setValue("Option 4");

        Map<String, String> listOfOptions2 = new LinkedHashMap<>();
        listOfOptions2.put("option1", "Option 1");
        listOfOptions2.put("option2", "Option 2");
        listOfOptions2.put("option-3", "Option 3");
        listOfOptions2.put("option-5", "Option 5");
        valueField.setListOfOptions(listOfOptions2);
        secondaryList.put("selection", valueField);

        // Perform the function
        Map<String, CustomField> newList = CustomFieldListMergeCommand.mergeCustomFieldLists(mainList, secondaryList);

        assertTrue(!newList.isEmpty());
        assertTrue(newList.size() == 1);
        assertEquals("Option 4", newList.get("selection").getValue());

        // Compare the lists
        // Should have everything from list1, plus the value used in list2
        // {option1=Option 1, option2=Option 2, option-3=Option 3, Option 4=Option 4}
        System.out.println(newList.get("selection").getListOfOptions());
        // newList.get("selection").getListOfOptions()

        Map<String, String> finalListOfOptions = newList.get("selection").getListOfOptions();
        assertTrue(finalListOfOptions.containsKey("option1"));
        assertTrue(finalListOfOptions.containsKey("option2"));
        assertTrue(finalListOfOptions.containsKey("option-3"));
        assertTrue(finalListOfOptions.containsKey("option-4"));

    }
}
