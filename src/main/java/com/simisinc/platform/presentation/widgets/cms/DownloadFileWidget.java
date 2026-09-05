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

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.domain.model.cms.FileDownload;
import com.simisinc.platform.infrastructure.persistence.cms.FileDownloadRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.FileDownloadCommand;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Streams previously uploaded files and videos, supports resume 
 *
 * @author matt rajkowski
 * @created 12/13/18 2:50 PM
 */
public class DownloadFileWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(DownloadFileWidget.class);

  public WidgetContext execute(WidgetContext context) {

    // GET uri /assets/file/20180503171549-5/something.pdf
    // GET uri /assets/view/20180503171549-5/something.pdf

    // Use the request uri
    String resourceValue = context.getUri().substring(context.getResourcePath().length() + 1);
    if (resourceValue.contains("/")) {
      resourceValue = resourceValue.substring(0, resourceValue.indexOf("/"));
    }
    LOG.debug("Using resource value: " + resourceValue);
    int dashIdx = resourceValue.lastIndexOf("-");
    if (dashIdx == -1) {
      return null;
    }

    // Determine the file id and web path
    String webPath = resourceValue.substring(0, dashIdx);
    String fileIdValue = resourceValue.substring(dashIdx + 1);
    long fileId = Long.parseLong(fileIdValue);
    if (fileId <= 0) {
      return null;
    }

    // Determine if the file should be sent with the mime type (used below, and to label the audit
    // event for every outcome, including the access-denied/not-found case which has no FileItem yet)
    boolean doView = "true".equals(context.getPreferences().get("view"));
    String accessEventType = doView ? "folder_file.view" : "folder_file.download";

    // Determine the file and access permissions
    FileItem record;
    if (context.hasRole("admin")) {
      // The file can be downloaded
      record = LoadFileCommand.loadItemById(fileId);
    } else {
      // User must have view access in the folder's user group
      record = LoadFileCommand.loadLatestFileByIdForAuthorizedUser(webPath, fileId, context.getUserId());
    }
    if (record == null) {
      LOG.warn("File record does not exist or no access: " + fileId);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.FAILURE,
          "folder_file", String.valueOf(fileId), null, "not found or access denied");
      return null;
    }

    // An expired file's content is off-limits to everyone except admins (who still need access for
    // management/verification purposes, matching the admin bypass used above to select which
    // LoadFileCommand method authorized this request). This only blocks the content-serving path
    // below (streaming, viewing, and the remote-URL redirect) -- it does not affect
    // LoadFileCommand's other, non-content callers (e.g. edit-form metadata fetch, delete, version
    // listing), which must keep working on expired files.
    if (!context.hasRole("admin") && record.isExpired()) {
      LOG.warn("File is expired: " + fileId);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.FAILURE,
          "folder_file", String.valueOf(fileId), null, "file is expired");
      return null;
    }

    // A web path that doesn't match the file's current one belongs to a specific archived version
    // (see FileVersion#getUrl()) -- resolve that version so its own bytes are streamed, rather than
    // whatever is currently live. The access check above already used this web path (matched either
    // to the live record or to a file_versions row for this file id) to authorize the request, so a
    // failed lookup here means the version was removed after that check, not a permissions gap.
    FileVersion versionRecord = null;
    if (!webPath.equals(record.getWebPath())) {
      FileVersionSpecification versionSpecification = new FileVersionSpecification();
      versionSpecification.setFileId(fileId);
      versionSpecification.setWebPath(webPath);
      List<FileVersion> versionList = FileVersionRepository.findAll(versionSpecification, null);
      if (versionList.size() != 1) {
        LOG.warn("File version does not exist for web path: " + webPath);
        AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.FAILURE,
            "folder_file", String.valueOf(fileId), null, "version not found");
        return null;
      }
      versionRecord = versionList.get(0);
    }
    String fileServerPath = versionRecord != null ? versionRecord.getFileServerPath() : record.getFileServerPath();
    String mimeType = versionRecord != null ? versionRecord.getMimeType() : record.getMimeType();
    String filename = versionRecord != null ? versionRecord.getFilename() : record.getFilename();
    Timestamp lastModifiedTimestamp = versionRecord != null ? versionRecord.getCreated() : record.getModified();

    // Determine if this file is a remote URL
    if (record.getFileType() != null && record.getFileType().equals("URL")) {
      String url = record.getFilename();
      if (url.startsWith("http://") || url.startsWith("https://")) {
        // Update the download counter
        FileItemRepository.incrementDownloadCount(record);
        recordDownload(context, record, null);
        AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.SUCCESS,
            "folder_file", String.valueOf(record.getId()), record.getFilename(), "redirect to external URL");
        // Redirect to the URL
        context.setRedirect(url);
        return context;
      }
    }

    // Make sure it exists
    File file = new File(FileSystemCommand.getFileServerRootPath() + fileServerPath);
    if (!file.isFile()) {
      LOG.warn("Server file does not exist: " + fileServerPath);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.FAILURE,
          "folder_file", String.valueOf(record.getId()), filename, "server file missing");
      return null;
    }

    // Determine if the file is being viewed or downloaded
    long lastModified = lastModifiedTimestamp.getTime();
    if (doView && StringUtils.isNotBlank(mimeType)) {

      // @todo go through this and use for all downloads so pause/resume works on large files

      if (mimeType.startsWith("video/")) {
        try {
          MultipartFileSender.fromFile(file)
              .with(context.getRequest())
              .with(context.getResponse())
              .withMimeType(mimeType)
              .withFilename(filename)
              .serveResource();
          AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.SUCCESS,
              "folder_file", String.valueOf(record.getId()), filename, "video stream");
        } catch (Exception e) {
          LOG.debug("Video aborted");
          AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.FAILURE,
              "folder_file", String.valueOf(record.getId()), filename, e.getMessage());
        } finally {
          context.setHandledResponse(true);
          // @todo determine if whole range or end was viewed to register a download count
          // Update the download counter
          //    FileItemRepository.incrementDownloadCount(record);
        }
        return context;
      }

      // The file is being viewed (in a new window); the safe disposition + content type are set below.

      // Check for a last-modified header and return 304 if possible
      long headerValue = context.getRequest().getDateHeader("If-Modified-Since");
      if (lastModified <= headerValue + 1000) {
        context.getResponse().setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        context.setHandledResponse(true);
        return context;
      }

    }

    // Set header info: nosniff, a safe inline/attachment disposition, and the content type. An uploaded
    // HTML or SVG file is served as a download (never rendered inline), so it cannot execute in this origin.
    context.getResponse().setDateHeader("Last-Modified", lastModified);
    FileDownloadCommand.applyContentHeaders(context.getResponse(), mimeType, filename, doView);
    context.getResponse().setContentLength((int) file.length());

    // Check for head method
    if ("head".equalsIgnoreCase(context.getRequest().getMethod())) {
      context.setHandledResponse(true);
      return context;
    }
    
    // Stream the file
    try {
      FileInputStream in = new FileInputStream(file);
      OutputStream out = context.getResponse().getOutputStream();

      // Copy the contents of the file to the output stream
      byte[] buf = new byte[1024];
      int count = 0;
      while ((count = in.read(buf)) >= 0) {
        out.write(buf, 0, count);
      }
      out.close();
      in.close();
    } catch (Exception e) {
      LOG.debug("Stream error: " + e.getMessage());
    }

    // Update the download counter
    FileItemRepository.incrementDownloadCount(record);
    recordDownload(context, record, versionRecord);
    AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, accessEventType, AuditEventCommand.SUCCESS,
        "folder_file", String.valueOf(record.getId()), filename, null);

    // Return success
    context.setHandledResponse(true);
    return context;
  }

  /**
   * Records the download with a date on it, alongside the cumulative counter that has always been
   * kept. The counter answers "most downloaded ever" and nothing else, because it carries no dates;
   * these rows are what let the Content Analytics report ask the same question over a window.
   *
   * <p>Deliberately not fatal. A reporting write must never be the reason a file fails to deliver,
   * so the repository swallows and logs its own errors and this returns regardless. Nor does it go
   * through SaveWebPageHitCommand's queue: that exists because page hits arrive constantly, whereas
   * a download is rare enough that the insert can sit beside the counter update already happening
   * here.
   */
  private void recordDownload(WidgetContext context, FileItem record, FileVersion versionRecord) {
    FileDownload fileDownload = new FileDownload();
    fileDownload.setFileId(record.getId());
    if (versionRecord != null) {
      fileDownload.setVersionId(versionRecord.getId());
    }
    if (context.getUserSession() != null) {
      fileDownload.setSessionId(context.getUserSession().getSessionId());
      if (context.getUserSession().isLoggedIn()) {
        fileDownload.setDownloadBy(context.getUserId());
      }
    }
    FileDownloadRepository.save(fileDownload);
  }
}
