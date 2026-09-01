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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="statisticsDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<jsp:useBean id="optionsList" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="currentValue" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty optionsList}">
  <ul class="tabs" id="tabs${widgetContext.uniqueId}">
    <c:forEach items="${optionsList}" var="option" varStatus="status">
      <li id="val<c:out value="${option.value}"/>-${widgetContext.uniqueId}" class="tabs-title<c:if test="${(empty currentValue and status.first) or option.value eq currentValue}"> is-active</c:if>"><a href="#" class="js-updateStats${widgetContext.uniqueId}" data-value="<c:out value="${option.value}"/>"><c:out value="${option.key}"/></a></li>
    </c:forEach>
  </ul>
</c:if>
<script src="${ctx}/javascript/chartjs-4.4.1/chart.umd.min.js"></script>
<%-- The canvas chart is not readable by assistive technology, so it is labeled and paired with an equivalent
     screen-reader-only data table (WCAG 2.1 SC 1.1.1 / 1.3.1; Section 508). --%>
<canvas id="myChart-${widgetContext.uniqueId}" width="200" height="100" role="img"
        aria-label="<c:out value="${not empty title ? title : label}"/> chart. The data follows in a table."></canvas>
<table class="show-for-sr" id="srTable${widgetContext.uniqueId}">
  <caption><c:out value="${not empty title ? title : label}"/> &ndash; data table</caption>
  <thead>
    <tr>
      <th scope="col">Category</th>
      <th scope="col"><c:out value="${label}"/></th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${statisticsDataList}" var="data">
      <tr>
        <th scope="row"><c:out value="${data.label}"/></th>
        <td><c:out value="${data.value}"/></td>
      </tr>
    </c:forEach>
  </tbody>
</table>
<script nonce="${cspNonce}">
  var chartContext${widgetContext.uniqueId} = document.getElementById("myChart-${widgetContext.uniqueId}").getContext('2d');
  var myChart${widgetContext.uniqueId} = new Chart(chartContext${widgetContext.uniqueId}, {
    type: "line",
    data: {
      labels: [
        <c:forEach items="${statisticsDataList}" var="data" varStatus="status">
        "${js:escape(data.label)}"<c:if test="${!status.last}">, </c:if>
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
      plugins: {
        legend: {
          display: false
        }
      },
      scales: {
        y: {
          display: true,
          ticks: {
            suggestedMin: 0,
            suggestedMax: 10,
            precision:0
          }
        }
      }
    }
  });
</script>
<c:if test="${!empty optionsList}">
<script nonce="${cspNonce}">
  // Range tabs. The chart instance and this widget's helpers are all suffixed with the widget's
  // unique id because an admin page renders several of these charts side by side and every one of
  // them contributes its own copy of this script to the same global scope.
  (function () {
    var chart = myChart${widgetContext.uniqueId};
    var tabs = document.getElementById("tabs${widgetContext.uniqueId}");
    var srTableBody = document.querySelector("#srTable${widgetContext.uniqueId} tbody");

    // Keep the screen-reader data table in step with the canvas. The canvas is inert to assistive
    // technology, so this table is the chart for those users (WCAG 2.1 SC 1.1.1) -- leaving it on
    // the first range while the picture changes would be worse than having no control at all.
    function renderScreenReaderTable(data) {
      if (!srTableBody) {
        return;
      }
      srTableBody.textContent = "";
      data.forEach(function (item) {
        var row = document.createElement("tr");
        var head = document.createElement("th");
        head.setAttribute("scope", "row");
        head.textContent = item.label;
        var cell = document.createElement("td");
        cell.textContent = item.value;
        row.appendChild(head);
        row.appendChild(cell);
        srTableBody.appendChild(row);
      });
    }

    function query(value) {
      $.ajax({
        url: '${widgetContext.uri}?widget=${widgetContext.uniqueId}&action=get&value=' + encodeURIComponent(value) + '&token=${userSession.formToken}',
        type: 'GET',
        dataType: 'json',
        cache: false,
        timeout: 5000
      }).done(function (data) {
        chart.data.labels = data.map(function (item) { return item.label; });
        chart.data.datasets[0].data = data.map(function (item) { return parseFloat(item.value); });
        chart.update();
        renderScreenReaderTable(data);
        // Highlight only once the new data is actually rendered -- moving it on click instead would
        // leave a failed request showing a tab that disagrees with the chart under it
        tabs.querySelectorAll("li").forEach(function (li) {
          li.classList.toggle("is-active", li.id === 'val' + value + '-${widgetContext.uniqueId}');
        });
      });
    }

    document.querySelectorAll(".js-updateStats${widgetContext.uniqueId}").forEach(function (el) {
      el.addEventListener("click", function (event) {
        event.preventDefault();
        query(el.dataset.value);
      });
    });
  })();
</script>
</c:if>
