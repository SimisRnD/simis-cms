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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="contentTabList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<div class="platform-content-container">
  <ul class="tabs" data-deep-link="true" data-update-history="true"<c:if test="${smudge eq 'true'}"> data-deep-link-smudge="true" data-deep-link-smudge-delay="500" data-deep-link-smudge-offset="8"</c:if> data-tabs id="deeplinked-tabs-${widgetContext.uniqueId}">
  <c:forEach items="${contentTabList}" var="contentTab" varStatus="tabStatus">
    <li class="tabs-title<c:if test="${tabStatus.first}"> is-active</c:if>"><a href="#<c:out value="${contentTab.linkId}" />" aria-selected="true"><c:out value="${contentTab.name}" /></a></li>
  </c:forEach>
  </ul>
  <div class="tabs-content" data-tabs-content="deeplinked-tabs-${widgetContext.uniqueId}">
  <c:forEach items="${contentTabList}" var="contentTab" varStatus="tabStatus">
    <div class="tabs-panel<c:if test="${tabStatus.first}"> is-active</c:if>" id="${contentTab.linkId}">
      <c:if test="${showEditor eq 'true'}">
        <div class="platform-content-editor"><a class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${contentTab.contentUniqueId}&returnPage=${returnPage}#${contentTab.linkId}"><i class="${font:fas()} fa-edit"></i></a></div>
      </c:if>
      <div class="platform-content">
        ${contentTab.html}
      </div>
    </div>
  </c:forEach>
  </div>
</div>
