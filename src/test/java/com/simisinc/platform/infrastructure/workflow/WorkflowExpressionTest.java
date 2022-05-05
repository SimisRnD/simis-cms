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

package com.simisinc.platform.infrastructure.workflow;

import com.simisinc.platform.application.workflow.WorkflowCommand;
import org.jeasy.flows.work.Expression;
import org.jeasy.flows.work.LogTask;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.WorkContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for workflow expression processing
 *
 * @author matt rajkowski
 * @created 4/30/2021 4:34 PM
 */
class WorkflowExpressionTest {

  @Test
  void expressionTest() {

    LogTask task = new LogTask();
    WorkContext workContext = new WorkContext();
    TaskContext taskContext = new TaskContext(task);

    Map<String, Object> order = new HashMap<>();
    order.put("live", false);
    order.put("uniqueId", "0000-0000-0000");

    taskContext.put("order", order);

    String subject1 = (String) Expression.evaluate(workContext, taskContext, "{{ !order.live ? \"TEST \" : \"\" }}New order # {{ order.uniqueId }}");
    String subject2 = WorkflowCommand.getValue(workContext, taskContext, "{{ !order.live ? \"TEST \" : \"\" }}New order # {{ order.uniqueId }}");
    Assertions.assertEquals("TEST New order # 0000-0000-0000", subject1);
    Assertions.assertEquals("TEST New order # 0000-0000-0000", subject2);
  }
}
