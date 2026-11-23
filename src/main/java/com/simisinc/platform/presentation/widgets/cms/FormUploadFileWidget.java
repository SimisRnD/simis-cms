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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.FileDownloadCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves a file a visitor attached to a form (issue #2130), at {@code /assets/form-upload?id=N}, to
 * the roles that review form data -- the page declares them, so the controller refuses anyone else
 * before this runs.
 *
 * <p>Only a file whose scan passed is served: one pending or rejected answers 403, whoever asks. The
 * image itself is always sent as a download ({@code attachment}, {@code nosniff}, a neutral content
 * type), never rendered in this origin; {@code &thumbnail=true} sends its thumbnail inline, with the
 * same sandboxing headers as library images, for the review screen. Every request, served or refused,
 * is recorded in the audit log.
 *
 * @author SimIS Inc.
 */
public class FormUploadFileWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(FormUploadFileWidget.class);

  static final String AUDIT_EVENT = "form_upload.download";

  public WidgetContext execute(WidgetContext context) {
    long id = context.getParameterAsLong("id");
    boolean thumbnail = "true".equals(context.getParameter("thumbnail"));

    FormUpload upload = FormUploadRepository.findById(id);
    if (upload == null) {
      audit(context, id, null, AuditEventCommand.FAILURE, "not found");
      return null;
    }
    if (!upload.isClean()) {
      // Never served until the scan has passed, to anyone
      LOG.warn("Refused a form upload whose scan status is " + upload.getScanStatus() + ": " + id);
      audit(context, id, upload.getOriginalFilename(), AuditEventCommand.FAILURE, "scan status " + upload.getScanStatus());
      context.getResponse().setStatus(HttpServletResponse.SC_FORBIDDEN);
      context.setHandledResponse(true);
      return context;
    }

    String path = thumbnail ? upload.getThumbnailPath() : upload.getFilePath();
    String root = FileSystemCommand.getFileServerRootPath();
    File file = root != null && StringUtils.isNotBlank(path) ? FileSystemCommand.resolveWithinRoot(root, path) : null;
    if (file == null || !file.isFile()) {
      LOG.warn("A form upload's file is missing: " + id);
      audit(context, id, upload.getOriginalFilename(), AuditEventCommand.FAILURE, "server file missing");
      return null;
    }

    HttpServletResponse response = context.getResponse();
    if (thumbnail) {
      FileDownloadCommand.applyInlineMediaHeaders(response, upload.getThumbnailContentType());
    } else {
      FileDownloadCommand.applyContentHeaders(response, upload.getContentType(), downloadName(upload), false);
    }
    // The review screen is the only place these appear; nothing should keep a copy
    response.setHeader("Cache-Control", "private, no-store");
    response.setContentLengthLong(file.length());
    if (!"head".equalsIgnoreCase(context.getRequest().getMethod())) {
      try (InputStream in = Files.newInputStream(file.toPath())) {
        OutputStream out = response.getOutputStream();
        in.transferTo(out);
        out.flush();
      } catch (Exception e) {
        LOG.debug("Form upload stream error: " + e.getMessage());
      }
    }
    audit(context, id, upload.getOriginalFilename(), AuditEventCommand.SUCCESS, thumbnail ? "thumbnail" : "download");
    context.setHandledResponse(true);
    return context;
  }

  /**
   * The visitor's name for the file, but with the extension of what is actually stored: the stored
   * file is the re-encoded image, so a PNG sent as "photo.jpg" downloads as "photo.png"
   */
  static String downloadName(FormUpload upload) {
    String extension = StringUtils.substringAfterLast(upload.getFilePath(), ".");
    String base = StringUtils.defaultIfBlank(upload.getOriginalFilename(), "attachment");
    int dot = base.lastIndexOf('.');
    if (dot > 0) {
      base = base.substring(0, dot);
    }
    return StringUtils.isBlank(extension) ? base : base + "." + extension;
  }

  private static void audit(WidgetContext context, long id, String filename, String outcome, String detail) {
    AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, AUDIT_EVENT, outcome,
        "form_upload", String.valueOf(id), filename, detail);
  }
}
