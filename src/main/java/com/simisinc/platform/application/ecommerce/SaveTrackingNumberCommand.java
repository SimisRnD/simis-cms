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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.persistence.ecommerce.TrackingNumberRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves tracking number objects
 *
 * @author matt rajkowski
 * @created 4/22/20 8:32 PM
 */
public class SaveTrackingNumberCommand {

  private static Log LOG = LogFactory.getLog(SaveTrackingNumberCommand.class);

  public static TrackingNumber save(TrackingNumber trackingNumberBean) throws DataException {
    // Required dependencies
    if (trackingNumberBean.getOrderId() == -1) {
      throw new DataException("An order is required");
    }
    if (StringUtils.isBlank(trackingNumberBean.getTrackingNumber())) {
      throw new DataException("A tracking number is required");
    }
    if (trackingNumberBean.getShippingCarrierId() <= 0) {
      throw new DataException("A shipping carrier selection is required");
    }
    if (trackingNumberBean.getCreatedBy() == -1) {
      throw new DataException("The user id was not set");
    }
    // Save the record
    if (!TrackingNumberRepository.exists(trackingNumberBean)) {
      return TrackingNumberRepository.save(trackingNumberBean);
    }
    return trackingNumberBean;
  }
}
