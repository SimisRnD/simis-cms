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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;

/**
 * Validates image objects
 *
 * @author matt rajkowski
 * @created 5/3/18 3:25 PM
 */
public class ValidateImageCommand {

  private static Log LOG = LogFactory.getLog(ValidateImageCommand.class);

  public static void checkFile(Image imageBean) throws DataException {

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File imageFile = FileSystemCommand.resolveWithinRoot(serverRootPath, imageBean.getFileServerPath());
    if (imageFile == null || !imageFile.exists()) {
      return;
    }

    // Check mime type
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(imageFile.toPath());
    } catch (Exception e) {
      LOG.error("Error checking file for image data", e);
    }
    if (mimeType == null || !mimeType.split("/")[0].equals("image")) {
      LOG.warn("Detected mimeType = " + mimeType);
      throw new DataException("Could not determine image type");
    }
    LOG.debug("MimeType: " + mimeType);
    imageBean.setFileType(mimeType);

    // Use a streaming method to get the dimensions -- falls back to ImageMagick's `identify` for
    // formats the JDK's own ImageIO cannot decode (e.g. WebP), see ImageDimensionCommand.
    try {
      Dimension dimension = ImageDimensionCommand.readDimension(imageFile);
      imageBean.setWidth(dimension.width);
      imageBean.setHeight(dimension.height);
      LOG.debug("Width: " + imageBean.getWidth());
      LOG.debug("Height: " + imageBean.getHeight());
    } catch (Exception e) {
      LOG.warn("Image could not be read", e);
      throw new DataException("Image could not be read");
    }
  }
}
