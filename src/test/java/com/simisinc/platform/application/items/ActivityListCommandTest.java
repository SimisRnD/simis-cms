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

package com.simisinc.platform.application.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.domain.model.items.Item;

/**
 * Verifies that a content-authored item name is html-escaped when it is expanded into an activity
 * message. The ${item:id} variable resolves to the item's name inside an anchor, and the resulting
 * markup is stored on the activity and rendered raw in the feed, so an unescaped name would be stored
 * XSS. This mirrors the escaping already applied to the sibling user-name branch.
 *
 * @author Elizabeth Houser
 * @created 2026-07-19
 */
class ActivityListCommandTest {

  @Test
  void itemNameIsHtmlEscapedInActivityMessage() {
    Item item = new Item();
    item.setName("<script>alert(1)</script>");

    Activity activity = new Activity();
    activity.setMessageText("posted ${item:5}");

    List<Activity> activityList = new ArrayList<>();
    activityList.add(activity);

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemById(5L)).thenReturn(item);
      ActivityListCommand.createMessageHtml(activityList);
    }

    // The activity is kept (it resolved to a known object, not "<unknown>")
    assertEquals(1, activityList.size());

    String html = activity.getMessageHtml();
    assertNotNull(html);
    // The hostile name is entity-encoded, so no live script tag reaches the feed
    assertFalse(html.contains("<script>"), "raw script tag must not appear: " + html);
    assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "item name must be html-escaped: " + html);
    // The item link itself is still built
    assertTrue(html.contains("<a href=\"#\">"), "the item link should still be present: " + html);
  }
}
