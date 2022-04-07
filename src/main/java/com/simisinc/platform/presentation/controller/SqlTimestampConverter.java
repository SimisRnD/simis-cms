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

package com.simisinc.platform.presentation.controller;

import org.apache.commons.beanutils.converters.DateTimeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public final class SqlTimestampConverter extends DateTimeConverter {

  private static Log LOG = LogFactory.getLog(SqlTimestampConverter.class);

  public SqlTimestampConverter() {
  }

  public SqlTimestampConverter(Object defaultValue) {
    super(defaultValue);
  }

  protected Class<?> getDefaultType() {
    return Timestamp.class;
  }

  protected DateFormat getFormat(Locale locale, TimeZone timeZone) {
    DateFormat format = null;
    if (locale == null) {
      format = DateFormat.getDateTimeInstance(3, 3);
    } else {
      format = DateFormat.getDateTimeInstance(3, 3, locale);
    }

    if (timeZone != null) {
      format.setTimeZone(timeZone);
    }

    return format;
  }
}
