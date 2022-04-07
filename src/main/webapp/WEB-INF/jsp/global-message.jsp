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
<jsp:useBean id="messageType" class="java.lang.String" scope="request"/>
<jsp:useBean id="messageValue" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${'error' eq messageType}">
    <div class="callout radius alert">
      <p class="text-center"><c:out value="${messageValue}" /></p>
    </div>
  </c:when>
  <c:when test="${'warning' eq messageType}">
    <div class="callout radius warning">
      <p class="text-center"><c:out value="${messageValue}" /></p>
    </div>
  </c:when>
  <c:when test="${'success' eq messageType}">
    <div class="callout radius success">
      <p class="text-center"><c:out value="${messageValue}" /></p>
    </div>
  </c:when>
  <c:otherwise>
    <div class="callout radius">
      <p class="text-center"><c:out value="${messageValue}" /></p>
    </div>
  </c:otherwise>
</c:choose>
