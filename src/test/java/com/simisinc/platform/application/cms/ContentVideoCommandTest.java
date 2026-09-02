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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.PageVideo;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Reading self-hosted videos back out of rendered content (issue #1795).
 */
class ContentVideoCommandTest {

  private static final String POSTER = "/assets/img/20260824035858-255/SimIS%20HTT%20Video%20Poster.jpg";

  private static FileItem videoFile() {
    FileItem fileItem = new FileItem();
    fileItem.setId(8L);
    fileItem.setTitle("HTT in action");
    fileItem.setSummary("Four HTT units moving independently across open terrain.");
    fileItem.setMimeType("video/mp4");
    fileItem.setWebPath("20260820014544");
    fileItem.setFilename("SimIS-HTT long.mp4");
    fileItem.setCreated(Timestamp.valueOf("2026-08-20 01:45:44"));
    return fileItem;
  }

  private static String markup(String posterAttribute) {
    return "<h3>See HTT in Action</h3>"
        + "<div class=\"responsive-embed widescreen\"><video " + posterAttribute
        + " controls=\"controls\"><source src=\"/assets/view/20260820014544-8/SimIS-HTT%20long.mp4\""
        + " type=\"video/mp4\"></video></div>";
  }

  @Test
  void aPosteredVideoIsDescribedFromItsFileRecord() {
    List<PageVideo> videoList;
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(8L, UserSession.GUEST_ID))
          .thenReturn(videoFile());
      videoList = ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\""));
    }
    assertEquals(1, videoList.size());
    PageVideo pageVideo = videoList.get(0);
    // The name and description come from the file record, not from the heading and paragraph
    // wrapped around the video -- those read like a title and a description and are not one
    assertEquals("HTT in action", pageVideo.getName());
    assertEquals("Four HTT units moving independently across open terrain.", pageVideo.getDescription());
    assertEquals(POSTER, pageVideo.getThumbnailUrl());
    assertEquals("video/mp4", pageVideo.getEncodingFormat());
    assertEquals(Timestamp.valueOf("2026-08-20 01:45:44"), pageVideo.getUploadDate());
    // The file's own current URL, so a src left pointing at a superseded version still resolves to
    // what the library serves today
    assertEquals("/assets/view/20260820014544-8/SimIS-HTT%20long.mp4", pageVideo.getContentUrl());
  }

  @Test
  void aVideoWithNoPosterIsSkippedRatherThanEmittedWithoutAThumbnail() {
    // thumbnailUrl is required, and nothing else on the page says which image belongs to the video
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(videoFile());
      assertTrue(ContentVideoCommand.findVideos(markup("")).isEmpty());
    }
  }

  @Test
  void aFileTheGuestCannotSeeIsNotDescribed() {
    // Resolving as a guest is what keeps a restricted folder's titles out of a public page's
    // markup, and keeps the emitted graph identical for every visitor
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(null);
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\"")).isEmpty());
    }
  }

  @Test
  void filesAreAlwaysResolvedAsAGuestWhoeverIsViewing() {
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(videoFile());
      ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\""));
      loadFile.verify(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(eq(8L), eq(UserSession.GUEST_ID)));
    }
  }

  @Test
  void aFileThatIsNotAVideoIsNotDescribedAsOne() {
    FileItem notAVideo = videoFile();
    notAVideo.setMimeType("application/pdf");
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(notAVideo);
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\"")).isEmpty());
    }
  }

  @Test
  void aFileWithNoTitleIsSkippedBecauseNameIsRequired() {
    FileItem untitled = videoFile();
    untitled.setTitle("  ");
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(untitled);
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\"")).isEmpty());
    }
  }

  @Test
  void aFileWithNoSummaryStillProducesAVideoBecauseDescriptionIsOptional() {
    FileItem noSummary = videoFile();
    noSummary.setSummary(null);
    List<PageVideo> videoList;
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(noSummary);
      videoList = ContentVideoCommand.findVideos(markup("poster=\"" + POSTER + "\""));
    }
    assertEquals(1, videoList.size());
    assertNull(videoList.get(0).getDescription());
  }

  @Test
  void everyVideoOnThePageIsReported() {
    // The home page shows four, each in its own modal
    StringBuilder html = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      html.append(markup("poster=\"" + POSTER + "\""));
    }
    List<PageVideo> videoList;
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(videoFile());
      videoList = ContentVideoCommand.findVideos(html.toString());
    }
    assertEquals(4, videoList.size());
  }

  @Test
  void contentWithNoVideoIsNeverParsed() {
    // Nearly every content block on the site takes this path, so it must not reach the parser or
    // the database at all
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      assertTrue(ContentVideoCommand.findVideos("<p>Just some copy about targets and ranges.</p>").isEmpty());
      assertTrue(ContentVideoCommand.findVideos(null).isEmpty());
      assertTrue(ContentVideoCommand.findVideos("").isEmpty());
      loadFile.verifyNoInteractions();
    }
  }

  @Test
  void aVideoBackgroundIsNotDescribed() {
    // A section's decorative background never reaches here at all -- content.jsp renders it from
    // the videoBackgroundUrl preference, outside contentHtml. This guards the other half: markup
    // shaped like one, pasted into content, is still skipped, because the poster attribute is what
    // says an author meant this video to be presented, and a background has none
    String backgroundMarkup = "<div class=\"video-background\"><video autoplay muted loop>"
        + "<source src=\"/assets/view/20260820014544-8/SimIS-HTT%20long.mp4\" type=\"video/mp4\">"
        + "</video></div>";
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(videoFile());
      assertTrue(ContentVideoCommand.findVideos(backgroundMarkup).isEmpty());
    }
  }

  @Test
  void aPosterAGoogleCrawlerCouldNotFetchIsTreatedAsNoPoster() {
    // A thumbnail that cannot be retrieved fails the rich result, so it is worse than no video
    // node at all. jsoup's safelist registers poster with no protocol rule, so an inline image or
    // a bare relative path does reach here
    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(anyLong(), anyLong()))
          .thenReturn(videoFile());
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"data:image/gif;base64,R0lGODlhAQ==\"")).isEmpty());
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"assets/img/poster.jpg\"")).isEmpty());
      assertTrue(ContentVideoCommand.findVideos(markup("poster=\"   \"")).isEmpty());
      // An absolute poster on another host is fetchable, so it stays
      assertEquals("https://cdn.example.net/poster.jpg",
          ContentVideoCommand.findVideos(markup("poster=\"https://cdn.example.net/poster.jpg\""))
              .get(0).getThumbnailUrl());
    }
  }

  @Test
  void theSrcIsReadForItsFileIdAndNothingElse() {
    assertEquals(8L, ContentVideoCommand.parseFileId("/assets/view/20260820014544-8/SimIS-HTT%20long.mp4"));
    assertEquals(8L, ContentVideoCommand.parseFileId("/assets/file/20260820014544-8/SimIS-HTT%20long.mp4"));
    // An absolute URL is how content ported from another host arrives
    assertEquals(3L, ContentVideoCommand.parseFileId("https://example.org/assets/view/20181214165905-3/AIMS.mp4"));
    // A file id with no filename after it still resolves
    assertEquals(11L, ContentVideoCommand.parseFileId("/assets/view/20260820014630-11"));
    // Anything that is not an asset URL, or carries no id, resolves to nothing rather than to a
    // neighbouring file
    assertEquals(-1L, ContentVideoCommand.parseFileId("/videos/promo.mp4"));
    assertEquals(-1L, ContentVideoCommand.parseFileId("https://cdn.example.org/promo.mp4"));
    assertEquals(-1L, ContentVideoCommand.parseFileId("/assets/view/20260820014544/SimIS-HTT.mp4"));
    assertEquals(-1L, ContentVideoCommand.parseFileId("/assets/view/notanid-x/SimIS-HTT.mp4"));
    assertEquals(-1L, ContentVideoCommand.parseFileId(""));
    assertEquals(-1L, ContentVideoCommand.parseFileId(null));
  }
}
