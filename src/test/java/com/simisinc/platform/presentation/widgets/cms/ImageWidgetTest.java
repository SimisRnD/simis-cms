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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.WidgetBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author elizabeth houser
 * @created 7/31/2026
 */
class ImageWidgetTest extends WidgetBase {

  @Test
  void execute() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"image\">\n" +
            "  <imageUrl>/assets/media/photo.png</imageUrl>\n" +
            "  <altText>A description of the photo</altText>\n" +
            "</widget>");

    ImageWidget widget = new ImageWidget();
    widget.execute(widgetContext);

    Assertions.assertEquals("/assets/media/photo.png", request.getAttribute("imageUrl"));
    Assertions.assertEquals("A description of the photo", request.getAttribute("altText"));
    Assertions.assertEquals(ImageWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeWithNoImageSetRendersPlaceholderAttributes() {
    // No preferences at all -- the freshly-added-via-"+Widget" case. The widget must still resolve
    // to its JSP (which renders a placeholder), never leave imageUrl pointing at a broken <img>.
    ImageWidget widget = new ImageWidget();
    widget.execute(widgetContext);

    Assertions.assertNull(request.getAttribute("imageUrl"));
    Assertions.assertEquals("", request.getAttribute("altText"));
    Assertions.assertEquals(ImageWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeDropsUnsafeImageUrlRatherThanRenderingIt() {
    // Defense in depth: MutateLayoutCommand's save-path validation (isValidImageUrl) should
    // already have rejected this, but a value that reaches render some other way must still never
    // be written into the src attribute.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"image\">\n" +
            "  <imageUrl>javascript:alert(1)</imageUrl>\n" +
            "</widget>");

    ImageWidget widget = new ImageWidget();
    widget.execute(widgetContext);

    Assertions.assertNull(request.getAttribute("imageUrl"));
    Assertions.assertEquals(ImageWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void isValidImageUrlAcceptsBlankAndSiteRelativePaths() {
    Assertions.assertTrue(ImageWidget.isValidImageUrl(null));
    Assertions.assertTrue(ImageWidget.isValidImageUrl(""));
    Assertions.assertTrue(ImageWidget.isValidImageUrl("/assets/media/photo.png"));
    Assertions.assertTrue(ImageWidget.isValidImageUrl("https://example.com/photo.png"));
  }

  @Test
  void isValidImageUrlRejectsActiveSchemesAndAttributeBreakout() {
    Assertions.assertFalse(ImageWidget.isValidImageUrl("javascript:alert(1)"));
    Assertions.assertFalse(ImageWidget.isValidImageUrl("data:text/html,<script>alert(1)</script>"));
    Assertions.assertFalse(ImageWidget.isValidImageUrl("/assets/x.png\" onerror=\"alert(1)"));
  }
}
