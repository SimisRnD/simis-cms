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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;

/**
 * Deletes an image tag globally -- unassigning it from every image that carries it. Mirrors
 * items' DeleteTagCommand.
 *
 * @author SimIS
 * @created 8/5/2026
 */
public class DeleteImageTagCommand {

  public static boolean deleteImageTag(ImageTag imageTagBean) throws DataException {

    if (imageTagBean == null || imageTagBean.getId() == -1) {
      throw new DataException("The tag was not specified");
    }

    return ImageTagRepository.remove(imageTagBean);
  }

}
