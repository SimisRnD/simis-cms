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

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/3/18 4:05 PM
 */
public class ImageBrowserWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(ImageBrowserWidget.class);

  private static String JSP = "/cms/image-browser.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    List<Image> imageList = ImageRepository.findAll();
    context.getRequest().setAttribute("imageList", imageList);

    // Batch-fetch existing image variants for every listed image in one query (issue #411 PR2)
    List<Long> browserImageIds = new ArrayList<>();
    if (imageList != null) {
      for (Image listedImage : imageList) {
        browserImageIds.add(listedImage.getId());
      }
    }
    Map<Long, List<ImageVariant>> imageVariantsByImageId = ImageVariantRepository.findByImageIds(browserImageIds);
    context.getRequest().setAttribute("imageVariantsByImageId", imageVariantsByImageId);
    // The originals' widths, so srcset can offer the full-size file as a candidate rather than
    // topping out at a thumbnail (issue #1370). One extra query for the whole page, not per row.
    context.getRequest().setAttribute("imageWidthsByImageId", ImageRepository.findWidthsByIds(browserImageIds));

    if ("reveal".equals(context.getRequest().getParameter("view"))) {
      context.setEmbedded(true);
    }

    String inputId = context.getRequest().getParameter("inputId");
    context.getRequest().setAttribute("inputId", inputId);

    // Show the editor
    context.setEmbedded(true);
    context.setJsp(JSP);
    return context;
  }
}
