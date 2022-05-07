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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="webPageTemplateList" class="java.util.ArrayList" scope="request"/>
<div class="grid-container">
  <form name="templateForm${widgetContext.uniqueId}" method="post">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
    <input type="hidden" name="token" value="${userSession.formToken}" />
    <%-- Form specific --%>
    <input type="hidden" name="webPage" value="${webPage.link}" />
    <input type="hidden" id="templateId" name="templateId" value="-1" />
    <input type="hidden" id="templateUniqueId" name="templateUniqueId" value="-1" />
    <c:if test="${!empty webPage.link && webPage.link ne '/'}">
      <h4>Set a web page title for this page</h4>
      <input type="text" name="title" value="<c:out value="${webPage.title}" />" autofocus="autofocus" />
    </c:if>
    <h4>Choose a template for this page</h4>
    <c:if test="${empty webPageTemplateList}">
      <p>No templates were found.</p>
    </c:if>
    <c:set var="currentCategory" scope="request" value="---"/>
    <c:set var="categoryOpen" scope="request" value="false"/>
    <c:forEach items="${webPageTemplateList}" var="template" varStatus="status">
      <c:if test="${template.category ne currentCategory}">
        <c:if test="${categoryOpen eq 'true'}">
          </div>
        </c:if>
        <c:set var="categoryOpen" scope="request" value="true"/>
        <h5 class="margin-top-30"><c:out value="${fn:toUpperCase(template.category)}" /></h5>
        <div class="grid-x grid-margin-x">
      </c:if>
      <div class="small-6 medium-4 large-3 cell">
        <div class="template cell card" onclick="mySubmit(${template.id},${template.uniqueId})">
          <c:choose>
            <c:when test="${!empty template.imagePath}">
              <img src="${ctx}/images/templates/${url:encodeUri(template.imagePath)}">
            </c:when>
            <c:otherwise>
              <img src="${ctx}/images/templates/Blank.png">
            </c:otherwise>
          </c:choose>
          <div class="card-section">
            <p>
              <small><c:out value="${template.name}"/></small>
            </p>
          </div>
        </div>
      </div>
      <c:set var="currentCategory" scope="request" value="${template.category}"/>
    </c:forEach>
    <c:if test="${categoryOpen eq 'true'}">
      </div>
    </c:if>
  </form>
  <c:choose>
    <c:when test="${!empty returnPage}">
      <a href="${returnPage}" class="button radius secondary">Cancel</a>
    </c:when>
    <c:when test="${!empty webPage.link}">
      <a href="${ctx}${webPage.link}" class="button radius secondary">Cancel</a>
    </c:when>
    <c:otherwise>

    </c:otherwise>
  </c:choose>
</div>
<script>
  function mySubmit(templateId, templateUniqueId) {
    <%-- Post to the url --%>
    document.getElementById("templateId").value = templateId;
    document.getElementById("templateUniqueId").value = templateUniqueId;
    document.templateForm${widgetContext.uniqueId}.submit();
  }
</script>
