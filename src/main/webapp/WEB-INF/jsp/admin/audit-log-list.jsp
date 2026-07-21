<%--
  ~ Copyright 2022 SimIS Inc.
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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="auditLogList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Category
        <select name="category">
          <option value="">Any category</option>
          <c:forEach items="${categoryList}" var="cat">
            <option value="<c:out value='${cat}'/>"<c:if test="${category eq cat}"> selected</c:if>><c:out value="${cat}"/></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-3">
      <label>Event type
        <input type="text" name="eventType" placeholder="e.g. user.disable" value="<c:out value='${eventType}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Outcome
        <select name="outcome">
          <option value="">Any outcome</option>
          <option value="success"<c:if test="${outcome eq 'success'}"> selected</c:if>>Success</option>
          <option value="failure"<c:if test="${outcome eq 'failure'}"> selected</c:if>>Failure</option>
        </select>
      </label>
    </div>
    <div class="cell medium-4">
      <label>Actor (email contains)
        <input type="text" name="actor" placeholder="username or email" value="<c:out value='${actor}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>From date
        <input type="date" name="fromDate" value="<c:out value='${fromDate}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>To date
        <input type="date" name="toDate" value="<c:out value='${toDate}'/>">
      </label>
    </div>
    <div class="cell medium-6">
      <label>&nbsp;</label>
      <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
      <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
    </div>
  </div>
</form>
<table class="unstriped hover">
  <thead>
    <tr>
      <th width="150">When</th>
      <th>Event</th>
      <th width="70">Outcome</th>
      <th>Actor</th>
      <th width="120">Source IP</th>
      <th>Target</th>
      <th>Details</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${auditLogList}" var="record">
    <tr>
      <td nowrap>
        <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${record.occurred}' />"><c:out value="${date:relative(record.occurred)}" /></span>
      </td>
      <td>
        <c:out value="${record.eventType}" /><br />
        <small class="subheader"><c:out value="${record.eventCategory}" /></small>
      </td>
      <td nowrap>
        <c:choose>
          <c:when test="${record.outcome eq 'success'}"><span class="label success radius">success</span></c:when>
          <c:when test="${record.outcome eq 'failure'}"><span class="label alert radius">failure</span></c:when>
          <c:otherwise><span class="label secondary radius"><c:out value="${record.outcome}"/></span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty record.actorUsername}"><c:out value="${record.actorUsername}" /></c:when>
          <c:otherwise><em class="subheader">(unknown)</em></c:otherwise>
        </c:choose>
      </td>
      <td nowrap><c:out value="${record.sourceIp}" /></td>
      <td>
        <c:if test="${!empty record.targetLabel or !empty record.targetType}">
          <c:out value="${record.targetLabel}" />
          <c:if test="${!empty record.targetType}"><br /><small class="subheader"><c:out value="${record.targetType}" /></small></c:if>
        </c:if>
      </td>
      <td class="break-word"><small><c:out value="${record.details}" /></small></td>
    </tr>
    </c:forEach>
    <c:if test="${empty auditLogList}">
      <tr>
        <td colspan="7">No audit records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
