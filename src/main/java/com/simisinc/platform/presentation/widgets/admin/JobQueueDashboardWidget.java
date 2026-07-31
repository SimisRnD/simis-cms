/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.admin;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.Page;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.OffsetBasedPageRequest;

import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Admin-only, read-only view of the background job queue (JobRunr): current counts per state, and a
 * paginated list of the jobs sitting in a selected state (issue #464, Phase 1). Retry/cancel actions
 * and a dedicated dead-letter-queue view are deliberately out of scope here -- see the phased scoping
 * already recorded on the issue -- so this widget has no post() at all.
 *
 * <p>JobRunr's own {@link Page} is offset/limit based, not the page-number-based
 * {@link DataConstraints} the rest of this codebase's list widgets use for
 * {@code paging_control.jspf}. Rather than teach the JSP a second pagination model, this widget
 * adapts one into the other: it asks JobRunr for the page of jobs it needs, then builds a
 * {@link DataConstraints} from that {@link Page}'s total/offset/limit so job-queue-dashboard.jsp can
 * reuse the standard paging control unchanged.
 *
 * @author SimIS
 * @created 7/30/2026
 */
public class JobQueueDashboardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/job-queue-dashboard.jsp";

  private static final int PAGE_SIZE = 25;

  // The states an admin can filter the job list to. AWAITING (carbon-aware scheduling holdback) and
  // DELETED (soft-deleted, pending permanent removal) are internal JobRunr bookkeeping states rather
  // than something day-to-day queue monitoring needs its own view for, so Phase 1 leaves them out of
  // both the count tiles and the filter -- consistent with deferring the dead-letter-queue view.
  static final List<StateName> FILTERABLE_STATES = List.of(
      StateName.SCHEDULED, StateName.ENQUEUED, StateName.PROCESSING, StateName.FAILED, StateName.SUCCEEDED);

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    StorageProvider storageProvider = SchedulerManager.getStorageProvider();
    if (storageProvider == null) {
      // Defensive only: the scheduler starts at application startup, well before this admin page
      // could be reached, and StorageProvider stays set until shutdown() runs.
      context.getRequest().setAttribute("storageProviderUnavailable", true);
      context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
      context.getRequest().setAttribute("title", context.getPreferences().get("title"));
      context.setJsp(JSP);
      return context;
    }

    loadDashboardData(context, storageProvider);

    context.setJsp(JSP);
    return context;
  }

  private void loadDashboardData(WidgetContext context, StorageProvider storageProvider) {

    Map<String, Long> stateCounts = loadStateCounts(storageProvider);
    context.getRequest().setAttribute("stateCounts", stateCounts);

    StateName selectedState = resolveSelectedState(context, stateCounts);
    context.getRequest().setAttribute("selectedState", selectedState.name());

    int page = context.getParameterAsInt("page", 1);
    if (page < 1) {
      page = 1;
    }
    long offset = (long) (page - 1) * PAGE_SIZE;
    Page<Job> jobPage = storageProvider.getJobs(selectedState,
        new OffsetBasedPageRequest("updatedAt:DESC", offset, PAGE_SIZE));

    List<JobRow> jobList = new ArrayList<>();
    for (Job job : jobPage.getItems()) {
      jobList.add(new JobRow(job));
    }
    context.getRequest().setAttribute("jobList", jobList);

    // Bridge JobRunr's Page into this codebase's DataConstraints/paging_control.jspf (see class
    // javadoc). The filter state is carried through page links the same way AuditLogListWidget
    // carries its filters: a "recordPagingParams" request attribute appended by paging_control.jspf.
    DataConstraints recordPaging = new DataConstraints(page, PAGE_SIZE);
    long total = jobPage.getTotal() != null ? jobPage.getTotal() : 0L;
    recordPaging.setTotalRecordCount(total);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, recordPaging);
    context.getRequest().setAttribute("recordPagingParams", "state=" + selectedState.name());

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
  }

  private Map<String, Long> loadStateCounts(StorageProvider storageProvider) {
    JobStats stats = storageProvider.getJobStats();
    Map<String, Long> stateCounts = new LinkedHashMap<>();
    stateCounts.put(StateName.SCHEDULED.name(), nullToZero(stats.getScheduled()));
    stateCounts.put(StateName.ENQUEUED.name(), nullToZero(stats.getEnqueued()));
    stateCounts.put(StateName.PROCESSING.name(), nullToZero(stats.getProcessing()));
    stateCounts.put(StateName.FAILED.name(), nullToZero(stats.getFailed()));
    stateCounts.put(StateName.SUCCEEDED.name(), nullToZero(stats.getSucceeded()));
    return stateCounts;
  }

  /** The "state" request parameter when it names one of the filterable states; otherwise FAILED if
   * any jobs are currently failed (the state an admin most likely wants to see without knowing to
   * look for it), else ENQUEUED (the normal "waiting to run" state). */
  static StateName resolveSelectedState(WidgetContext context, Map<String, Long> stateCounts) {
    String requested = context.getParameter("state");
    if (requested != null) {
      for (StateName candidate : FILTERABLE_STATES) {
        if (candidate.name().equals(requested)) {
          return candidate;
        }
      }
    }
    Long failedCount = stateCounts.get(StateName.FAILED.name());
    if (failedCount != null && failedCount > 0) {
      return StateName.FAILED;
    }
    return StateName.ENQUEUED;
  }

  private static long nullToZero(Long value) {
    return value != null ? value : 0L;
  }

  /** A display-friendly adapter from JobRunr's {@link Job} (java.time.Instant timestamps, a nested
   * JobDetails object) to plain bean properties job-queue-dashboard.jsp can read with EL, including
   * {@code date:relative()} which -- like the rest of this codebase's date functions -- takes a
   * {@link Timestamp}, not an Instant. */
  public static class JobRow {

    private final String id;
    private final String jobType;
    private final String state;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;

    JobRow(Job job) {
      this.id = job.getId().toString();
      this.jobType = simpleJobType(job);
      this.state = job.getState().name();
      this.createdAt = Timestamp.from(job.getCreatedAt());
      this.updatedAt = Timestamp.from(job.getUpdatedAt());
    }

    private static String simpleJobType(Job job) {
      String className = job.getJobDetails().getClassName();
      String simpleName = className.substring(className.lastIndexOf('.') + 1);
      return simpleName + "." + job.getJobDetails().getMethodName();
    }

    public String getId() {
      return id;
    }

    public String getJobType() {
      return jobType;
    }

    public String getState() {
      return state;
    }

    public Timestamp getCreatedAt() {
      return createdAt;
    }

    public Timestamp getUpdatedAt() {
      return updatedAt;
    }
  }
}
