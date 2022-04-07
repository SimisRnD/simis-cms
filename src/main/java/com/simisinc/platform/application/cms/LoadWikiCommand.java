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

import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/10/19 11:41 AM
 */
public class LoadWikiCommand {

  private static Log LOG = LogFactory.getLog(LoadWikiCommand.class);

  public static Wiki loadWikiByUniqueId(String wikiUniqueId) {
    return WikiRepository.findByUniqueId(wikiUniqueId);
  }

  public static Wiki loadWikiById(long wikiId) {
    return WikiRepository.findById(wikiId);
  }

}
