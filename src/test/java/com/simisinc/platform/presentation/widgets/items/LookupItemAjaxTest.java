/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.items;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * The item-lookup autocomplete returns names/cities as JSON that the client concatenates straight
 * into markup (renderItem). JSON-encoding alone is not HTML-safe, so a crafted item name could break
 * out of the suggestion attribute and inject markup (stored DOM XSS). This verifies the endpoint
 * HTML-encodes the values so the payload is inert by the time it reaches the DOM.
 *
 * @author Elizabeth Houser
 */
class LookupItemAjaxTest extends WidgetBase {

  @Test
  void craftedItemNameIsHtmlEncodedAgainstDomXss() {
    addQueryParameter(widgetContext, "q", "report");
    addQueryParameter(widgetContext, "cid", "5");
    addQueryParameter(widgetContext, "iid", "-1");

    Item item = new Item();
    item.setId(2L);
    item.setName("Report\"><img src=x onerror=alert(1)>");
    item.setUniqueId("report-1");
    item.setCity("");
    List<Item> results = new ArrayList<>();
    results.add(item);

    try (MockedStatic<LoadCollectionCommand> collections = mockStatic(LoadCollectionCommand.class);
        MockedStatic<ItemRepository> items = mockStatic(ItemRepository.class)) {
      collections.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(new Collection());
      items.when(() -> ItemRepository.findAll(any(), any())).thenReturn(results);

      new LookupItemAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertNotNull(json);
      // The payload must be neutralized: no raw tag, no attribute breakout.
      Assertions.assertFalse(json.contains("<img"), "raw markup must not appear in the JSON: " + json);
      Assertions.assertFalse(json.contains("\"><"), "attribute breakout must not survive: " + json);
      // ...delivered as inert, HTML-encoded text instead.
      Assertions.assertTrue(json.contains("&lt;img"), "the item name must be HTML-encoded: " + json);
    }
  }
}
