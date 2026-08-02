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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods to delete a tag (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class DeleteTagCommand {

  private static Log LOG = LogFactory.getLog(DeleteTagCommand.class);

  public static boolean deleteTag(Tag tagBean) throws DataException {

    // Verify the object
    if (tagBean == null || tagBean.getId() == -1) {
      throw new DataException("The tag was not specified");
    }

    return TagRepository.remove(tagBean);
  }

}
