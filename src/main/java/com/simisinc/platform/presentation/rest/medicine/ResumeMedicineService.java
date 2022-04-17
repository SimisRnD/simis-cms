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

package com.simisinc.platform.presentation.rest.medicine;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.medicine.ResumeMedicineCommand;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import com.simisinc.platform.presentation.rest.ServiceContext;
import com.simisinc.platform.presentation.rest.ServiceResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An endpoint for resuming medicine for an individual
 *
 * @author matt rajkowski
 * @created 9/25/18 10:36 AM
 */
public class ResumeMedicineService {

  private static Log LOG = LogFactory.getLog(ResumeMedicineService.class);

  // POST: resume/medicine/{medicineId}
  public ServiceResponse post(ServiceContext context) {
    String medicineId = context.getPathParam();
    if (!StringUtils.isNumeric(medicineId)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Medicine Id was not found in path param");
      return response;
    }
    Medicine medicine = MedicineRepository.findById(Long.parseLong(medicineId));
    if (medicine == null) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Medicine could not be found");
      return response;
    }

    try {
      // Resume the medicine
      boolean result = ResumeMedicineCommand.resumeMedicine(medicine, context.getUserId());
      if (!result) {
        ServiceResponse response = new ServiceResponse(400);
        response.getError().put("title", "The medicine could not be resumed");
        return response;
      }
      // Prepare the response
      ServiceResponse response = new ServiceResponse(200);
      response.getMeta().put("type", "medicine");
      response.getMeta().put("id", medicine.getId());
      return response;
    } catch (DataException e) {
      LOG.error("resumeError", e);
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
  }
}
