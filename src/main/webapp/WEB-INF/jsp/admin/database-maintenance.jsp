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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="tableStatsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="indexStatsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="activeQueryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>Live introspection of PostgreSQL's own statistics catalogs -- everything here is a snapshot of right now, not a persisted history like System Health or the Job Queue. <strong>Tables</strong> shows size, estimated live/dead rows, and when each table was last vacuumed/analyzed (by autovacuum or manually), with a one-click, non-locking <strong>Vacuum</strong> action per table. <strong>Indexes</strong> is sorted least-used first, so a candidate-to-drop index surfaces at the top. <strong>Active Activity</strong> lists whatever is running against this database right now, excluding this page's own monitoring connection.</p>
  <p>This first slice is intentionally scoped to what's safe and needs no extra Postgres extension -- REINDEX, query-plan/slow-query analysis, and bloat estimation are left for later.</p>
</div>
<c:if test="${!empty overview}">
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-4 cell">
      <div class="callout secondary text-center">
        <span class="stat-label">Database Size</span>
        <h3><c:out value="${overview.sizePretty}" /></h3>
      </div>
    </div>
    <div class="small-12 medium-4 cell">
      <div class="callout secondary text-center">
        <span class="stat-label">Tables</span>
        <h3><c:out value="${overview.tableCount}" /></h3>
      </div>
    </div>
    <div class="small-12 medium-4 cell">
      <div class="callout secondary text-center">
        <span class="stat-label">Indexes</span>
        <h3><c:out value="${overview.indexCount}" /></h3>
      </div>
    </div>
  </div>
</c:if>

<h5>Tables</h5>
<div style="overflow-x: auto">
<table class="unstriped">
  <thead>
    <tr>
      <th>Table</th>
      <th>Rows (est.)</th>
      <th>Dead Rows (est.)</th>
      <th>Size</th>
      <th>Last Vacuum</th>
      <th>Last Analyze</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${tableStatsList}" var="tableStats">
      <tr>
        <td><c:out value="${tableStats.tableName}" /></td>
        <td><fmt:formatNumber value="${tableStats.liveRowEstimate}" /></td>
        <td>
          <c:choose>
            <c:when test="${tableStats.deadRowEstimate > 0}"><span class="label warning radius"><fmt:formatNumber value="${tableStats.deadRowEstimate}" /></span></c:when>
            <c:otherwise>0</c:otherwise>
          </c:choose>
        </td>
        <td><c:out value="${tableStats.totalSizePretty}" /></td>
        <td>
          <c:choose>
            <c:when test="${empty tableStats.lastVacuumAny}">&#8212;</c:when>
            <c:otherwise><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${tableStats.lastVacuumAny}' />"><c:out value="${date:relative(tableStats.lastVacuumAny)}" /></span></c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${empty tableStats.lastAnalyzeAny}">&#8212;</c:when>
            <c:otherwise><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${tableStats.lastAnalyzeAny}' />"><c:out value="${date:relative(tableStats.lastAnalyzeAny)}" /></span></c:otherwise>
          </c:choose>
        </td>
        <td>
          <a href="#" onclick="return confirmPostAction('Run VACUUM (ANALYZE) on ${fn:escapeXml(tableStats.tableName)}?', '${widgetContext.uri}?command=vacuumAnalyze&table=${fn:escapeXml(tableStats.tableName)}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}');" class="button tiny secondary">Vacuum</a>
        </td>
      </tr>
    </c:forEach>
  </tbody>
</table>
</div>

<h5>Indexes</h5>
<p class="small">Sorted by scan count, least-used first -- a 0-scan index on a table with real traffic may be a candidate to drop.</p>
<div style="overflow-x: auto">
<table class="unstriped">
  <thead>
    <tr>
      <th>Index</th>
      <th>Table</th>
      <th>Scans</th>
      <th>Size</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${indexStatsList}" var="indexStats">
      <tr>
        <td>
          <c:out value="${indexStats.indexName}" />
          <c:if test="${indexStats.unused}"> <span class="label alert radius">unused</span></c:if>
        </td>
        <td><c:out value="${indexStats.tableName}" /></td>
        <td><fmt:formatNumber value="${indexStats.scanCount}" /></td>
        <td><c:out value="${indexStats.sizePretty}" /></td>
      </tr>
    </c:forEach>
  </tbody>
</table>
</div>

<h5>Active Activity</h5>
<c:choose>
  <c:when test="${empty activeQueryList}">
    <p class="small">No active (non-idle) queries right now.</p>
  </c:when>
  <c:otherwise>
    <div style="overflow-x: auto">
    <table class="unstriped">
      <thead>
        <tr>
          <th>PID</th>
          <th>State</th>
          <th>Started</th>
          <th>Waiting On</th>
          <th>Application</th>
          <th>Query</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${activeQueryList}" var="activeQuery">
          <tr>
            <td><c:out value="${activeQuery.pid}" /></td>
            <td><c:out value="${activeQuery.state}" /></td>
            <td>
              <c:choose>
                <c:when test="${empty activeQuery.queryStart}">&#8212;</c:when>
                <c:otherwise><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${activeQuery.queryStart}' />"><c:out value="${date:relative(activeQuery.queryStart)}" /></span></c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${activeQuery.waitEventType}" /></td>
            <td><c:out value="${activeQuery.applicationName}" /></td>
            <td><code><c:out value="${activeQuery.query}" /></code></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
    </div>
  </c:otherwise>
</c:choose>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>Dead rows:</strong> some is completely normal -- Postgres doesn't reclaim space in place, autovacuum does that in the background. Worth a look when a table's dead-row count is large relative to its live rows <em>and</em> "Last Vacuum" is old or empty: that pattern means autovacuum isn't keeping up with how often that table is written to. The manual Vacuum button is a safe way to catch a table up right now, but if you find yourself using it repeatedly on the same table, that's a sign autovacuum's own settings need tuning for that table (a Postgres server-parameter change, not something to keep doing by hand from here).</p>
  <p><strong>Unused indexes:</strong> a 0-scan index isn't automatically wrong -- right after a restart or failover these counters reset to zero for every index, so "unused" only means something once it's stayed at 0 scans for a good stretch of real, normal traffic.</p>
  <p><strong>Active Activity:</strong> normally near-empty. A query that's been running a long time, or sitting in a wait state, is worth a look -- a long "Waiting On" duration usually means it's blocked behind another session holding a lock, not that it's simply slow.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>Azure Database for PostgreSQL exposes these same catalog views (<code>pg_stat_user_tables</code>, <code>pg_stat_user_indexes</code>, <code>pg_stat_activity</code>) unmodified, so this page works as-is there.</p>
  <p><strong>Stats reset on failover:</strong> if the server has high availability enabled, a failover to the standby resets every one of these counters -- dead-row counts, last-vacuum times, index scan counts all go back to a fresh baseline. Don't read a page full of zeros right after a failover as "nothing has ever run here"; give it time to accumulate real activity again.</p>
  <p><strong>VACUUM cost:</strong> the Vacuum action here is non-locking and safe to run against a live table, but it still uses real IO and CPU. On a Burstable-tier instance in particular, prefer running it against large tables during a quiet period rather than peak traffic.</p>
  <p>Query text in Active Activity is visible here because this app connects with a single database role for everything -- the app itself, background jobs, and this page all share it. Postgres normally hides other roles' query text from a non-superuser unless it's a member of <code>pg_monitor</code>; that only becomes relevant if a second database role is ever introduced.</p>
</div>
