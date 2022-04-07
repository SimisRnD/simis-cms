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

package com.simisinc.platform.infrastructure.scheduler;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/20/21 11:25 AM
 */
@NoArgsConstructor
public class WorkflowEngineJob implements JobRequest {

  @Getter
  @Setter
  private Event event = null;

  public WorkflowEngineJob(Event event) {
    this.event = event;
  }

  @Override
  public Class<WorkflowEngineJobRequestHandler> getJobRequestHandler() {
    return WorkflowEngineJobRequestHandler.class;
  }

  public static class WorkflowEngineJobRequestHandler implements JobRequestHandler<WorkflowEngineJob> {
    @Override
    @Job(name = "Run a workflow", retries = 1)
    public void run(WorkflowEngineJob jobRequest) {
      jobContext().saveMetadata("name", jobRequest.getEvent().getDomainEventType());
      WorkflowManager.findAndRunWorkflow(jobRequest.getEvent());
    }
  }
}
