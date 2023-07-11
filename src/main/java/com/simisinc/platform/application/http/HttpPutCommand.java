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

package com.simisinc.platform.application.http;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Functions for working with http requests
 *
 * @author matt rajkowski
 * @created 7/9/2023 5:32 PM
 */
public class HttpPutCommand {

  private static Log LOG = LogFactory.getLog(HttpGetToFileCommand.class);

  public static String execute(String url, Map<String, String> parameters) {
    return HttpPostCommand.execute(url, null, parameters, HttpPostCommand.PUT);
  }

  public static String execute(String url, Map<String, String> headers, Map<String, String> parameters) {
    return HttpPostCommand.execute(url, headers, parameters, HttpPostCommand.PUT);
  }

  public static String execute(String url, Map<String, String> headers, String data) {
    return HttpPostCommand.execute(url, headers, data, HttpPostCommand.PUT);
  }
}
