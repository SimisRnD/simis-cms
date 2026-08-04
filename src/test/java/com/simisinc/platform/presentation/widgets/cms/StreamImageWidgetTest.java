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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.HashMap;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies {@link StreamImageWidget}'s {@code ?variant=} handling (issue #411): a requested
 * variant is served when it exists and its file is present, and the original is served as a
 * graceful fallback in every other case. {@link ImageRepository} and {@link ImageVariantRepository}
 * are statically mocked; {@link FileSystemCommand#getFileServerRootPath()} is stubbed to a
 * {@code @TempDir} so the widget reads real files.
 *
 * <p>
 * Note: {@link WidgetContext#getParameter} reads from {@code WidgetContext}'s own internal
 * parameter map, not {@code request.getParameter()} -- so a {@code ?variant=} value is set via
 * {@code context.getParameterMap()}, not by stubbing the mocked request.
 * </p>
 *
 * @author SimIS Inc.
 */
class StreamImageWidgetTest {

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);

  private WidgetContext newContext(String uri) throws IOException {
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getMethod()).thenReturn("GET");
    when(request.getDateHeader("If-Modified-Since")).thenReturn(-1L);
    when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
    WidgetContext context = new WidgetContext(request, response, "widget1", "/assets/img");
    context.setParameterMap(new HashMap<>());
    return context;
  }

  private void setVariantParameter(WidgetContext context, String variantType) {
    context.getParameterMap().put("variant", new String[] { variantType });
  }

  private Image imageWithRealFile(Path tempDir, long id, String relativePath) throws IOException {
    Path absolute = tempDir.resolve(relativePath);
    Files.createDirectories(absolute.getParent());
    Files.writeString(absolute, "original bytes");

    Image image = new Image();
    image.setId(id);
    image.setWebPath("20260803120000");
    image.setFileServerPath(relativePath);
    image.setFileType("image/png");
    image.setCreated(new Timestamp(1_700_000_000_000L));
    return image;
  }

  private ImageVariant variantWithRealFile(Path tempDir, long imageId, String variantType, String relativePath)
      throws IOException {
    Path absolute = tempDir.resolve(relativePath);
    Files.createDirectories(absolute.getParent());
    Files.writeString(absolute, "variant bytes");

    ImageVariant variant = new ImageVariant();
    variant.setImageId(imageId);
    variant.setVariantType(variantType);
    variant.setFileServerPath(relativePath);
    variant.setFileType("image/webp");
    variant.setCreated(new Timestamp(1_700_000_500_000L));
    variant.setModified(new Timestamp(1_700_000_500_000L));
    return variant;
  }

  @Test
  void executeStreamsTheOriginalWhenNoVariantParamIsGiven(@TempDir Path tempDir) throws Exception {
    Image image = imageWithRealFile(tempDir, 42L, "images/2026/08/photo.png");
    WidgetContext context = newContext("/assets/img/20260803120000-42/photo.png");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 42L)).thenReturn(image);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNotNull(result);
      verify(response).setContentType("image/png");
    }
  }

  @Test
  void executeStreamsTheRequestedVariantWhenItExistsAndItsFileIsPresent(@TempDir Path tempDir) throws Exception {
    Image image = imageWithRealFile(tempDir, 42L, "images/2026/08/photo.png");
    ImageVariant variant = variantWithRealFile(tempDir, 42L, "thumbnail", "images/2026/08/photo-thumbnail.png");
    WidgetContext context = newContext("/assets/img/20260803120000-42/photo.png");
    setVariantParameter(context, "thumbnail");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<ImageVariantRepository> variantRepository = mockStatic(ImageVariantRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 42L)).thenReturn(image);
      variantRepository.when(() -> ImageVariantRepository.findByImageIdAndVariantType(42L, "thumbnail"))
          .thenReturn(variant);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNotNull(result);
      verify(response).setContentType("image/webp");
    }
  }

  @Test
  void executeFallsBackToTheOriginalWhenTheRequestedVariantRowDoesNotExist(@TempDir Path tempDir) throws Exception {
    Image image = imageWithRealFile(tempDir, 42L, "images/2026/08/photo.png");
    WidgetContext context = newContext("/assets/img/20260803120000-42/photo.png");
    setVariantParameter(context, "large");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<ImageVariantRepository> variantRepository = mockStatic(ImageVariantRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 42L)).thenReturn(image);
      variantRepository.when(() -> ImageVariantRepository.findByImageIdAndVariantType(42L, "large")).thenReturn(null);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNotNull(result);
      verify(response).setContentType("image/png");
    }
  }

  @Test
  void executeFallsBackToTheOriginalWhenTheVariantRowExistsButItsFileIsMissing(@TempDir Path tempDir)
      throws Exception {
    Image image = imageWithRealFile(tempDir, 42L, "images/2026/08/photo.png");
    ImageVariant variant = new ImageVariant();
    variant.setImageId(42L);
    variant.setVariantType("medium");
    variant.setFileServerPath("images/2026/08/photo-medium.png"); // never written to disk
    variant.setFileType("image/webp");
    variant.setCreated(new Timestamp(1_700_000_500_000L));
    WidgetContext context = newContext("/assets/img/20260803120000-42/photo.png");
    setVariantParameter(context, "medium");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<ImageVariantRepository> variantRepository = mockStatic(ImageVariantRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 42L)).thenReturn(image);
      variantRepository.when(() -> ImageVariantRepository.findByImageIdAndVariantType(42L, "medium"))
          .thenReturn(variant);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNotNull(result);
      verify(response).setContentType("image/png");
    }
  }

  @Test
  void executeReturnsNullWhenTheImageRecordDoesNotExist() throws Exception {
    WidgetContext context = newContext("/assets/img/20260803120000-999/gone.png");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 999L)).thenReturn(null);

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNull(result);
    }
  }

  @Test
  void executeReturnsNotModifiedWhenIfModifiedSinceIsFresh(@TempDir Path tempDir) throws Exception {
    Image image = imageWithRealFile(tempDir, 42L, "images/2026/08/photo.png");
    WidgetContext context = newContext("/assets/img/20260803120000-42/photo.png");
    when(request.getDateHeader("If-Modified-Since")).thenReturn(image.getCreated().getTime());

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findByWebPathAndId("20260803120000", 42L)).thenReturn(image);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      WidgetContext result = new StreamImageWidget().execute(context);

      assertNotNull(result);
      verify(response).setStatus(eq(HttpServletResponse.SC_NOT_MODIFIED));
    }
  }
}
