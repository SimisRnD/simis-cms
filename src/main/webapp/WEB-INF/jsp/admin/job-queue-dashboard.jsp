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
