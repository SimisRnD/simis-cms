<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="botIdentityStatsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="optionsList" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="currentValue" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
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
      <th>Bot</th>
      <th class="text-center">Sessions</th>
      <th>First Seen</th>
      <th>Last Seen</th>
      <th>Top Page</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${botIdentityStatsList}" var="stats">
    <tr>
      <td><c:out value="${stats.identity}" /></td>
      <td class="text-center"><fmt:formatNumber value="${stats.sessionCount}" /></td>
      <td><c:out value="${stats.firstSeen}" /></td>
      <td><c:out value="${stats.lastSeen}" /></td>
      <td>
        <c:if test="${!empty stats.topPage}">
          <c:out value="${stats.topPage}" /> (<fmt:formatNumber value="${stats.topPageHits}" /> hits)
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty botIdentityStatsList}">
      <tr>
        <td colspan="5">Data was not found</td>
      </tr>
    </c:if>
  </tbody>
</table>
</div>
<c:if test="${!empty optionsList}">
<script nonce="${cspNonce}">
  var currentValue = '<c:out value="${empty currentValue ? optionsList.entrySet().toArray()[0].value : currentValue}"/>';

  function escapeHtml${widgetContext.uniqueId}(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : text;
    return div.innerHTML;
  }

  function buildItemRow(item) {
    var topPage = item.topPage
        ? escapeHtml${widgetContext.uniqueId}(item.topPage) + " (" + parseFloat(item.topPageHits).toLocaleString() + " hits)"
        : "";
    return "<tr><td>" + escapeHtml${widgetContext.uniqueId}(item.identity) + "</td>" +
        "<td class=\"text-center\">" + parseFloat(item.sessionCount).toLocaleString() + "</td>" +
        "<td>" + escapeHtml${widgetContext.uniqueId}(item.firstSeen) + "</td>" +
        "<td>" + escapeHtml${widgetContext.uniqueId}(item.lastSeen) + "</td>" +
        "<td>" + topPage + "</td></tr>";
  }

  function query${widgetContext.uniqueId}(value) {
    $.ajax({
      url: '${widgetContext.uri}?widget=${widgetContext.uniqueId}&action=get&value=' + value + '&token=${userSession.formToken}',
      type: 'GET',
      dataType: 'json',
      cache: false,
      timeout: 5000
    }).done(function(data) {
      $("#table${widgetContext.uniqueId} tbody").remove();
      var items = [];
      $.each(data, function (key, item) {
        items.push(buildItemRow(item));
      });
      $('<tbody/>', {
        html: items.join('')
      }).appendTo('#table${widgetContext.uniqueId}');
    });
  }

  function update${widgetContext.uniqueId}(value) {
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
