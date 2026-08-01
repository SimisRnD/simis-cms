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
import com.simisinc.platform.application.cms.DeleteImageCommand;
import com.simisinc.platform.application.cms.ImageUsageCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

  @Test
  void executeWithASearchQueryUsesTheFilenameSpecificationInsteadOfFindAll() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    addQueryParameter(widgetContext, "query", "3d");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(any(ImageSpecification.class), isNull()))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);

      ArgumentCaptor<ImageSpecification> specCaptor = ArgumentCaptor.forClass(ImageSpecification.class);
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findAll(specCaptor.capture(), isNull()));
      Assertions.assertEquals("3d", specCaptor.getValue().getMatchesName());
      // findAll() (no-arg, "everything") must not also be called on the search path
      imageRepositoryMockedStatic.verify(ImageRepository::findAll, org.mockito.Mockito.never());
    }

    Assertions.assertEquals("3d", request.getAttribute("query"));
  }

  @Test
  void executeWithCheckUsageParamReturnsJsonInsteadOfRenderingTheJsp() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    addQueryParameter(widgetContext, "checkUsage", "true");
    addQueryParameter(widgetContext, "imageId", "7");

    Image image = new Image();
    image.setId(7L);
    image.setWebPath("2026/07");
    image.setFilename("hero.png");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<ImageUsageCommand> usageMockedStatic = mockStatic(ImageUsageCommand.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(7L)).thenReturn(image);
      usageMockedStatic.when(() -> ImageUsageCommand.findUsages(image))
          .thenReturn(List.of(new ImageUsageCommand.UsageReference("Web Page", "/solutions")));

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertTrue(widgetContext.hasJson(), "a checkUsage request must return JSON, not a JSP");
    Assertions.assertTrue(widgetContext.getJson().contains("\"orphaned\":false"));
    Assertions.assertTrue(widgetContext.getJson().contains("/solutions"));
  }

  @Test
  void deleteWithoutPermissionNeverCallsDeleteImageCommand() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    // Default logged-in test user has no roles at all -- neither admin nor content-manager
    addQueryParameter(widgetContext, "imageId", "1");

    try (MockedStatic<DeleteImageCommand> deleteMockedStatic = mockStatic(DeleteImageCommand.class)) {
      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.delete(widgetContext);

      deleteMockedStatic.verifyNoInteractions();
    }
  }

  @Test
  void deleteWithAdminRoleDeletesTheRequestedImage() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "imageId", "42");

    Image image = new Image();
    image.setId(42L);
    image.setFilename("old-banner.png");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<DeleteImageCommand> deleteMockedStatic = mockStatic(DeleteImageCommand.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(42L)).thenReturn(image);
      deleteMockedStatic.when(() -> DeleteImageCommand.deleteImage(image)).thenReturn(true);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.delete(widgetContext);

      deleteMockedStatic.verify(() -> DeleteImageCommand.deleteImage(image));
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(), any(), eq("image.delete"),
          any(), any(), any(), any(), any()));
    }

    Assertions.assertEquals("/admin/images", widgetContext.getRedirect());
    Assertions.assertEquals("Image deleted", widgetContext.getSuccessMessage());
  }

  @Test
  void bulkDeletePostRemovesOnlyTheSelectedImageIds() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("imageId", new String[] { "1", "3" });

    Image image1 = new Image();
    image1.setId(1L);
    Image image3 = new Image();
    image3.setId(3L);

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<DeleteImageCommand> deleteMockedStatic = mockStatic(DeleteImageCommand.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(1L)).thenReturn(image1);
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(3L)).thenReturn(image3);
      deleteMockedStatic.when(() -> DeleteImageCommand.deleteImage(any(Image.class))).thenReturn(true);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      deleteMockedStatic.verify(() -> DeleteImageCommand.deleteImage(image1));
      deleteMockedStatic.verify(() -> DeleteImageCommand.deleteImage(image3));
      // image id 2 was never selected/present -- findById must not even be looked up for it
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findById(2L), org.mockito.Mockito.never());
    }

    Assertions.assertEquals("2 of 2 selected images deleted.", widgetContext.getSuccessMessage());
  }

  @Test
  void bulkDeleteRejectsASelectionLargerThanTheMax() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    String[] tooMany = new String[AdminImageBrowserWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 1);
    }
    widgetContext.getParameterMap().put("imageId", tooMany);

    try (MockedStatic<DeleteImageCommand> deleteMockedStatic = mockStatic(DeleteImageCommand.class)) {
      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      deleteMockedStatic.verifyNoInteractions();
    }
    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }
}