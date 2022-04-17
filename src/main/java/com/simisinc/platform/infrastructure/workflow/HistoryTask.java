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

import com.simisinc.platform.application.xapi.XapiStatementCommand;
import com.simisinc.platform.application.workflow.WorkflowCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.xapi.XapiStatement;
import com.simisinc.platform.infrastructure.persistence.xapi.XapiStatementRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.work.*;

import java.sql.Timestamp;

import static com.simisinc.platform.infrastructure.workflow.WorkflowManager.EVENT_OBJECT;

/**
 * A workflow task to save a history record
 *
 * @author matt rajkowski
 * @created 4/29/21 5:32 PM
 */
public class HistoryTask implements Work {

  private static Log LOG = LogFactory.getLog(HistoryTask.class);

  // Work Context
  public static final String USER_VAR = "user";

  // Task Context
  public static final String MESSAGE = "message";
  public static final String ACTOR_ID = "actor-id";
  public static final String VERB = "verb";
  public static final String OBJECT = "object";
  public static final String OBJECT_ID = "object-id";

  @Override
  public WorkReport execute(WorkContext workContext, TaskContext taskContext) {

    // xAPI properties
    String message = (String) taskContext.get(MESSAGE);
    String actorIdValue = (String) taskContext.get(ACTOR_ID);
    String verb = (String) taskContext.get(VERB);
    String object = (String) taskContext.get(OBJECT);
    String objectIdValue = (String) taskContext.get(OBJECT_ID);

    // Validate the requirements
    if (StringUtils.isBlank(message)) {
      LOG.error("Message is required");
      return new DefaultWorkReport(WorkStatus.FAILED, workContext);
    }

    // Log the history
    LOG.debug("Checking: '" + message + "'");

    try {
      // Determine the referenced objects
      User user = (User) workContext.get(USER_VAR);
      if (user == null) {
        LOG.warn("User is null!");
      }
      Object linkedObject = workContext.get(object);
      if (linkedObject == null) {
        LOG.warn("Object is null!");
      }

      // Populate the values
      long userId = WorkflowCommand.getValueAsLong(workContext, taskContext, actorIdValue);
      long objectId = WorkflowCommand.getValueAsLong(workContext, taskContext, objectIdValue);

      // Save the statement
      XapiStatement statement = new XapiStatement();
      statement.setMessage(message);
      statement.setActorId(userId);
      statement.setVerb(verb);
      statement.setObject(object);
      statement.setObjectId(objectId);
      if (workContext.containsKey(EVENT_OBJECT)) {
        statement.setOccurredAt(new Timestamp(((Event) workContext.get(EVENT_OBJECT)).getOccurred()));
      }
      String messageSnapshot = XapiStatementCommand.populateMessage(statement, workContext.getMap());
      statement.setMessageSnapshot(messageSnapshot);
      XapiStatementRepository.save(statement);
      return new DefaultWorkReport(WorkStatus.COMPLETED, workContext);
    } catch (Exception e) {
      LOG.error("decode values", e);
    }
    return new DefaultWorkReport(WorkStatus.FAILED, workContext);
  }
}
