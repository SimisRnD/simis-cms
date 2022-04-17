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

package com.simisinc.platform.application.items;

import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods for displaying item address information
 *
 * @author matt rajkowski
 * @created 4/25/18 8:30 PM
 */
public class ItemAddressCommand {

  private static Log LOG = LogFactory.getLog(ItemAddressCommand.class);

  public static String toText(Item itemBean) {
    if (itemBean == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotBlank(itemBean.getStreet())) {
      sb.append(itemBean.getStreet());
    }
    if (StringUtils.isNotBlank(itemBean.getAddressLine2())) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(itemBean.getAddressLine2());
    }
    if (StringUtils.isNotBlank(itemBean.getAddressLine3())) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(itemBean.getAddressLine3());
    }
    if (StringUtils.isNotBlank(itemBean.getCity())) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(itemBean.getCity());
    }
    if (StringUtils.isNotBlank(itemBean.getState())) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(itemBean.getState());
    }
    if (StringUtils.isNotBlank(itemBean.getPostalCode())) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(itemBean.getPostalCode());
    }
    if (StringUtils.isNotBlank(itemBean.getCountry()) && !"united states".equals(itemBean.getCountry().toLowerCase())) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(itemBean.getCountry());
    }
    if (sb.length() > 0) {
      return sb.toString();
    }
    return null;
  }

}
