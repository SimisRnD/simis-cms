<%--
  ~ Copyright 2026 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>A read-only view of the background job queue (JobRunr): how many jobs are in each state right now, and a list of the jobs in whichever state you select. <strong>SCHEDULED</strong> is waiting for its run time; <strong>ENQUEUED</strong> is waiting for a free worker; <strong>PROCESSING</strong> is running; <strong>FAILED</strong> exhausted its retries; <strong>SUCCEEDED</strong> finished normally. Two internal JobRunr states aren't shown -- a carbon-aware scheduling holdback state, and jobs that are soft-deleted pending permanent removal -- neither needs day-to-day monitoring.</p>
  <p>Job data is stored in the same Postgres database as the rest of the app (not in memory), so what you see here is the real, shared state across every running instance, and it survives restarts and redeploys.</p>
</div>
<c:choose>
  <c:when test="${storageProviderUnavailable}">
    <p>The background job scheduler has not started yet, so no queue data is available. Try reloading this page in a moment.</p>
  </c:when>
  <c:otherwise>
    <jsp:useBean id="stateCounts" class="java.util.LinkedHashMap" scope="request"/>
    <jsp:useBean id="jobList" class="java.util.ArrayList" scope="request"/>
    <jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
    <%-- State tiles double as the filter: each shows the current count for that state and links to
         show that state's job list. Only the 5 states an admin would monitor day-to-day are shown --
         see JobQueueDashboardWidget's FILTERABLE_STATES javadoc for AWAITING/DELETED being left out
         of Phase 1. --%>
    <div class="button-group tiny">
      <c:forEach items="${stateCounts}" var="entry">
        <c:url var="stateFilterUrl" value="${widgetContext.uri}">
          <c:param name="state" value="${entry.key}"/>
        </c:url>
        <c:choose>
          <c:when test="${selectedState eq entry.key}">
            <a href="${stateFilterUrl}" class="button primary"><c:out value="${entry.key}"/> (<c:out value="${entry.value}"/>)</a>
          </c:when>
          <c:otherwise>
            <a href="${stateFilterUrl}" class="button secondary"><c:out value="${entry.key}"/> (<c:out value="${entry.value}"/>)</a>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </div>
    <c:choose>
      <c:when test="${empty jobList}">
        <p>No jobs are currently in the <c:out value="${selectedState}"/> state.</p>
      </c:when>
      <c:otherwise>
        <table class="unstriped">
          <thead>
            <tr>
              <th>Job Type</th>
              <th>State</th>
              <th>Created</th>
              <th>Updated</th>
              <th>Id</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach items="${jobList}" var="job">
              <tr>
                <td><c:out value="${job.jobType}"/></td>
                <td>
                  <c:choose>
                    <c:when test="${job.state eq 'FAILED'}"><span class="label alert radius"><c:out value="${job.state}"/></span></c:when>
                    <c:when test="${job.state eq 'SUCCEEDED'}"><span class="label success radius"><c:out value="${job.state}"/></span></c:when>
                    <c:when test="${job.state eq 'PROCESSING'}"><span class="label warning radius"><c:out value="${job.state}"/></span></c:when>
                    <c:otherwise><span class="label secondary radius"><c:out value="${job.state}"/></span></c:otherwise>
                  </c:choose>
                </td>
                <td><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${job.createdAt}' />"><c:out value="${date:relative(job.createdAt)}" /></span></td>
                <td><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${job.updatedAt}' />"><c:out value="${date:relative(job.updatedAt)}" /></span></td>
                <td><small><c:out value="${job.id}"/></small></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
        <%@include file="../paging_control.jspf" %>
      </c:otherwise>
    </c:choose>
  </c:otherwise>
</c:choose>

<h5>Is a FAILED job a problem?</h5>
<div class="callout warning radius">
  <p>By the time a job reaches FAILED, JobRunr already retried it automatically (2 retries beyond the first attempt) -- it isn't a transient blip waiting to self-heal, something genuinely didn't work.</p>
  <p>This page opens on the FAILED filter automatically whenever any jobs are currently failed, so you don't have to know to look for it.</p>
  <p><strong>Usually fine to just note and move on:</strong> a single FAILED occurrence of a routine nightly cleanup/retention job (there are many -- audit log retention, session cleanup, analytics cleanup, and similar) will simply run again at its next scheduled time. One miss rarely matters.</p>
  <p><strong>Worth checking soon:</strong> a job tied to something a person is waiting on -- order processing, shipping updates, newsletter sending -- failing means that customer-facing outcome didn't happen this cycle.</p>
  <p><strong>Worth investigating now:</strong> the <em>same</em> job type failing across several consecutive scheduled runs, not just once. That's a persistent problem, not noise. Since most jobs read or write the database, a cluster of FAILED jobs appearing at the same time is often downstream of a database issue -- check the <a href="/admin/health-dashboard">System Health</a> page for the same time window.</p>
  <p>This page doesn't show the job's actual exception yet -- that requires checking the application logs for the failure.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>Job data lives in the same Postgres database as the app, not in each instance's memory. If you scale to more than one Azure App Service instance, JobRunr elects one instance as the coordinator so each recurring job still runs exactly once across the whole fleet, not once per instance -- and this page shows the true shared state regardless of which instance happens to serve your request.</p>
  <p>An instance can be marked <code>CMS_NODE_TYPE=web</code> to opt out of running the recurring job set entirely (useful if you ever split off a dedicated web-serving instance from a worker instance) -- this is an optional lever, not something you need to set for normal scaling.</p>
  <p>JobRunr ships its own separate dashboard; it's turned off in this deployment because its open-source edition has no built-in login, so it can't safely be exposed. This page is the intended way to monitor jobs here.</p>
  <p><strong>Short history is expected, not a bug:</strong> SUCCEEDED jobs are pruned after 1 hour, so this page shows recent activity rather than a long-running history -- that's a deliberate, low retention window for this deployment, not data loss.</p>
</div>
