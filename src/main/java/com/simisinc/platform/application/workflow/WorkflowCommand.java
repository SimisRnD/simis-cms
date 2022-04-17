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

package com.simisinc.platform.application.workflow;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.work.Expression;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.WorkContext;

/**
 * Methods for workflow values
 *
 * @author matt rajkowski
 * @created 4/6/2021 9:55 AM
 */
public class WorkflowCommand {

  private static Log LOG = LogFactory.getLog(WorkflowCommand.class);

  public static String getValue(WorkContext workContext, TaskContext taskContext, Object property) {
    LOG.debug("Checking property: " + property);
    if (property == null) {
      return null;
    }
    String result = (String) property;
    if (!result.contains("{{") || !result.contains("}}")) {
      return result;
    }
    result = String.valueOf(Expression.evaluate(workContext, taskContext, (String) property));
    LOG.debug("Returning value: " + result);
    return result;
  }

  public static long getValueAsLong(WorkContext workContext, TaskContext taskContext, Object property) {
    String value = getValue(workContext, taskContext, property);
    if (StringUtils.isNumeric(value)) {
      return Long.parseLong(value);
    }
    return -1;
  }

  public static boolean getValueAsBoolean(WorkContext workContext, TaskContext taskContext, Object property) {
    String value = getValue(workContext, taskContext, property);
    return ("true".equals(value));
  }
}
