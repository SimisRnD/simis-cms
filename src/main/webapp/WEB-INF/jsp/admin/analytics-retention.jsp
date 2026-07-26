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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="retentionDays" class="java.lang.Integer" scope="request"/>
<jsp:useBean id="sessionsWithPii" class="java.lang.Long" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table>
  <tr>
    <th>Metric</th>
    <th class="text-right">Value</th>
  </tr>
  <tr>
    <td>Sessions with retained visitor PII (IP address, city, coordinates)</td>
    <td class="text-right"><fmt:formatNumber value="${sessionsWithPii}"/></td>
  </tr>
  <tr>
    <td>Retention window</td>
    <td class="text-right"><c:out value="${retentionDays}"/> days</td>
  </tr>
</table>
<p class="subheader">The nightly job scrubs PII from session records older than the retention window. Use the button below to run the scrub immediately.</p>
<form method="post">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="submit" class="button radius alert" value="Purge PII now" data-disable-on-submit="Purging..."/>
</form>
