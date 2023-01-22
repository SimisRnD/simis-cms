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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="statisticsDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<script src="${ctx}/javascript/chartjs-3.9.1/chart.min.js"></script>
<canvas id="myChart-${widgetContext.uniqueId}" width="200" height="100"></canvas>
<script>
  var chartContext = document.getElementById("myChart-${widgetContext.uniqueId}").getContext('2d');
  var myChart = new Chart(chartContext, {
    type: "line",
    data: {
      labels: [
        <c:forEach items="${statisticsDataList}" var="data" varStatus="status">
        "${data.label}"<c:if test="${!status.last}">, </c:if>
        </c:forEach>
      ],
      datasets: [{
        label: "${js:escape(label)}",
        data: [
          <c:forEach items="${statisticsDataList}" var="data" varStatus="status">
          ${data.value}<c:if test="${!status.last}">, </c:if>
          </c:forEach>
        ],
        fill: "origin",
        backgroundColor: "rgba(75, 192, 192, 0.4)",
        borderColor: "rgb(75, 192, 192)",
        lineTension: 0.1
      }]
    },
    options: {
      legend: {
        display: false
      },
      scales: {
        yAxes: [{
          display: true,
          ticks: {
            suggestedMin: 0,
            suggestedMax: 10,
            precision:0
          }
        }]
      }
    }
  });
</script>