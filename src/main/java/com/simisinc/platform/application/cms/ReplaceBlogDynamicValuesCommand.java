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

import com.simisinc.platform.domain.model.cms.Blog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods for working with dynamic values
 *
 * @author matt rajkowski
 * @created 2/24/2020 7:49 AM
 */
public class ReplaceBlogDynamicValuesCommand {

  private static Log LOG = LogFactory.getLog(ReplaceBlogDynamicValuesCommand.class);

  /**
   * Replaces dynamic values for blogs
   *
   * @param blog
   * @param value
   * @return
   */
  public static String replaceValues(Blog blog, String value) {
    try {
      // Blog
      value = StringUtils.replace(value, "${blog.id}", String.valueOf(blog.getId()));
      value = StringUtils.replace(value, "${blog.uniqueId}", blog.getUniqueId());
      value = StringUtils.replace(value, "${blog.name}", blog.getName());
    } catch (Exception e) {
      LOG.warn("Could not replace blog values");
      return null;
    }
    return value;
  }

}
