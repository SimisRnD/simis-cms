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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="subFolderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cardSize" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="controlId" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<style>
  .card${widgetContext.uniqueId} {
    position: relative;
    border: none;
    margin-bottom: 20px;
    height: 120px;
    background-color: #ffffff;
    background-position: center;
    background-repeat: no-repeat;
    background-size: cover;
    cursor: pointer;
  }
  .card${widgetContext.uniqueId} .card-content {
    display: table;
    position: absolute;
    bottom: 0;
    padding: 3px;
    margin: 0;
    width: 100%;
    background-color: black;
    background-color: rgba(10, 10, 10, 0.5);
    color: white;
    min-height: 30px;
  }
  .card${widgetContext.uniqueId} .card-content-inner {
    display: table-cell;
    vertical-align: middle;
    height: 45px;
    text-align: center;
  }
  .card${widgetContext.uniqueId} .card-content p {
    padding: 0;
    margin: 0;
    line-height: 1;
    font-size: smaller;
  }
</style>
<div class="platform-content-container">
  <div class="platform-content">
    <div class="grid-x grid-margin-x text-center align-stretch small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
      <c:forEach items="${subFolderList}" var="subFolder">
        <div class="card-container${widgetContext.uniqueId} cell">
          <div class="card${widgetContext.uniqueId}<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>"
               style="background-image:url('${ctx}/assets/view/${subFolder.posterFileItem.url}');" onclick="showAlbum${controlId}(${subFolder.id})">
            <div class="card-content">
              <div class="card-content-inner">
                <p><c:out value="${subFolder.name}"/></p>
              </div>
            </div>
          </div>
        </div>
      </c:forEach>
    </div>
  </div>
</div>
