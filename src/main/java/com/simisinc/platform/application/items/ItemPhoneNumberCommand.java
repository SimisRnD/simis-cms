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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods for formatting an item's phone number
 *
 * @author matt rajkowski
 * @created 5/29/18 9:40 AM
 */
public class ItemPhoneNumberCommand {

  private static Log LOG = LogFactory.getLog(ItemPhoneNumberCommand.class);

  public static String format(String phoneNumberValue) {
    if (StringUtils.isBlank(phoneNumberValue)) {
      return null;
    }
    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    try {
      Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(phoneNumberValue, "US");
      if (phoneNumberValue.startsWith("+")) {
        return phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
      } else {
        return phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
      }
    } catch (NumberParseException e) {
      LOG.error("NumberParseException was thrown", e);
    }
    return phoneNumberValue;
  }

}
