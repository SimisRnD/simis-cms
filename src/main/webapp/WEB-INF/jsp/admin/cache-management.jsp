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
<jsp:useBean id="cacheSummaryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="small">
  In-memory application caches (Caffeine) -- clearing a cache is immediate and does not require a
  restart, and the next request simply reloads what it needs. Hit/miss/eviction counts accumulate
  from the moment the application started, so they will read low right after a deploy.
</p>

<p>
  <a href="#" onclick="return confirmPostAction('Clear ALL caches? This cannot be undone.', '${widgetContext.uri}?command=clearAll&widget=${widgetContext.uniqueId}&token=${userSession.formToken}');" class="button alert">Clear All Caches</a>
</p>

<div style="overflow-x: auto">
<table class="unstriped">
  <thead>
    <tr>
      <th>Cache</th>
      <th>Entries</th>
      <th>Hits</th>
      <th>Misses</th>
      <th>Hit Rate</th>
      <th>Evictions</th>
      <th>Last Cleared</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${cacheSummaryList}" var="cacheSummary">
      <tr>
        <td><c:out value="${cacheSummary.name}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.estimatedSize}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.hitCount}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.missCount}" /></td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.hitCount + cacheSummary.missCount == 0}">&#8212;</c:when>
            <c:otherwise><fmt:formatNumber value="${cacheSummary.hitRate * 100}" maxFractionDigits="1" />%</c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.evictionCount > 0}"><span class="label warning radius"><fmt:formatNumber value="${cacheSummary.evictionCount}" /></span></c:when>
            <c:otherwise>0</c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.neverCleared}">&#8212;</c:when>
            <c:otherwise><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${cacheSummary.lastClearedAt}' />"><c:out value="${date:relative(cacheSummary.lastClearedAt)}" /></span></c:otherwise>
          </c:choose>
        </td>
        <td>
          <a href="#" onclick="return confirmPostAction('Clear the ${fn:escapeXml(cacheSummary.name)} cache?', '${widgetContext.uri}?command=clearCache&cache=${fn:escapeXml(cacheSummary.name)}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}');" class="button tiny secondary">Clear</a>
        </td>
      </tr>
    </c:forEach>
  </tbody>
</table>
</div>
