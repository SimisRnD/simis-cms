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
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.infrastructure.scheduler.WorkflowEngineJob;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jeasy.flows.playbook.Playbook;
import org.jeasy.flows.playbook.PlaybookManager;
import org.jeasy.flows.reader.YamlReader;
import org.jeasy.flows.work.Expression;
import org.jeasy.flows.work.WorkContext;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.BackgroundJobRequest;

import javax.servlet.ServletContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Description
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
    // Start the background job
    JobId jobId = BackgroundJobRequest.enqueue(new WorkflowEngineJob(domainEvent));
    if (LOG.isDebugEnabled()) {
      LOG.debug("WorkflowEngineJob Enqueue jobId: " + jobId.toString() + " at " + domainEvent.getOccurred() + ": " + domainEvent.getDomainEventType());
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

    // Execute the playbook
    PlaybookManager.run(playbook, workContext);

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
