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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.webhooks.DispatchWebhookDeliveriesCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.infrastructure.scheduler.WorkflowEngineJob;
import com.simisinc.platform.infrastructure.scheduler.webhooks.WebhookDispatchJob;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.playbook.Playbook;
import org.jeasy.flows.playbook.PlaybookManager;
import org.jeasy.flows.reader.YamlReader;
import org.jeasy.flows.work.Expression;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkStatus;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.BackgroundJobRequest;

import jakarta.servlet.ServletContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manager used for running synchronous and asynchronous workflows
 *
 * @author matt rajkowski
 * @created 3/20/21 11:30 AM
 */
public class WorkflowManager {

  public static final String EVENT_OBJECT = "event";
  public static final String SITE_OBJECT = "site";

  private static Log LOG = LogFactory.getLog(WorkflowManager.class);

  public static void startup(ServletContext context, String path) {
    // Verify the path and files
    if (path == null) {
      LOG.warn("Path is null");
    }

    Set<String> paths = context.getResourcePaths(path);
    if (paths == null || paths.isEmpty()) {
      LOG.warn("Paths is null or empty");
      return;
    }

    // Read the files
    Map<String, String> taskLibrary = new HashMap<>();
    for (String filePath : paths) {
      try (InputStream inputStream = context.getResourceAsStream(filePath)) {
        if (filePath.contains("-playbook") || filePath.contains("-workflow")) {
          // Add playbooks
          String yaml = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
          List<Playbook> playbookList = YamlReader.readPlaybooks(yaml);
          if (playbookList == null || playbookList.isEmpty()) {
            LOG.error("PlaybookList IS NULL OR EMPTY: " + filePath);
          } else {
            for (Playbook playbook : playbookList) {
              PlaybookManager.add(playbook);
              LOG.info("Added playbook: " + playbook.getId());
            }
          }
        } else if (filePath.contains("-task-library")) {
          // Add task definitions
          String yaml = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
          Map<String, String> tasks = YamlReader.readTaskLibrary(yaml);
          taskLibrary.putAll(tasks);
        } else {
          LOG.warn("Skipping file: " + filePath);
        }
      } catch (Exception e) {
        LOG.error("Could not read file: " + filePath, e);
      }
    }
    // Register the classes
    PlaybookManager.register(taskLibrary);
  }

  public static void triggerWorkflowForEvent(Event domainEvent) {
    // Start the background job that runs this event's playbook (email/history/etc steps), if
    // one exists for its domainEventType
    JobId jobId = BackgroundJobRequest.enqueue(new WorkflowEngineJob(domainEvent));
    if (LOG.isDebugEnabled()) {
      LOG.debug("WorkflowEngineJob Enqueue jobId: " + jobId.toString() + " at " + domainEvent.getOccurred() + ": " + domainEvent.getDomainEventType());
    }

    // Also fan this event out to matching webhook subscriptions (issue #418) -- unconditionally,
    // for every event that reaches this method, independent of whether a playbook exists for
    // domainEvent.getDomainEventType() or what steps it lists. A playbook is looked up by exact
    // event-type id and findAndRunWorkflow() bails out entirely with just a warning log when none
    // is found (see below), so making webhook delivery depend on that same lookup -- e.g. by only
    // wiring a `- webhook:` step into each *-workflows.yml playbook -- would silently exclude any
    // event that doesn't happen to have a playbook today, and any future event whose author didn't
    // think to add one. See DispatchWebhookDeliveriesCommand's and WebhookTask's javadoc for the
    // full rationale, and WebhookTask's javadoc specifically for why that class exists but is not
    // wired into any playbook YAML.
    JobId webhookJobId = BackgroundJobRequest.enqueue(new WebhookDispatchJob(domainEvent));
    if (LOG.isDebugEnabled()) {
      LOG.debug("WebhookDispatchJob Enqueue jobId: " + webhookJobId.toString() + " at " + domainEvent.getOccurred() + ": " + domainEvent.getDomainEventType());
    }
  }

  public static void findAndRunWorkflow(Event domainEvent) {
    // Validate the input
    if (domainEvent == null || StringUtils.isBlank(domainEvent.getDomainEventType())) {
      LOG.error("Domain event is empty or id is empty");
      return;
    }

    if (LOG.isDebugEnabled()) {
      // Introduce a pause for display
      try {
        TimeUnit.SECONDS.sleep(2);
      } catch (Exception e) {

      }
      LOG.debug("=======================================================================");
      LOG.debug("Executing workflow: " + domainEvent.getDomainEventType());
    }

    // Look for workflow that handles domainEvent.getId()
    Playbook playbook = PlaybookManager.getPlaybook(domainEvent.getDomainEventType());
    if (playbook == null) {
      LOG.warn("Playbook not found for domain event: " + domainEvent.getDomainEventType());
      return;
    }

    // The workflow will need a work context
    WorkContext workContext = new WorkContext();
    workContext.put(EVENT_OBJECT, domainEvent);

    // The workflow YAML can use a global site object
    Map<String, String> siteObject = new HashMap<>();
    addValue(siteObject, "name", LoadSitePropertyCommand.loadByName("site.name"));
    addValue(siteObject, "keyword", LoadSitePropertyCommand.loadByName("site.name.keyword"));
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String siteLogo = LoadSitePropertyCommand.loadByName("site.logo");
    addValue(siteObject, "url", siteUrl);
    if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(siteLogo)) {
      siteObject.put("logo", siteUrl + siteLogo);
    }
    workContext.put(SITE_OBJECT, siteObject);

    // Parse variables for the work context
    Map<String, Object> starterObjectMap = new LinkedHashMap<>();
    starterObjectMap.put(EVENT_OBJECT, domainEvent);
    Expression.applyVarExpressionsToWorkContext(playbook, workContext, starterObjectMap);

    if (LOG.isDebugEnabled()) {
      LOG.debug("-----------------------------------------------------------------------");
    }

    // Execute the playbook -- a step reporting FAILED (e.g. EmailTask's send() throwing) has to
    // propagate as a thrown exception here, or the enclosing JobRunr job
    // (WorkflowEngineJobRequestHandler, @Job(retries = 1)) never sees a failure to retry against
    // (issue #1124). findAndRunWorkflow() has exactly one caller -- that job's run() method --
    // so throwing on failure here is safe.
    WorkReport report = PlaybookManager.run(playbook, workContext);
    if (report.getStatus() == WorkStatus.FAILED) {
      // FAILED means two different things here, and telling them apart is the whole point.
      //
      // WhenTask returns FAILED when its condition is merely false -- that is how the library
      // says "skip this block". A playbook that branches on `when` therefore reports FAILED on
      // its ordinary path. form-submitted has three guarded blocks; a form with an address
      // configured and confirmation off leaves two of them false, so every successful submission
      // ended here. Throwing sent the whole playbook back through JobRunr (retries = 1), which
      // re-ran the block that had already sent, and the notification went out twice -- every
      // time, not intermittently. That is issue 1643.
      //
      // A first-party task that genuinely fails attaches an error (see TaskReports), so an
      // error-less FAILED is the library declining a guard and must not be retried. The throw is
      // kept for the real thing, which is what issue 1124 needed it for.
      if (report.getError() == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Workflow stopped early on a declined condition, which is not a failure: "
              + domainEvent.getDomainEventType());
        }
        return;
      }
      throw new RuntimeException("Workflow failed for event: " + domainEvent.getDomainEventType(), report.getError());
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("=======================================================================");
    }
  }

  private static void addValue(Map<String, String> map, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    map.put(name, value);
  }
}
