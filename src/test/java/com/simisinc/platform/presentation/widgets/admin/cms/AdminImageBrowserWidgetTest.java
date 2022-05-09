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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class AdminImageBrowserWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");

    List<Image> imageList = new ArrayList<>();
    Image image = new Image();
    image.setId(1L);
    imageList.add(image);

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      imageRepositoryMockedStatic.when(ImageRepository::findAll).thenReturn(imageList);

      // Execute the widget
      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(AdminImageBrowserWidget.JSP, widgetContext.getJsp());
    Assertions.assertNull(request.getAttribute("title"));
    List<Image> imageListRequest = (List) request.getAttribute("imageList");
    Assertions.assertEquals(image.getId(), imageListRequest.get(0).getId());
  }
}