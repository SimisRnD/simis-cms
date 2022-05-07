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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="playerList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="filterMap" class="java.util.LinkedHashMap" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-leaderboard.css?v=<%= VERSION %>" />
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<table class="leaderboard">
  <thead>
    <tr>
      <th style="text-align: left;">Rank</th>
      <th style="text-align: left;" width="100%">Player</th>
      <th style="text-align: right;">
      <c:if test="${!empty optionsList}">
        <form id="leaderboardForm" method="get">
          <select name="filter" id="filter" style="width: 160px;">
            <c:forEach items="${optionsList}" var="option" varStatus="status">
              <option value="${option.value}"<c:if test="${selectedFilter eq option.value}"> selected</c:if>><c:out value="${option.key}" /></option>
            </c:forEach>
          </select>
        </form>
      </c:if>
      </th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${playerList}" var="player" varStatus="status">
    <tr>
      <td align="center" class="leaderboard-rank">${status.count}</td>
      <td align="left" width="100%" class="leaderboard-name">
        <c:if test="${!empty player['IMAGE']}">
          <img class="leaderboard-photo" src="${player['IMAGE']}" />
        </c:if>
        ${player['NAME']}
      </td>
      <td class="leaderboard-points"><fmt:formatNumber value="${player['VALUE']}"/></td>
    </tr>
    </c:forEach>
  </tbody>
</table>
<script>
  document.getElementById("filter").onchange = function() {
    document.getElementById("leaderboardForm").submit();
  }
</script>