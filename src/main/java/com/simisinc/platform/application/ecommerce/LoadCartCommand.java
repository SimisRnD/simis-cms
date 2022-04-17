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

package com.simisinc.platform.application.ecommerce;

import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads a user's cart
 *
 * @author matt rajkowski
 * @created 4/14/19 9:39 PM
 */
public class LoadCartCommand {

  private static Log LOG = LogFactory.getLog(LoadCartCommand.class);

  public static Cart loadCartByToken(String token) {
    if (StringUtils.isBlank(token)) {
      return null;
    }
    return CartRepository.findValidCartByToken(token);
  }

}
