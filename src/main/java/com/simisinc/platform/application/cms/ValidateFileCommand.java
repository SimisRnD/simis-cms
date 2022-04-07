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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.nio.file.Files;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/13/18 11:47 AM
 */
public class ValidateFileCommand {

  private static Log LOG = LogFactory.getLog(ValidateFileCommand.class);

  public static void checkFile(FileItem fileItemBean) {

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File file = new File(serverRootPath + fileItemBean.getFileServerPath());
    if (!file.exists()) {
      LOG.warn("File does not exist: " + serverRootPath + fileItemBean.getFileServerPath());
      return;
    }

    // Simplify the filename for display
    if (StringUtils.isBlank(fileItemBean.getTitle())) {
      if (StringUtils.isBlank(fileItemBean.getTitle())) {
        fileItemBean.setTitle(generateTitle(fileItemBean.getFilename().trim()));
      }
    }

    // Generate Hash
    fileItemBean.setFileHash(FileSystemCommand.getFileChecksum(file));

    // Determine mime type and file type
    fileItemBean.setMimeType(getMimeType(file, fileItemBean.getExtension()));
    fileItemBean.setFileType(getFileType(fileItemBean.getMimeType(), fileItemBean.getExtension()));

    // @todo perform image analysis
    // @todo perform text extraction
  }

  public static void checkFile(ItemFileItem fileItemBean) {

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File file = new File(serverRootPath + fileItemBean.getFileServerPath());
    if (!file.exists()) {
      LOG.warn("File does not exist: " + serverRootPath + fileItemBean.getFileServerPath());
      return;
    }

    // Simplify the filename for display
    if (StringUtils.isBlank(fileItemBean.getTitle())) {
      fileItemBean.setTitle(generateTitle(fileItemBean.getFilename().trim()));
    }

    // Generate Hash
    fileItemBean.setFileHash(FileSystemCommand.getFileChecksum(file));

    // Determine mime type and file type
    fileItemBean.setMimeType(getMimeType(file, fileItemBean.getExtension()));
    fileItemBean.setFileType(getFileType(fileItemBean.getMimeType(), fileItemBean.getExtension()));

    // @todo perform image analysis
    // @todo perform text extraction
  }

  private static String generateTitle(String title) {
    if (title == null) {
      return title;
    }
    if (title.contains(".") && !title.contains(" ")) {
      if (title.indexOf(".") != title.lastIndexOf(".")) {
        int idx = title.indexOf(".");
        title = title.substring(0, idx) + " " + title.substring(idx + 1);
      }
    }
    if (!title.contains(" - ")) {
      title = StringUtils.replace(title, "-", " - ");
    }
    title = StringUtils.replace(title, "_", " ");
    title = StringUtils.replace(title, "@", " ");
    title = StringUtils.replace(title, "/", " ");
    title = StringUtils.replace(title, "\\", " ");
    title = StringUtils.replace(title, "    ", " ");
    title = StringUtils.replace(title, "   ", " ");
    title = StringUtils.replace(title, "  ", " ");
    if (title.contains(".")) {
      // String the extension
      title = title.substring(0, title.lastIndexOf("."));
    }
    return title;
  }


  public static String getMimeType(File file, String extension) {
    // Finally, check mime type
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(file.toPath());
    } catch (Exception e) {
      LOG.error("Error checking file for mime type: " + file.toPath(), e);
    }
    if (StringUtils.isBlank(mimeType)) {
      if ("doc".equals(extension)) {
        return "application/msword";
      } else if ("docx".equals(extension)) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      } else if ("ppt".equals(extension)) {
        return "application/vnd.ms-powerpoint";
      } else if ("pptx".equals(extension)) {
        return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
      } else if ("xls".equals(extension)) {
        return "application/vnd.ms-excel";
      } else if ("xlsx".equals(extension)) {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      } else if ("vsdx".equals(extension)) {
        return "application/vnd.visio";
      }
    }
    LOG.debug("MimeType: " + mimeType);
    return mimeType;
  }

  public static String getFileType(String mimeType, String extension) {
    if (StringUtils.isBlank(mimeType)) {
      return null;
    }
    // Mime Type to File Type conversion
    // image, video, pdf, zip, xls/xlsx, doc/docx, other
    if (mimeType.contains("/")) {
      if (mimeType.endsWith("/pdf")) {
        return "PDF";
      } else if (mimeType.endsWith("/zip")) {
        return "Archive";
      } else if (mimeType.endsWith("/cpp")) {
        return "Code";
      } else if (mimeType.endsWith("/java")) {
        return "Code";
      } else if (mimeType.endsWith("/py")) {
        return "Code";
      } else if (mimeType.contains("wordprocessing") || mimeType.contains("msword")) {
        return "Document";
      } else if (mimeType.contains("spreadsheet") || mimeType.contains("ms-excel")) {
        return "Spreadsheet";
      } else if (mimeType.contains("visio")) {
        return "Diagram";
      } else if (mimeType.contains("presentation") || mimeType.contains("powerpoint")) {
        return "Presentation";
      } else {
        return StringUtils.capitalize(mimeType.substring(0, mimeType.indexOf("/")));
      }
    }
    return null;
  }
}
