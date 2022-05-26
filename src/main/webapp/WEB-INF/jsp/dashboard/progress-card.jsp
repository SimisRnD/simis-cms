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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="progressCard" class="com.simisinc.platform.domain.model.dashboard.ProgressCard" scope="request"/>
<jsp:useBean id="textColor" class="java.lang.String" scope="request"/>
<jsp:useBean id="subheaderColor" class="java.lang.String" scope="request"/>
<jsp:useBean id="progressColor" class="java.lang.String" scope="request"/>
<jsp:useBean id="remainderColor" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<script src="${ctx}/javascript/chartjs-3.7.1/chart.min.js"></script>
<style>
  .chart-overlay-text {
      position: absolute;
      top: 53%;
      left: 50%;
      transform: translate(-50%,-50%);
      margin:0;
      padding:0;
      font-size: 16px;
      font-weight: bold;
      text-align: center;
      display:none;
  }
  .chart-overlay-text.chart-${widgetContext.uniqueId} {
      color: <c:out value="${textColor}" />;
  }
  .chart-header-text.chart-${widgetContext.uniqueId} {
      font-weight: bold;
  }
  .chart-subheader-text.chart-${widgetContext.uniqueId} {
      color: <c:out value="${subheaderColor}" />;
  }
</style>
<div class="grid-x align-middle text-middle">
  <div class="small-4 cell" style="position:relative">
    <canvas id="myChart-${widgetContext.uniqueId}"></canvas>
    <c:if test="${progressCard.maxValue > 0}">
      <p id="text${widgetContext.uniqueId}" class="chart-overlay-text chart-${widgetContext.uniqueId}"><fmt:formatNumber value="${100 * (progressCard.progress / progressCard.maxValue)}" />%</p>
    </c:if>
  </div>
  <div class="auto cell padding-left-10">
    <c:if test="${!empty progressCard.label}">
      <p class="chart-subheader-text chart-${widgetContext.uniqueId} no-gap"><c:out value="${progressCard.label}" /></p>
    </c:if>
    <c:if test="${!empty progressCard.value}">
      <p class="chart-header-text chart-${widgetContext.uniqueId} no-gap"><c:out value="${progressCard.value}" /></p>
    </c:if>
    <p class="chart-subheader-text chart-${widgetContext.uniqueId} no-gap"><c:out value="${progressCard.maxLabel}" /></p>
    <p class="chart-header-text chart-${widgetContext.uniqueId} no-gap">${progressCard.progress}/${progressCard.maxValue}</p>
  </div>
  <c:if test="${!empty progressCard.link}">
    <div class="small-1 cell">
      <a href="<c:out value="${progressCard.link}" />"><i class="fa fa-2x fa-chevron-right"></i></a>
    </div>
  </c:if>
</div>
<script>
  const data${widgetContext.uniqueId} = {
    datasets: [
      {
        data: [${progressCard.progress}, ${progressCard.difference}],
        backgroundColor: [
          '<c:out value="${progressColor}" />',
          '<c:out value="${remainderColor}" />'
        ]
      }
    ]
  };
  let chartContext${widgetContext.uniqueId} = document.getElementById("myChart-${widgetContext.uniqueId}").getContext('2d');
  let myChart${widgetContext.uniqueId} = new Chart(chartContext${widgetContext.uniqueId}, {
    type: "doughnut",
    data: data${widgetContext.uniqueId},
    options: {
      responsive: true,
      events: []
    }
  });

  function updateFontSize${widgetContext.uniqueId}() {
    let value = Math.round($('#text${widgetContext.uniqueId}').closest('.cell').outerWidth()*.16);
    $("#text${widgetContext.uniqueId}").css({'font-size': value + 'px'});
  }

  $(document).ready(function() {
    updateFontSize${widgetContext.uniqueId}();
    $('#text${widgetContext.uniqueId}').fadeIn(200);
  });

  $(window).resize(function() {
    updateFontSize${widgetContext.uniqueId}();
  });
</script>