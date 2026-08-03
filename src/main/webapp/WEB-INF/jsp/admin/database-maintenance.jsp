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
<p class="small">
  Live introspection of PostgreSQL's own statistics catalogs -- nothing here is persisted or historical.
  This first slice covers table/index size and activity monitoring plus a safe VACUUM (ANALYZE) trigger;
  REINDEX, query-plan analysis, and bloat estimation are intentionally not offered yet.
</p>
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
