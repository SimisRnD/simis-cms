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
        // The item's legacy value is retained on the merged field...
        assertEquals("Option 4", newList.get("selection").getValue());

        // ...but a value with no matching option must NOT be silently added to the field's
        // defined list of options (that used to happen, and since the merged field is the same
        // object as the live/cached collection definition, it permanently polluted the shared
        // option list for every future request). The main list's original 3 options are the
        // only options that should remain.
        Map<String, String> finalListOfOptions = newList.get("selection").getListOfOptions();
        assertTrue(finalListOfOptions.containsKey("option1"));
        assertTrue(finalListOfOptions.containsKey("option2"));
        assertTrue(finalListOfOptions.containsKey("option-3"));
        assertEquals(3, finalListOfOptions.size());
        assertTrue(!finalListOfOptions.containsKey("option-4"));
        assertTrue(!finalListOfOptions.containsValue("Option 4"));
    }

    @Test
    void testFieldTypeChangeDoesNotInjectSyntheticOptionForLegacyValue() {

        // The collection's current field definition, after an admin changed the field's type
        // from "text" to "list" and defined a fixed set of options
        Map<String, CustomField> mainList = new LinkedHashMap<>();
        CustomField currentFieldDefinition = new CustomField();
        currentFieldDefinition.setName("status");
        currentFieldDefinition.setType("list");
        Map<String, String> definedOptions = new LinkedHashMap<>();
        definedOptions.put("active", "Active");
        definedOptions.put("inactive", "Inactive");
        currentFieldDefinition.setListOfOptions(definedOptions);
        mainList.put("status", currentFieldDefinition);

        // An existing item that was saved back when "status" was a free-text field, so its
        // stored value doesn't match either of the newly-defined options
        Map<String, CustomField> secondaryList = new LinkedHashMap<>();
        CustomField itemLegacyField = new CustomField();
        itemLegacyField.setName("status");
        itemLegacyField.setValue("Needs Review");
        secondaryList.put("status", itemLegacyField);

        // Merge repeatedly, as would happen across separate requests against the same cached
        // collection field definition, to confirm the option list never grows
        Map<String, CustomField> mergedList = null;
        for (int i = 0; i < 3; i++) {
            mergedList = CustomFieldListMergeCommand.mergeCustomFieldLists(mainList, secondaryList);
        }

        assertTrue(!mergedList.isEmpty());
        // The item's legacy value is preserved as-is on the merged field
        assertEquals("Needs Review", mergedList.get("status").getValue());

        // The field's defined options are untouched: still exactly the two options the admin
        // configured, with no synthetic option ever injected for the unmatched legacy value
        Map<String, String> finalOptions = mergedList.get("status").getListOfOptions();
        assertEquals(2, finalOptions.size());
        assertTrue(finalOptions.containsKey("active"));
        assertTrue(finalOptions.containsKey("inactive"));
        assertTrue(!finalOptions.containsValue("Needs Review"));

        // The original (shared/cached) field definition's option map itself was never mutated
        assertEquals(2, definedOptions.size());
    }
}
