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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="sampleRows" class="java.util.ArrayList" scope="request"/>
<script src="${ctx}/javascript/jspreadsheet-4.11.1/jspreadsheet.min.js"></script>
<link rel="stylesheet" media="screen" href="${ctx}/javascript/jspreadsheet-4.11.1/jspreadsheet.css">
<script src="${ctx}/javascript/jsuites-4.17.5/jsuites.min.js"></script>
<link rel="stylesheet" media="screen" href="${ctx}/javascript/jsuites-4.17.5/jsuites.css">
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<style>
  #dataset-preview {
    font-size: 12px;
    max-width: calc(100vw - 280px);
  }
  .jexcel_content {
    padding-right: 5px;
  }
</style>
<div id="dataset-preview"></div>
<script>
  var colHeaders = [
    <c:choose>
      <c:when test="${empty dataset.fieldTitles}">
        {title:"Data",width:1000}
      </c:when>
      <c:otherwise>
        <c:forEach items="${dataset.fieldTitles}" var="columnName" varStatus="columnStatus">
        {
          title:"<c:out value="${js:escape(columnName)}" />"
          <c:if test="${fn:length(dataset.fieldTitles) == 1}">,
          width: 1000
          </c:if>
        }<c:if test="${!columnStatus.last}">, </c:if>
        </c:forEach>
      </c:otherwise>
    </c:choose>
  ];
  var data = [
    <c:forEach items="${sampleRows}" var="row" varStatus="rowStatus">
    [
      <c:forEach items="${row}" var="data" varStatus="dataStatus">
        <c:choose>
          <c:when test="${empty data}">
            " "<c:if test="${!dataStatus.last}">, </c:if>
          </c:when>
          <c:otherwise>
            "${js:escape(data)}"<c:if test="${!dataStatus.last}">,</c:if>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    ]<c:if test="${!rowStatus.last}">, </c:if>
    </c:forEach>
  ];
  var container = document.getElementById('dataset-preview');

  var table = jspreadsheet(container, {
    data: data,
    columns: colHeaders,
    defaultColAlign: 'left',
    defaultColWidth: 130,
    tableOverflow: true,
    tableWidth: "100%",
    tableHeight: "100%",
    editable: false
  });

  $(document).ready(function () {
    var rect = container.getBoundingClientRect(),
      scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
      scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    container.style.height = "calc(100vh - " + Math.round(rect.top + scrollTop + 50) + "px)";
    // table.setWidth(0, 800);
  });
</script>
