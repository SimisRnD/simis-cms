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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="cardList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<style>
  .card${widgetContext.uniqueId} { border: none; }
</style>
<div class="platform-content-container">
  <div class="platform-content">
    <div class="grid-x grid-margin-x align-center text-center align-middle small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
      <c:forEach items="${cardList}" var="card">
        <div class="card-container${widgetContext.uniqueId} cell">
          <div class="card${widgetContext.uniqueId}<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
            ${card}
          </div>
        </div>
      </c:forEach>
    </div>
  </div>
</div>
<c:if test="${!empty extraHTMLContent}">
  ${extraHTMLContent}
</c:if>