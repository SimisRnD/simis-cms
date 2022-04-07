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
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="contentHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="wiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPage" class="com.simisinc.platform.domain.model.cms.WikiPage" scope="request"/>
<jsp:useBean id="wikiLinkPrefix" class="java.lang.String" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/prism-1.15.0/prism.css">
<script src="${ctx}/javascript/prism-1.15.0/prism.min.js"></script>
<c:choose>
  <c:when test="${wiki.startingPage eq wikiPage.id && !empty title}">
    <h4 class="no-gap"><c:out value="${title}" /></h4>
  </c:when>
  <c:otherwise>
    <h4 class="no-gap"><c:out value="${wikiPage.title}" /></h4>
  </c:otherwise>
</c:choose>
<p class="subheader no-gap">
  <small>
    <c:out value="${user:name(wikiPage.modifiedBy)}"/> edited this page <c:out value="${date:relative(wikiPage.modified)}" />
    <c:if test="${wiki.startingPage ne wikiPage.id}">
      <br /><a href="${wikiLinkPrefix}/Home"><i class="fa fa-angle-double-left"></i> Back to Home</a>
    </c:if>
  </small>
</p>
<hr />
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty wikiPage.uniqueId}">
    <div class="platform-content-editor"><a class="hollow button small secondary" href="${ctx}/wiki-editor?wikiUniqueId=${wiki.uniqueId}&pageUniqueId=${wikiPage.uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a></div>
  </c:if>
  <div class="markdown-body">${contentHtml}</div>
</div>
