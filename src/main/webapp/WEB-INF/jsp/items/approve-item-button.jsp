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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="buttonClass" class="java.lang.String" scope="request"/>
<%@ taglib prefix="fn" uri="jakarta.tags.core" %>
<c:if test="${item.id gt 0}">
<c:url var="approveUrl" value="${widgetContext.uri}">
  <c:param name="action" value="approve"/>
  <c:param name="widget" value="${widgetContext.uniqueId}"/>
  <c:param name="token" value="${userSession.formToken}"/>
  <c:param name="itemUniqueId" value="${item.uniqueId}"/>
  <c:if test="${!empty returnPage}"><c:param name="returnPage" value="${returnPage}"/></c:if>
</c:url>
<a class="radius button <c:out value="${buttonClass}"/>" href="${approveUrl}" onclick="return confirm('Are you sure you want to approve <c:out value="${js:escape(item.name)}" />?');"><i class="fa fa-check"></i> <c:out value="${title}" /></a>
</c:if>