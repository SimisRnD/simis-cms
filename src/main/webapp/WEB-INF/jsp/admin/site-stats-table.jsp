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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="statisticsDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<jsp:useBean id="value" class="java.lang.String" scope="request"/>
<jsp:useBean id="optionsList" class="java.util.LinkedHashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty optionsList}">
  <ul class="tabs" id="tabs${widgetContext.uniqueId}">
    <c:forEach items="${optionsList}" var="option" varStatus="status">
      <li id="val<c:out value="${option.value}"/>" class="tabs-title<c:if test="${status.first}"> is-active</c:if>"><a href="javascript:update${widgetContext.uniqueId}('<c:out value="${option.value}"/>')"><c:out value="${option.key}"/></a></li>
    </c:forEach>
  </ul>
</c:if>
<div id="stats${widgetContext.uniqueId}">
<table class="unstriped" id="table${widgetContext.uniqueId}">
  <thead>
    <tr>
      <th><c:out value="${label}" /></th>
      <th class="text-center"><c:out value="${value}" /></th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${statisticsDataList}" var="data">
    <tr>
      <td><c:out value="${data.label}" /></td>
      <td class="text-center"><fmt:formatNumber value="${data.value}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty statisticsDataList}">
      <tr>
        <td colspan="2">Data was not found</td>
      </tr>
    </c:if>
  </tbody>
</table>
</div>
<c:if test="${!empty optionsList}">
<script>
  // Interval to update the highlighted tab data
  var currentValue = '<c:out value="${optionsList.entrySet().toArray()[0].value}"/>';
  var updateIntervalFunction = function() {
    query${widgetContext.uniqueId}(currentValue);
  };
  // Immediately start polling
  // var updateInterval = setInterval(updateIntervalFunction, 10000);
  // Wait for the first query
  var updateInterval;

  // Update the table data
  function buildItemRow(item) {
    return "<tr><td>" + item.label + "</td><td class=\"text-center\">" + parseFloat(item.value).toLocaleString() + "</td></tr>";
  }

  // Query the data
  function query${widgetContext.uniqueId}(value) {
    // Turn off the interval
    if (updateInterval) {
      clearInterval(updateInterval);
      updateInterval = null;
    }
    // Query the new data
    $.ajax({
      url: '${widgetContext.uri}?widget=${widgetContext.uniqueId}&action=get&value=' + value + '&token=${userSession.formToken}',
      type: 'GET',
      dataType: 'json',
      cache: false,
      // complete: function() {
      // },
      timeout: 5000
    }).done(function(data) {
      // Remove the old data
      $("#table${widgetContext.uniqueId} tbody").remove();
      // Build the new data
      var items = [];
      $.each(data, function (key, item) {
        items.push(buildItemRow(item));
      });
      // Add the new data
      $('<tbody/>', {
        html: items.join('')
      }).appendTo('#table${widgetContext.uniqueId}');
      // Turn on the interval
      updateInterval = setInterval(updateIntervalFunction, 10000);
    }).fail(function() {
      // Turn on the interval
      updateInterval = setInterval(updateIntervalFunction, 30000);
    });
  }

  function update${widgetContext.uniqueId}(value) {
    // Update the highlighted tab class
    $("#tabs${widgetContext.uniqueId} li").each(function(idx, li) {
      if (li.id === 'val' + value) {
        if (!li.matches('.is-active')) {
          li.className = li.className + ' is-active';
          currentValue = value;
        }
      } else {
        if (li.matches('.is-active')) {
          li.className = li.className.replace(/\s*\bis-active\b/, "");
        }
      }
    });
    query${widgetContext.uniqueId}(value);
  }
</script>
</c:if>