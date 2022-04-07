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

package com.simisinc.platform.application.xapi;

import com.simisinc.platform.domain.model.xapi.XapiStatement;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.work.Expression;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/2021 9:55 AM
 */
public class XapiStatementCommand {

  private static Log LOG = LogFactory.getLog(XapiStatementCommand.class);

  public static String populateMessage(XapiStatement statement, Map<String, Object> mappings) {
    // message: '_{{ user.fullName }}_ **{{ verb }}** a blog post: [{{ blogPost.title }}]({{ blogPost.link }})'
    String message = statement.getMessage();
    // Evaluate values within the message
    if (message.contains("{{") && message.contains("}}")) {
      // Find all of the expressions
      Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
      Matcher matcher = pattern.matcher(message);
      // Evaluate and replace the expressions with a result
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
        String expression = matcher.group(1).trim();
        LOG.debug("Expression found: " + expression);
        if ("verb".equals(expression)) {
          matcher.appendReplacement(sb, statement.getVerb());
        } else {
          try {
            JexlContext mapContext = new MapContext();
            for (String key : mappings.keySet()) {
              mapContext.set(key, mappings.get(key));
            }
            Object result = Expression.evaluate(mapContext, expression);
            String replacement = String.valueOf(result);
            matcher.appendReplacement(sb, replacement);
          } catch (Exception e) {
            LOG.error("Expression not replaced: " + expression);
          }
        }
      }
      matcher.appendTail(sb);
      message = sb.toString();
    }
    return message;
  }

}
