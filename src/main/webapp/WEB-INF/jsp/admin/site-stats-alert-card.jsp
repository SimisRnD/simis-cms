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
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="numberValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="severity" class="java.lang.String" scope="request"/>
<jsp:useBean id="link" class="java.lang.String" scope="request"/>
<%--
  severity is one of "critical" (Foundation's "alert" callout, red), "warning" (yellow), or "ok" (no
  color suffix -- the default neutral callout). It is computed in Java (SiteStatsWidget), not guessed
  from numberValue here, since what counts as concerning differs per metric (any locked account is a
  warning; zero bot sessions is not).
--%>
<c:set var="severityClass" value="${severity eq 'critical' ? 'alert' : (severity eq 'warning' ? 'warning' : '')}"/>
<div class="callout radius ${fn:escapeXml(severityClass)}" style="height: 100%; margin: 0;">
  <c:choose>
    <c:when test="${!empty link}">
      <a href="<c:out value="${link}" />" style="color: inherit; text-decoration: none; display: block;">
        <p class="statistic-card-value no-gap" style="font-size: 32px; font-weight: bold; line-height: 1;">
          <fmt:formatNumber value="${numberValue}" />
        </p>
        <p class="no-gap"><c:out value="${title}"/></p>
      </a>
    </c:when>
    <c:otherwise>
      <p class="statistic-card-value no-gap" style="font-size: 32px; font-weight: bold; line-height: 1;">
        <fmt:formatNumber value="${numberValue}" />
      </p>
      <p class="no-gap"><c:out value="${title}"/></p>
    </c:otherwise>
  </c:choose>
</div>
