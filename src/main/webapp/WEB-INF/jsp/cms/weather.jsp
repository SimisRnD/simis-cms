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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<div class="platform-weather">
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:out value="${title}" /></h2>
  </c:if>
  <div class="grid-x grid-margin-x">
    <c:forEach items="${periods}" var="period">
      <div class="small-6 medium-3 cell text-center margin-bottom-20">
        <c:if test="${!empty period.iconUrl}">
          <img src="<c:out value="${period.iconUrl}"/>" alt="<c:out value="${period.shortForecast}"/>" width="60" height="60" loading="lazy" decoding="async" />
        </c:if>
        <p class="text-bold margin-bottom-5"><c:out value="${period.name}"/></p>
        <c:if test="${!empty period.temperatureUnit}">
          <p class="margin-bottom-5"><c:out value="${period.temperature}"/>&deg;<c:out value="${period.temperatureUnit}"/></p>
        </c:if>
        <p><small><c:out value="${period.shortForecast}"/></small></p>
      </div>
    </c:forEach>
  </div>
</div>
