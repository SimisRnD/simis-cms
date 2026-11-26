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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ValidateFormUploadCommand.Result;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * Verifies {@link ValidateFormUploadCommand}, the type and size check a form upload passes before
 * anything is written (issue #2130), against the cases the issue names: PNG renamed .jpg, JPEG
 * renamed .exe, SVG renamed .png, a 0-byte file, and a file one byte over the limit.
 */
class ValidateFormUploadCommandTest {

  private static final long MB = 1_048_576L;

  private MockedStatic<LoadSitePropertyCommand> siteProperty;

  @BeforeEach
  void siteUploadLimitOfFiftyMegabytes() {
    siteProperty = mockStatic(LoadSitePropertyCommand.class);
    siteUploadLimit(50 * MB);
  }

  @AfterEach
  void closeMocks() {
    siteProperty.close();
  }

  private void siteUploadLimit(long bytes) {
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn(String.valueOf(bytes));
  }

  /** An image field labeled "Screenshot" with a 10 MB limit */
  private static FormField screenshotField() {
    FormField field = new FormField();
    field.setLabel("Screenshot");
    field.setType("file");
    field.setAcceptedKinds("image");
    field.setMaxBytes(10 * MB);
    return field;
  }

  private static Result check(String filename, byte[] content) {
    return ValidateFormUploadCommand.check(screenshotField(), filename, content.length, new ByteArrayInputStream(content));
  }

  private static void assertRejected(Result result, String reason) {
    assertFalse(result.accepted(), "expected a rejection for " + reason);
    assertEquals(reason, result.reason());
    assertNull(result.contentType());
    assertNull(result.extension());
  }

  /** A stream that fails the test if anything reads it */
  private static InputStream mustNotBeRead() {
    return new InputStream() {
      @Override
      public int read() {
        throw new AssertionError("the content was read, but a size failure needs nothing read");
      }
    };
  }

  // -- The cases #2130 names -------------------------------------------------------------------------

  @Test
  void aPngNamedJpgPassesAsAPng() {
    Result result = check("screenshot.jpg", FormUploadFixtures.pngWithTransparency());

    assertTrue(result.accepted());
    assertEquals("image/png", result.contentType(), "the bytes decide the type, not the name");
    assertEquals("png", result.extension(), "and is named for what it is, so later steps treat it as a PNG");
  }

  @Test
  void aJpegNamedExeIsRejected() {
    assertRejected(check("photo.exe", FormUploadFixtures.jpeg()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void anSvgNamedPngIsRejected() {
    assertRejected(check("logo.png", FormUploadFixtures.svg()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void htmlNamedJpgIsRejected() {
    assertRejected(check("photo.jpg", FormUploadFixtures.html()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void anEmptyFileIsRejectedWithoutReadingIt() {
    Result result = ValidateFormUploadCommand.check(screenshotField(), "screenshot.png", 0, mustNotBeRead());

    assertRejected(result, ValidateFormUploadCommand.REASON_EMPTY);
    assertEquals("Screenshot: the file is empty. Choose another file.", result.message());
  }

  @Test
  void aFileOneByteOverTheLimitIsRejectedWithoutReadingItAndOneAtTheLimitPasses() {
    Result over = ValidateFormUploadCommand.check(screenshotField(), "screenshot.png", 10 * MB + 1, mustNotBeRead());
    assertRejected(over, ValidateFormUploadCommand.REASON_TOO_LARGE);
    assertEquals("Screenshot: files can be up to 10 MB.", over.message());

    byte[] png = FormUploadFixtures.pngWithTransparency();
    Result atLimit = ValidateFormUploadCommand.check(screenshotField(), "screenshot.png", 10 * MB, new ByteArrayInputStream(png));
    assertTrue(atLimit.accepted());
  }

  // -- Size ------------------------------------------------------------------------------------------

  @Test
  void aSiteUploadLimitLoweredBelowTheFieldsLimitWins() {
    siteUploadLimit(5 * MB);

    Result result = ValidateFormUploadCommand.check(screenshotField(), "screenshot.png", 5 * MB + 1, mustNotBeRead());

    assertRejected(result, ValidateFormUploadCommand.REASON_TOO_LARGE);
    assertEquals("Screenshot: files can be up to 5 MB.", result.message());
  }

  @Test
  void aLimitUnderOneMegabyteIsShownInKilobytes() {
    assertEquals("512 KB", ValidateFormUploadCommand.formatSize(512 * 1024));
    assertEquals("10 MB", ValidateFormUploadCommand.formatSize(10 * MB));
  }

  // -- Type ------------------------------------------------------------------------------------------

  @Test
  void eachAllowedImageTypePassesUnderItsOwnName() {
    assertEquals("image/jpeg", check("photo.jpg", FormUploadFixtures.jpeg()).contentType());
    assertEquals("image/jpeg", check("photo.JPEG", FormUploadFixtures.jpeg()).contentType());
    assertEquals("image/png", check("shot.PNG", FormUploadFixtures.pngWithTransparency()).contentType());
  }

  @Test
  void aWebpIsRejectedUnderAnyName() {
    // Left off the list so no upload is decoded by ImageMagick and libwebp
    assertRejected(check("phone.webp", FormUploadFixtures.webpHeader()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check("phone.png", FormUploadFixtures.webpHeader()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check("photo.webp", FormUploadFixtures.jpeg()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void aGifIsRejectedUnderAnyName() {
    assertRejected(check("animation.gif", FormUploadFixtures.gif()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check("animation.png", FormUploadFixtures.gif()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void aPdfIsRejectedOnAnImageFieldUnderAnyName() {
    assertRejected(check("brief.pdf", FormUploadFixtures.pdf()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check("brief.png", FormUploadFixtures.pdf()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void anExecutableIsRejectedEvenNamedAsAnImage() {
    assertRejected(check("setup.png", FormUploadFixtures.exe()), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void anImageWithNoUsableExtensionIsRejected() {
    byte[] png = FormUploadFixtures.pngWithTransparency();
    assertRejected(check("screenshot", png), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check("screenshot.", png), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertRejected(check(null, png), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
  }

  @Test
  void onlyTheLastExtensionOfTheFilesOwnNameCounts() {
    byte[] png = FormUploadFixtures.pngWithTransparency();
    // An older browser's full client path, and a folder name with a dot in it
    assertTrue(check("C:\\fakepath\\shot.png", png).accepted());
    assertRejected(check("images.png/shot", png), ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    // A double extension is judged by its last part, and the stored name is the server's own
    Result doubled = check("shell.php.png", png);
    assertTrue(doubled.accepted());
    assertEquals("png", doubled.extension());
  }

  @Test
  void aFieldWithNoAcceptedKindsAcceptsNothing() {
    FormField field = screenshotField();
    field.setAcceptedKinds(null);
    byte[] png = FormUploadFixtures.pngWithTransparency();

    Result result = ValidateFormUploadCommand.check(field, "shot.png", png.length, new ByteArrayInputStream(png));

    assertRejected(result, ValidateFormUploadCommand.REASON_TYPE_NOT_ALLOWED);
    assertEquals("Screenshot: this type of file isn't accepted. Choose a different file.", result.message());
  }

  @Test
  void aStreamThatCannotBeReadIsRejectedAsUnreadable() {
    InputStream broken = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("connection reset");
      }
    };

    Result result = ValidateFormUploadCommand.check(screenshotField(), "shot.png", 100, broken);

    assertRejected(result, ValidateFormUploadCommand.REASON_UNREADABLE);
  }

  // -- What the visitor is told ----------------------------------------------------------------------

  @Test
  void theTypeMessageNamesTheRuleAndNothingTheCheckFound() {
    Result result = check("logo.png", FormUploadFixtures.svg());

    assertEquals("Screenshot: this type of file isn't accepted. Choose Images (JPG, PNG).", result.message());
    assertFalse(result.message().toLowerCase().contains("svg"), "the visitor isn't told what the bytes were");
  }

  @Test
  void theSameMessageCoversABadNameAndBadContent() {
    // So a visitor probing the check cannot tell which half failed
    assertEquals(check("photo.exe", FormUploadFixtures.jpeg()).message(), check("logo.png", FormUploadFixtures.svg()).message());
  }
}
