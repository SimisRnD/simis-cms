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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Event details for when a form is submitted
 *
 * @author matt rajkowski
 * @created 2/6/2022 8:56 PM
 * @song Atom Dancer - Hannah Holland
 */
@NoArgsConstructor
public class FormSubmittedEvent extends Event {

  public static final String ID = "form-submitted";

  @Setter
  @Getter
  private long formId = -1L;

  @Setter
  @Getter
  private String location = null;

  @Setter
  @Getter
  private String emailAddressesTo = null;

  public FormSubmittedEvent(FormData formData, String emailAddressesTo) {
    this.formId = formData.getId();
    this.emailAddressesTo = emailAddressesTo;
    this.location = GeoIPCommand.getCityStateCountryLocation(formData.getIpAddress());
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public String getGeneratedId() {
    return new SimpleDateFormat("yyyyMMdd").format(new Date(getOccurred())) + StringUtils.leftPad(String.valueOf(formId), 4, "0");
  }

  public FormData getFormData() {
    return FormDataRepository.findById(formId);
  }
}
