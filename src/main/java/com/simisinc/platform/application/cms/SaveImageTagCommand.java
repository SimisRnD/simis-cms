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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;

/**
 * Finds an image tag by name, or creates it if no tag with that name (case-insensitive) exists
 * yet. Mirrors items' SaveTagCommand, minus the collection scoping.
 *
 * @author SimIS
 * @created 8/5/2026
 */
public class SaveImageTagCommand {

  private static Log LOG = LogFactory.getLog(SaveImageTagCommand.class);

  public static ImageTag saveImageTag(String name, long createdBy) throws DataException {

    if (StringUtils.isBlank(name)) {
      throw new DataException("A name is required, please check the fields and try again");
    }

    ImageTag existing = ImageTagRepository.findByName(name);
    if (existing != null) {
      return existing;
    }

    LOG.debug("Creating a new image tag... ");
    ImageTag imageTag = new ImageTag();
    imageTag.setName(name.trim());
    imageTag.setCreatedBy(createdBy);
    ImageTag saved = ImageTagRepository.save(imageTag);
    if (saved == null) {
      // A concurrent request may have created the same (case-insensitive) name between the
      // findByName() check above and this insert -- the unique index rejects ours, insert()
      // swallows that as a plain save failure. Re-check once: if a tag with this name now
      // exists, that's the tag the admin asked for -- use it instead of hard-failing.
      ImageTag racedWith = ImageTagRepository.findByName(name);
      if (racedWith != null) {
        return racedWith;
      }
      throw new DataException("The tag could not be saved");
    }
    return saved;
  }

}
