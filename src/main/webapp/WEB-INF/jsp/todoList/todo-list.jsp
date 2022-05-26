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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="todoList" class="java.util.LinkedHashMap" scope="request"/>
<%@include file="../page_messages.jspf" %>
<link rel="stylesheet" href="${ctx}/css/platform-todo-list.css?v=<%= VERSION %>" />
<style>
    .todo-list-card ul li {
        background-color: <c:out value="${themePropertyMap['theme.callout.secondary.backgroundColor']}" />;
        margin: 10px 0;
        padding: 10px;
    }
    .todo-list-card ul li.selected {
        background-color: <c:out value="${themePropertyMap['theme.callout.success.backgroundColor']}" />;
    }
    .todo-list-card ul li:hover {
        box-shadow: inset 0 0 0 3px <c:out value="${themePropertyMap['theme.button.primary.backgroundColor']}" />;
    }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<c:choose>
  <c:when test="${!empty todoList}">
    <div class="todo-list-card">
      <ul>
        <c:forEach items="${todoList}" var="todoItem" varStatus="status">
          <li id="${widgetContext.uniqueId}${status.index}"<c:if test="${todoItem.value eq 'true'}"> class="selected"</c:if>>
            <input id="${widgetContext.uniqueId}item${status.index}" class="${widgetContext.uniqueId}todoListItem" type="checkbox"<c:if test="${todoItem.value eq 'true'}"> checked</c:if>></input><label for="${widgetContext.uniqueId}item${status.index}"></label>
            <c:out value="${todoItem.key}"/>
          </li>
        </c:forEach>
      </ul>
    </div>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No items were found
    </p>
  </c:otherwise>
</c:choose>
<script>
  var elements = document.getElementsByClassName("${widgetContext.uniqueId}todoListItem");
  var myFunction = function(ev) {
    ev.target.parentNode.classList[ ev.target.checked ? 'add' : 'remove'] ('selected');
    // ajax to set the value
  };
  for (var i = 0; i < elements.length; i++) {
    elements[i].addEventListener('click', myFunction, false);
  }
</script>