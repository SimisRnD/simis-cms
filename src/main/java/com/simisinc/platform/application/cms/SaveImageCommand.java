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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Validates and saves image objects
 *
 * @author matt rajkowski
 * @created 5/3/18 3:30 PM
 */
public class SaveImageCommand {

  private static Log LOG = LogFactory.getLog(SaveImageCommand.class);

  public static Image saveImage(Image imageBean) throws DataException {

    // Validate the required fields
    if (StringUtils.isBlank(imageBean.getFilename())) {
      throw new DataException("A file name is required, please check the fields and try again");
    }
    if (StringUtils.isBlank(imageBean.getFileServerPath())) {
      LOG.error("The developer needs to set a path");
      throw new DataException("A system path error occurred");
    }
    if (imageBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this record was not set");
    }

    // Transform the fields and store...
    Image image;
    if (imageBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      image = ImageRepository.findById(imageBean.getId());
      if (image == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      image = new Image();
      // Determine the web path for downloads, can randomize, etc.
      Date created = new Date(System.currentTimeMillis());
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
      image.setWebPath(sdf.format(created));
    }
    image.setFilename(imageBean.getFilename());
    image.setFileServerPath(imageBean.getFileServerPath());
    image.setCreatedBy(imageBean.getCreatedBy());
    image.setFileLength(imageBean.getFileLength());
    image.setFileType(imageBean.getFileType());
    image.setWidth(imageBean.getWidth());
    image.setHeight(imageBean.getHeight());
    return ImageRepository.save(image);
  }

}
