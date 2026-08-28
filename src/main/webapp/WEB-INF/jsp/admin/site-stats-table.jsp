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
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="statisticsDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<jsp:useBean id="value" class="java.lang.String" scope="request"/>
<jsp:useBean id="optionsList" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="currentValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="asOfDate" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty optionsList}">
  <ul class="tabs" id="tabs${widgetContext.uniqueId}">
    <c:forEach items="${optionsList}" var="option" varStatus="status">
      <li id="val<c:out value="${option.value}"/>" class="tabs-title<c:if test="${(empty currentValue and status.first) or option.value eq currentValue}"> is-active</c:if>"><a href="#" class="js-updateStats${widgetContext.uniqueId}" data-value="<c:out value="${option.value}"/>"><c:out value="${option.key}"/></a></li>
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
      <td class="text-center">
        <c:choose>
          <%-- Most reports put a plain number here (Hits, Submissions, Searches...), but a few
               (avg-time-on-page, high/low-traffic-engagement) put pre-formatted display text like
               "33.8s" or "185 hits, 33.8s avg" -- fmt:formatNumber throws on that and, uncaught,
               takes down this entire page's render, not just this cell. --%>
          <c:when test="${text:isNumeric(data.value)}"><fmt:formatNumber value="${data.value}" /></c:when>
          <c:otherwise><c:out value="${data.value}" /></c:otherwise>
        </c:choose>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty statisticsDataList}">
      <tr>
        <td colspan="2">Data was not found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<c:if test="${!empty asOfDate}">
  <p class="text-right"><small>As of <c:out value="${asOfDate}" /></small></p>
</c:if>
</div>
<c:if test="${!empty optionsList}">
<script nonce="${cspNonce}">
  // Interval to update the highlighted tab data
  var currentValue = '<c:out value="${empty currentValue ? optionsList.entrySet().toArray()[0].value : currentValue}"/>';
  var updateIntervalFunction = function() {
    query${widgetContext.uniqueId}(currentValue);
  };
  // Immediately start polling
  // var updateInterval = setInterval(updateIntervalFunction, 10000);
  // Wait for the first query
  var updateInterval;

  // Escape a label for safe insertion into the table markup (labels can be user-provided, e.g. search
  // terms or referrers)
  function escapeHtml${widgetContext.uniqueId}(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : text;
    return div.innerHTML;
  }

  // Update the table data
  function buildItemRow(item) {
    return "<tr><td>" + escapeHtml${widgetContext.uniqueId}(item.label) + "</td><td class=\"text-center\">" + parseFloat(item.value).toLocaleString() + "</td></tr>";
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

  document.querySelectorAll(".js-updateStats${widgetContext.uniqueId}").forEach(function (el) {
    el.addEventListener("click", function (event) {
      event.preventDefault();
      update${widgetContext.uniqueId}(el.dataset.value);
    });
  });
</script>
</c:if>