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

package com.simisinc.platform.infrastructure.scheduler.cms;

import com.simisinc.platform.application.cms.SaveWebPageHitCommand;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.domain.model.cms.WebPageHit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Saves a web page hit
 *
 * @author matt rajkowski
 * @created 5/21/18 10:00 AM
 */
@NoArgsConstructor
public class RecordWebPageHitJob implements JobRequest {

  private static Log LOG = LogFactory.getLog(RecordWebPageHitJob.class);

  @Getter
  @Setter
  private WebPageHit webPageHit = null;

  public RecordWebPageHitJob(WebPageHit webPageHit) {
    this.webPageHit = webPageHit;
  }

  @Override
  public Class<RecordWebPageHitJobRequestHandler> getJobRequestHandler() {
    return RecordWebPageHitJobRequestHandler.class;
  }

  public static class RecordWebPageHitJobRequestHandler implements JobRequestHandler<RecordWebPageHitJob> {
    @Override
//    @Job(name = "Record a web page hit", retries = 1, mutex = "web-page-hit")
    @Job(name = "Record a web page hit", retries = 1)
    public void run(RecordWebPageHitJob jobRequest) {
      WebPageHitRepository.save(jobRequest.getWebPageHit());
    }
  }

  @Job(name = "Record web page hits")
  public static void execute() {
    WebPageHit webPageHit = null;
    int count = 0;
    while ((webPageHit = SaveWebPageHitCommand.getHitFromQueue()) != null) {
      WebPageHitRepository.save(webPageHit);
      ++count;
    }
    if (count > 0) {
      LOG.debug("Hits processed: " + count);
    }
  }
}
