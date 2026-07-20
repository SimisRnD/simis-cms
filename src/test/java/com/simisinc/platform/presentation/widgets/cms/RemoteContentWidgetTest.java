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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the remote-image wrapper sanitizes its url (a widget preference) before placing it
 * into the src attribute, so a value with an embedded quote or an active scheme cannot break out of
 * the attribute and inject markup (stored XSS).
 *
 * @author Elizabeth Houser
 * @created 2026-07-19
 */
class RemoteContentWidgetTest {

  @Test
  void safeImageUrlProducesImgTag() {
    Assertions.assertEquals("<img src=\"https://cdn.example.com/photo.jpg\" />",
        RemoteContentWidget.buildImageTag("https://cdn.example.com/photo.jpg"));
  }

  @Test
  void quoteBreakoutUrlIsRejected() {
    // Ends with .jpg (so it reaches the image branch) but embeds a quote + event handler
    Assertions.assertNull(RemoteContentWidget.buildImageTag("https://x/\"onerror=\"alert(1)\".jpg"));
  }

  @Test
  void activeSchemeUrlIsRejected() {
    Assertions.assertNull(RemoteContentWidget.buildImageTag("javascript:alert(1)//.jpg"));
  }

  @Test
  void nullUrlIsRejected() {
    Assertions.assertNull(RemoteContentWidget.buildImageTag(null));
  }
}
