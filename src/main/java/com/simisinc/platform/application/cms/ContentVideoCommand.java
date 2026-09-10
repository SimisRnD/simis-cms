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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.PageVideo;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Finds the self-hosted videos a content block shows, and describes each one from its record in the
 * file library, so a page can emit schema.org VideoObject for them (issue #1795).
 *
 * <p>
 * A video reaches a page as hand-authored markup -- {@code <video>} with a {@code <source>} the
 * video browser inserted -- so nothing upstream knows a video is on the page at all. Reading it back
 * out of the rendered HTML is what makes that knowable, and the {@code src} is an identifier rather
 * than a guess: {@code /assets/view/20260820014544-8/name.mp4} carries the file's own id, which is
 * how the download servlet resolves the same URL.
 * </p>
 *
 * <p>
 * Nothing here is inferred from surrounding prose. A heading above a video is not its title and a
 * nearby paragraph is not its description, however much they look like it; guessing would put
 * markup in front of Google that contradicts the page. Every value comes from the file record or
 * from an attribute the author wrote, and a video missing any of what Google requires -- a name, a
 * poster, an upload date -- is skipped rather than emitted incomplete.
 * </p>
 *
 * <p>
 * <b>Files are resolved as a guest, deliberately, whoever is viewing.</b> Structured data is written
 * for crawlers, which arrive signed out, so describing a video the visiting administrator can see
 * and the crawler cannot would put a title and summary from a restricted folder into a page's
 * markup. Resolving as {@link UserSession#GUEST_ID} means the same page emits the same graph for
 * everyone, which is also the only version that is safe to cache.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ContentVideoCommand {

  private static Log LOG = LogFactory.getLog(ContentVideoCommand.class);

  /** Both routes serve the same FileItem; only the response headers differ (FileDownloadCommand) */
  private static final String[] ASSET_PATHS = { "/assets/view/", "/assets/file/" };

  private ContentVideoCommand() {
    // Static utility, not instantiated
  }

  /**
   * Describes each self-hosted video in a block of rendered content HTML.
   *
   * @param html the content as it will be sent to the browser
   * @return one entry per video that can be fully described, in document order; never null
   */
  public static List<PageVideo> findVideos(String html) {
    List<PageVideo> videoList = new ArrayList<>();
    // Nearly every content block on every page has no video in it. Parsing is not free, so the
    // cheap test runs first and the parser only sees the blocks that could produce something.
    if (StringUtils.isBlank(html) || !StringUtils.containsIgnoreCase(html, "<video")) {
      return videoList;
    }
    try {
      Document document = Jsoup.parseBodyFragment(html);
      for (Element videoElement : document.select("video")) {
        PageVideo pageVideo = describe(videoElement);
        if (pageVideo != null) {
          videoList.add(pageVideo);
        }
      }
    } catch (Exception e) {
      // A markup surprise costs the page its video markup, never the page itself.
      LOG.warn("Could not read videos from content", e);
    }
    return videoList;
  }

  /** @return the described video, or null when this element cannot produce a complete one */
  private static PageVideo describe(Element videoElement) {
    // Google requires a thumbnail, and the poster attribute is the only place on the page that
    // says which image belongs to this video. Without one there is nothing to emit.
    String poster = StringUtils.trimToNull(videoElement.attr("poster"));
    if (!isFetchableImageUrl(poster)) {
      return null;
    }
    FileItem fileItem = resolveFile(videoElement);
    if (fileItem == null) {
      return null;
    }
    // Google requires a name and an upload date as well. The title is set from the filename at
    // upload when the uploader leaves it blank (ValidateFileCommand#checkFile), so in practice this
    // is present and an administrator improves it by editing the file, not by editing the page.
    if (StringUtils.isBlank(fileItem.getTitle()) || fileItem.getCreated() == null) {
      LOG.debug("Video file has no title or created date, skipping: " + fileItem.getId());
      return null;
    }
    PageVideo pageVideo = new PageVideo();
    pageVideo.setName(fileItem.getTitle());
    pageVideo.setDescription(StringUtils.trimToNull(fileItem.getSummary()));
    pageVideo.setThumbnailUrl(poster);
    // The file's own current URL rather than the src the author wrote: an author's copy can point
    // at a superseded version's web path, while getUrl() is what the library links to today.
    pageVideo.setContentUrl("/assets/view/" + fileItem.getUrl());
    pageVideo.setEncodingFormat(fileItem.getMimeType());
    pageVideo.setUploadDate(fileItem.getCreated());
    return pageVideo;
  }

  /**
   * Whether a poster is something a crawler could actually fetch and index.
   *
   * <p>
   * A thumbnail Google cannot retrieve fails the rich result, so a poster that is not a real
   * location -- an inline {@code data:} image pasted by an editor, or a bare relative path with
   * nothing to resolve it against once it is lifted out of the page and into a JSON-LD graph --
   * means no VideoObject rather than one pointing nowhere. jsoup's safelist registers poster with
   * no protocol rule, so this is the only place the value is judged.
   * </p>
   */
  private static boolean isFetchableImageUrl(String poster) {
    if (poster == null) {
      return false;
    }
    return poster.startsWith("/")
        || StringUtils.startsWithIgnoreCase(poster, "https://")
        || StringUtils.startsWithIgnoreCase(poster, "http://");
  }

  /**
   * The file behind this element, from its own src or the first source child that names one.
   *
   * @return the file, or null when nothing resolves to a guest-viewable video
   */
  private static FileItem resolveFile(Element videoElement) {
    List<String> candidates = new ArrayList<>();
    candidates.add(videoElement.attr("src"));
    for (Element sourceElement : videoElement.select("source")) {
      candidates.add(sourceElement.attr("src"));
    }
    for (String src : candidates) {
      long fileId = parseFileId(src);
      if (fileId <= 0) {
        continue;
      }
      FileItem fileItem = LoadFileCommand.loadFileByIdForAuthorizedUser(fileId, UserSession.GUEST_ID);
      if (fileItem == null) {
        // Either no such file, or it lives in a folder that does not allow guests -- a crawler
        // could not fetch it either way, so there is nothing truthful to say about it.
        continue;
      }
      // A src pointing at something that is not a video would otherwise describe, say, a PDF as a
      // VideoObject.
      if (!StringUtils.startsWithIgnoreCase(fileItem.getMimeType(), "video/")) {
        continue;
      }
      return fileItem;
    }
    return null;
  }

  /**
   * The file id an asset URL carries, e.g. 8 from {@code /assets/view/20260820014544-8/name.mp4}.
   *
   * <p>
   * The trailing number of the first path segment, matching how DownloadFileWidget parses the same
   * URL. Works on an absolute URL too, since the asset path is searched for rather than anchored.
   * </p>
   *
   * @return the id, or -1 when this is not an asset URL or carries no usable id
   */
  static long parseFileId(String src) {
    if (StringUtils.isBlank(src)) {
      return -1;
    }
    for (String assetPath : ASSET_PATHS) {
      int pathIdx = src.indexOf(assetPath);
      if (pathIdx == -1) {
        continue;
      }
      String remainder = src.substring(pathIdx + assetPath.length());
      int slashIdx = remainder.indexOf('/');
      String resourceValue = slashIdx == -1 ? remainder : remainder.substring(0, slashIdx);
      int dashIdx = resourceValue.lastIndexOf('-');
      if (dashIdx == -1) {
        continue;
      }
      try {
        return Long.parseLong(resourceValue.substring(dashIdx + 1));
      } catch (NumberFormatException nfe) {
        // Not an id -- a hand-written or rewritten path. Try the next asset path form.
      }
    }
    return -1;
  }
}
