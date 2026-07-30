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
<jsp:useBean id="latestChecks" class="java.util.ArrayList" scope="request"/>
<meta http-equiv="refresh" content="30">
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="text-right">
  <small class="subheader">This page refreshes automatically every 30 seconds.</small>
  <a href="#" onclick="return confirmPostAction('Run all health checks now?', '${widgetContext.uri}?command=runCheckNow&widget=${widgetContext.uniqueId}&token=${userSession.formToken}');" class="button tiny">
    <i class="fa fa-rotate"></i> Run Check Now
  </a>
</p>
<c:choose>
  <c:when test="${empty latestChecks}">
    <p>No health checks have run yet. The scheduled check runs every minute -- use "Run Check Now" above for an immediate reading.</p>
  </c:when>
  <c:otherwise>
    <table class="unstriped">
      <thead>
        <tr>
          <th>Service</th>
          <th>Status</th>
          <th>Response Time</th>
          <th>Last Checked</th>
          <th>Uptime (last <c:out value="${uptimeWindowHours}"/>h)</th>
          <th>Error</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${latestChecks}" var="check">
          <tr>
            <td><c:out value="${check.serviceLabel}"/></td>
            <td>
              <c:choose>
                <c:when test="${check.up}"><span class="label success radius">UP</span></c:when>
                <c:otherwise><span class="label alert radius">DOWN</span></c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${check.responseTimeMs}"/> ms</td>
            <td><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${check.checkedAt}' />"><c:out value="${date:relative(check.checkedAt)}" /></span></td>
            <td>
              <c:set var="uptime" value="${uptimeByService[check.serviceName]}"/>
              <c:choose>
                <c:when test="${empty uptime}">&#8212;</c:when>
                <c:otherwise><fmt:formatNumber value="${uptime}" maxFractionDigits="1"/>%</c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${check.errorMessage}"/></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </c:otherwise>
</c:choose>
<p class="small">
  Checks readiness for the two dependencies that can go unhealthy after startup (database, file store) --
  see the <code>/healthz</code> endpoint for the combined readiness probe used by load balancers and
  container orchestration. History is kept for 30 days.
</p>
