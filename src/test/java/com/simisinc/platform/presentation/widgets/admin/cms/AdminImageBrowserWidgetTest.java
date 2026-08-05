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
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.infrastructure.scheduler.cms.FocalPointVariantJob;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import org.jobrunr.scheduling.BackgroundJobRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<ImageVariantRepository> imageVariantRepositoryMockedStatic = mockStatic(ImageVariantRepository.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(isNull(), any(DataConstraints.class)))
          .thenReturn(imageList);
      // Issue #411 PR2's batch-prefetch: findAll() returns a non-empty list here, so
      // findByImageIds() is reached and must be mocked like any other repository call.
      imageVariantRepositoryMockedStatic.when(() -> ImageVariantRepository.findByImageIds(anyCollection()))
          .thenReturn(Collections.emptyMap());

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
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(any(ImageSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);

      ArgumentCaptor<ImageSpecification> specCaptor = ArgumentCaptor.forClass(ImageSpecification.class);
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findAll(specCaptor.capture(), any(DataConstraints.class)));
      Assertions.assertEquals("3d", specCaptor.getValue().getMatchesName());
      // findAll() (no-arg, "everything, unpaginated") must not also be called on the search path
      imageRepositoryMockedStatic.verify(ImageRepository::findAll, org.mockito.Mockito.never());
    }

    Assertions.assertEquals("3d", request.getAttribute("query"));
  }

  @Test
  void executeDefaultsPagingToPageOneAtTheDefaultPageSizeWhenNoPagingParamsAreGiven() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(isNull(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findAll(isNull(), constraintsCaptor.capture()));
      Assertions.assertEquals(1, constraintsCaptor.getValue().getPageNumber());
      Assertions.assertEquals(AdminImageBrowserWidget.DEFAULT_PAGE_SIZE, constraintsCaptor.getValue().getPageSize());
    }
  }

  @Test
  void executeWithAPageParamPassesThatPageNumberInTheDataConstraintsOnTheUnfilteredBranch() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    addQueryParameter(widgetContext, "page", "3");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(isNull(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);

      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findAll(isNull(), constraintsCaptor.capture()));
      Assertions.assertEquals(3, constraintsCaptor.getValue().getPageNumber());
    }
  }

  @Test
  void executeWithASearchQueryAndAPageParamPassesBothTheSpecificationAndThePageNumber() {
    // Proves the search-filtered branch also respects pagination, not just the unfiltered branch.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    addQueryParameter(widgetContext, "query", "3d");
    addQueryParameter(widgetContext, "page", "2");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(any(ImageSpecification.class), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);

      ArgumentCaptor<ImageSpecification> specCaptor = ArgumentCaptor.forClass(ImageSpecification.class);
      ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);
      imageRepositoryMockedStatic.verify(() -> ImageRepository.findAll(specCaptor.capture(), constraintsCaptor.capture()));
      Assertions.assertEquals("3d", specCaptor.getValue().getMatchesName());
      Assertions.assertEquals(2, constraintsCaptor.getValue().getPageNumber());
    }

    // The search term must be echoed into the paging-links param so page-forward/back preserves it
    Assertions.assertEquals("query=3d", request.getAttribute("recordPagingParams"));
  }

  @Test
  void executeWithAnOutOfRangePageStillRendersTheJspWithAnEmptyListInsteadOfAnError() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    addQueryParameter(widgetContext, "page", "999");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      // A real out-of-range OFFSET returns zero rows rather than erroring (see
      // ImageRepositorySearchTest's pagination tests for the real-DB proof); the widget must
      // simply pass that empty list through to the JSP, not treat it as a failure.
      imageRepositoryMockedStatic.when(() -> ImageRepository.findAll(isNull(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(AdminImageBrowserWidget.JSP, widgetContext.getJsp());
    List<Image> imageListRequest = (List) request.getAttribute("imageList");
    Assertions.assertNotNull(imageListRequest);
    Assertions.assertTrue(imageListRequest.isEmpty());
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
  void deleteWithAdminRolePreservesThePageParamOnRedirect() {
    // Regression test for issue #498 slice 2: deleting from page 2+ must redirect back to that
    // page instead of resetting to page 1. See redirectWithQuery().
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "page", "3");

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
    }

    Assertions.assertEquals("/admin/images?page=3", widgetContext.getRedirect());
  }

  @Test
  void deleteWithAdminRolePreservesQueryAndPageTogetherOnRedirect() {
    // Regression test for issue #498 slice 2: a delete from a search-filtered, paged view must
    // carry both the search term and the page number back into the redirect.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "query", "3d");
    addQueryParameter(widgetContext, "page", "2");

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
    }

    Assertions.assertEquals("/admin/images?query=3d&page=2", widgetContext.getRedirect());
  }

  @Test
  void deleteWithoutAPageParamDoesNotAddOneToTheRedirect() {
    // Page 1 is the implicit default -- the redirect should stay bare rather than growing a
    // redundant "?page=1", matching the pre-pagination redirect shape.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "page", "1");

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
    }

    Assertions.assertEquals("/admin/images", widgetContext.getRedirect());
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
  void bulkDeletePreservesTheQueryAndPageParamsOnRedirect() {
    // Regression test for issue #498 slice 2: bulk-deleting from a search-filtered, paged view
    // must redirect back to that same page/search combination instead of resetting to page 1.
    // Unlike single delete, the bulk-delete <form> in image-browser.jsp has no action attribute,
    // so the browser submits it to the current document address (with its query string) -- these
    // params arrive as ordinary request parameters, exactly like addQueryParameter simulates here.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("imageId", new String[] { "1", "3" });
    addQueryParameter(widgetContext, "query", "3d");
    addQueryParameter(widgetContext, "page", "4");

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
    }

    Assertions.assertEquals("/admin/images?query=3d&page=4", widgetContext.getRedirect());
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

  @Test
  void setFocalPointWithAdminRoleSavesAndEnqueuesTheRegenerationJob() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "setFocalPoint" });
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "focalX", "12.5");
    addQueryParameter(widgetContext, "focalY", "87.25");

    Image image = new Image();
    image.setId(42L);
    image.setFilename("hero.png");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(42L)).thenReturn(image);
      imageRepositoryMockedStatic.when(() -> ImageRepository.save(image)).thenReturn(image);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      Assertions.assertEquals(0, new BigDecimal("12.5").compareTo(image.getFocalX()));
      Assertions.assertEquals(0, new BigDecimal("87.25").compareTo(image.getFocalY()));
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((FocalPointVariantJob job) -> job.getImageId() == 42L)));
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(), any(), eq("image.setFocalPoint"),
          any(), any(), any(), any(), any()));
    }

    Assertions.assertEquals("Focal point saved", widgetContext.getSuccessMessage());
    Assertions.assertEquals("/admin/images", widgetContext.getRedirect());
  }

  @Test
  void setFocalPointWithAnUnknownImageIdProducesAnErrorAndDoesNotEnqueue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "setFocalPoint" });
    addQueryParameter(widgetContext, "imageId", "999");
    addQueryParameter(widgetContext, "focalX", "50");
    addQueryParameter(widgetContext, "focalY", "50");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(999L)).thenReturn(null);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      jobRequest.verifyNoInteractions();
      imageRepositoryMockedStatic.verify(() -> ImageRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void setFocalPointWithAnOutOfRangeValueProducesAnErrorAndDoesNotSaveOrEnqueue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "setFocalPoint" });
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "focalX", "150");
    addQueryParameter(widgetContext, "focalY", "50");

    Image image = new Image();
    image.setId(42L);

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(42L)).thenReturn(image);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      jobRequest.verifyNoInteractions();
      imageRepositoryMockedStatic.verify(() -> ImageRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void setFocalPointWithANonNumericValueProducesAnErrorAndDoesNotSaveOrEnqueue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "setFocalPoint" });
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "focalX", "not-a-number");
    addQueryParameter(widgetContext, "focalY", "50");

    Image image = new Image();
    image.setId(42L);

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepositoryMockedStatic.when(() -> ImageRepository.findById(42L)).thenReturn(image);

      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      jobRequest.verifyNoInteractions();
      imageRepositoryMockedStatic.verify(() -> ImageRepository.save(any()), never());
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void setFocalPointWithoutPermissionNeverTouchesTheRepository() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"adminImageBrowser\"/>");
    // Default logged-in test user has no roles at all -- neither admin nor content-manager
    widgetContext.getParameterMap().put("command", new String[] { "setFocalPoint" });
    addQueryParameter(widgetContext, "imageId", "42");
    addQueryParameter(widgetContext, "focalX", "50");
    addQueryParameter(widgetContext, "focalY", "50");

    try (MockedStatic<ImageRepository> imageRepositoryMockedStatic = mockStatic(ImageRepository.class)) {
      AdminImageBrowserWidget widget = new AdminImageBrowserWidget();
      widget.post(widgetContext);

      imageRepositoryMockedStatic.verifyNoInteractions();
    }
  }
}