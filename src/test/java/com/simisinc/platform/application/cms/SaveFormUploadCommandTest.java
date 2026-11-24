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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveFormUploadCommand.Result;
import com.simisinc.platform.application.cms.SaveFormUploadCommand.UploadSource;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.malware.FakeClamd;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * Verifies {@link SaveFormUploadCommand} runs a form upload through every step and keeps nothing
 * for a file that fails one (issue #2130, step 5).
 */
class SaveFormUploadCommandTest {

  private static final long MB = 1_048_576L;

  @TempDir
  Path root;

  private MockedStatic<FileSystemCommand> fileSystem;
  private MockedStatic<LoadSitePropertyCommand> siteProperty;
  private MockedStatic<FormUploadRepository> repository;
  private FakeClamd clamd;

  @BeforeEach
  void setUp() throws IOException {
    fileSystem = mockStatic(FileSystemCommand.class, Mockito.CALLS_REAL_METHODS);
    fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
    siteProperty = mockStatic(LoadSitePropertyCommand.class);
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(50 * MB));
    repository = mockStatic(FormUploadRepository.class);
    repository.when(() -> FormUploadRepository.add(any())).thenAnswer(invocation -> {
      FormUpload record = invocation.getArgument(0);
      record.setId(42L);
      return record;
    });
    repository.when(() -> FormUploadRepository.remove(any())).thenReturn(true);
    scanner(FakeClamd.Mode.ANSWER);
  }

  @AfterEach
  void tearDown() throws IOException {
    clamd.close();
    repository.close();
    siteProperty.close();
    fileSystem.close();
  }

  private void scanner(FakeClamd.Mode mode) throws IOException {
    if (clamd != null) {
      clamd.close();
    }
    clamd = new FakeClamd(mode);
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.clamav.host")).thenReturn(clamd.host());
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.clamav.port")).thenReturn(String.valueOf(clamd.port()));
  }

  private static FormField screenshotField() {
    FormField field = new FormField();
    field.setName("screenshot");
    field.setLabel("Screenshot");
    field.setType("file");
    field.setAcceptedKinds("image");
    field.setMaxBytes(10 * MB);
    return field;
  }

  private static UploadSource source(String name, byte[] bytes) {
    return source(name, bytes.length, bytes);
  }

  private static UploadSource source(String name, long declaredSize, byte[] bytes) {
    return new UploadSource() {
      @Override
      public String submittedFileName() {
        return name;
      }

      @Override
      public long size() {
        return declaredSize;
      }

      @Override
      public InputStream openStream() {
        return new ByteArrayInputStream(bytes);
      }
    };
  }

  private List<String> filesUnderRoot() throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString()).sorted().collect(Collectors.toList());
    }
  }

  private void assertRejectedAndNothingKept(Result result, String reason) throws IOException {
    assertFalse(result.accepted());
    assertEquals(reason, result.reason());
    assertNull(result.upload());
    assertEquals(List.of(), filesUnderRoot(), "nothing written for a rejected file, quarantine included");
  }

  @Test
  void aPngNamedJpgIsStoredAsAPngWithAThumbnailUnderNamesTheServerChose() throws IOException {
    Result result = SaveFormUploadCommand.save("contact-us", screenshotField(),
        source("C:\\fakepath\\my screenshot.jpg", FormUploadFixtures.pngWithTransparency()));

    assertTrue(result.accepted(), String.valueOf(result.message()));
    FormUpload upload = result.upload();
    assertEquals(42L, upload.getId());
    assertEquals("contact-us", upload.getFormUniqueId());
    assertEquals("screenshot", upload.getFieldName());
    assertEquals("my screenshot.jpg", upload.getOriginalFilename(), "kept for display only");
    assertEquals("image/png", upload.getContentType());
    assertEquals(FormUpload.SCAN_CLEAN, upload.getScanStatus());
    assertNotNull(upload.getScanChecked());
    assertTrue(upload.getFilePath().matches("forms/contact-us/[0-9a-f-]{36}\\.png"), upload.getFilePath());
    assertEquals(upload.getFilePath().replace(".png", "-thumbnail.png"), upload.getThumbnailPath());
    assertEquals(List.of(upload.getFilePath(), upload.getThumbnailPath()).stream().sorted().collect(Collectors.toList()),
        filesUnderRoot(), "the image and its thumbnail, and no quarantine copy");
    assertEquals(Files.size(root.resolve(upload.getFilePath())), upload.getFileLength());
  }

  @Test
  void aFileOfTheWrongTypeIsRejectedBeforeAnythingIsWritten() throws IOException {
    assertRejectedAndNothingKept(
        SaveFormUploadCommand.save("contact-us", screenshotField(), source("logo.png", FormUploadFixtures.svg())),
        ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void aFileTheScannerFlagsIsRejectedAndNothingIsKept() throws IOException {
    scanner(FakeClamd.Mode.FLAG_EVERYTHING);

    Result result = SaveFormUploadCommand.save("contact-us", screenshotField(), source("shot.png", FormUploadFixtures.pngWithTransparency()));

    assertRejectedAndNothingKept(result, MalwareScanCommand.REASON_INFECTED);
    assertEquals("Screenshot: the file didn't pass a security check. Choose another file.", result.message());
  }

  @Test
  void withNoScannerConfiguredTheFileIsRejectedAndNothingIsKept() throws IOException {
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.clamav.host")).thenReturn("");

    assertRejectedAndNothingKept(
        SaveFormUploadCommand.save("contact-us", screenshotField(), source("shot.png", FormUploadFixtures.pngWithTransparency())),
        MalwareScanCommand.REASON_SCAN_UNAVAILABLE);
  }

  @Test
  void aFileLargerThanTheSizeItDeclaredIsStoppedAtTheLimit() throws IOException {
    FormField field = screenshotField();
    field.setMaxBytes(MB);
    byte[] png = FormUploadFixtures.pngWithTransparency();
    byte[] oversized = java.util.Arrays.copyOf(png, (int) MB + 100);

    Result result = SaveFormUploadCommand.save("contact-us", field, source("shot.png", 1000, oversized));

    assertRejectedAndNothingKept(result, ValidateFormUploadCommand.REASON_TOO_LARGE);
    assertEquals("Screenshot: files can be up to 1 MB.", result.message());
  }

  @Test
  void theQuarantineCopyReadsNoMoreThanOneBytePastTheLimit() throws IOException {
    FormField field = screenshotField();
    field.setMaxBytes(MB);
    byte[] png = FormUploadFixtures.pngWithTransparency();
    // A body that claims 1,000 bytes and runs on for 64 MB: only the limit plus one byte is read
    long endless = 64 * MB;
    List<long[]> readsPerOpen = new java.util.ArrayList<>();
    UploadSource source = new UploadSource() {
      @Override
      public String submittedFileName() {
        return "shot.png";
      }

      @Override
      public long size() {
        return 1000;
      }

      @Override
      public InputStream openStream() {
        long[] read = new long[1];
        readsPerOpen.add(read);
        return new InputStream() {
          @Override
          public int read() {
            if (read[0] >= endless) {
              return -1;
            }
            int b = read[0] < png.length ? png[(int) read[0]] & 0xff : 0;
            read[0]++;
            return b;
          }
        };
      }
    };

    Result result = SaveFormUploadCommand.save("contact-us", field, source);

    assertRejectedAndNothingKept(result, ValidateFormUploadCommand.REASON_TOO_LARGE);
    assertEquals(2, readsPerOpen.size(), "opened once to check the type and once to copy");
    assertEquals(MB + 1, readsPerOpen.get(1)[0], "the copy stops one byte past the limit");
  }

  @Test
  void anImageThatWillNotDecodeIsRejectedAndNothingIsKept() throws IOException {
    assertRejectedAndNothingKept(
        SaveFormUploadCommand.save("contact-us", screenshotField(), source("broken.png", FormUploadFixtures.truncatedPng())),
        SanitizeFormUploadCommand.REASON_UNDECODABLE);
  }

  @Test
  void whenTheRecordCannotBeSavedTheStoredFilesAreRemoved() throws IOException {
    repository.when(() -> FormUploadRepository.add(any())).thenReturn(null);

    Result result = SaveFormUploadCommand.save("contact-us", screenshotField(), source("shot.png", FormUploadFixtures.pngWithTransparency()));

    assertRejectedAndNothingKept(result, SaveFormUploadCommand.REASON_SYSTEM_ERROR);
    assertEquals("Screenshot: the file couldn't be saved. Try again.", result.message());
  }

  @Test
  void discardRemovesAStoredUploadsFilesAndRecord() throws IOException {
    FormUpload upload = SaveFormUploadCommand.save("contact-us", screenshotField(),
        source("shot.png", FormUploadFixtures.pngWithTransparency())).upload();
    assertEquals(2, filesUnderRoot().size());

    SaveFormUploadCommand.discard(List.of(upload));

    assertEquals(List.of(), filesUnderRoot());
    repository.verify(() -> FormUploadRepository.remove(upload));
  }

  @Test
  void theFormsDirectoryNameIsReducedToSafeCharacters() {
    assertEquals("contact-us", SaveFormUploadCommand.directoryName("contact-us"));
    assertEquals("media-inquiries", SaveFormUploadCommand.directoryName("Media Inquiries"));
    assertEquals("etc-passwd", SaveFormUploadCommand.directoryName("../../etc/passwd"));
    assertEquals("form", SaveFormUploadCommand.directoryName("../"));
    assertEquals("form", SaveFormUploadCommand.directoryName(null));
  }

  @Test
  void theVisitorsFilenameIsCleanedForDisplay() {
    assertEquals("shot.png", SaveFormUploadCommand.displayName("C:\\fakepath\\shot.png"));
    assertEquals("evilname.png", SaveFormUploadCommand.displayName("evil\"\r\nname.png"));
    assertNull(SaveFormUploadCommand.displayName("  "));
    assertEquals(255, SaveFormUploadCommand.displayName("a".repeat(300) + ".png").length());
  }
}
