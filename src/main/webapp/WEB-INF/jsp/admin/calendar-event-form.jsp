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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEvent" class="com.simisinc.platform.domain.model.cms.CalendarEvent" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${calendarEvent.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Name
    <input type="text" placeholder="Name of event" name="title" value="<c:out value="${calendarEvent.title}"/>">
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${blog.description}"/>">
  </label>
  <label>All day?
    <%--<input id="enabled" type="checkbox" name="allDay" value="true" <c:if test="${calendarEvent.allDay}">checked</c:if>/>--%>
    <div class="switch large">
      <input class="switch-input" id="allDay-yes-no" type="checkbox" name="allDay" value="true"<c:if test="${calendarEvent.allDay}"> checked</c:if>>
      <label class="switch-paddle" for="allDay-yes-no">
        <span class="switch-active" aria-hidden="true">Yes</span>
        <span class="switch-inactive" aria-hidden="true">No</span>
      </label>
    </div>
  </label>
  <c:choose>
    <c:when test="${!empty returnPage}">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><input type="submit" class="button radius success expanded" value="Save"/></p>
    </c:otherwise>
  </c:choose>
</form>
