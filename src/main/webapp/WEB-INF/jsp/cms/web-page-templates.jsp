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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
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
    <h2 class="h4">Set a web page title for this page</h2>
    <input type="text" name="title" placeholder="Give it a title..." value="<c:out value="${webPage.title}" />" autofocus="autofocus" />
    <label>Description (optional, shown in search results)
      <input type="text" name="description" placeholder="Describe it..." value="<c:out value="${webPage.description}" />" />
    </label>
    <p class="help-text">
      Once this page is saved, add it to the <a href="${ctx}/admin/sitemap">Navigation Menu</a> so visitors can find it -- a page isn't linked from anywhere, or included in on-site search results, until it's added there.
    </p>
    <h2 class="h4">Choose a template for this page</h2>
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
        <h3 class="h5 margin-top-30"><c:out value="${fn:toUpperCase(template.category)}" /></h3>
        <div class="grid-x grid-margin-x">
      </c:if>
      <div class="small-6 medium-4 large-3 cell">
        <button type="button" class="template cell card js-mySubmit" data-template-id="${template.id}" data-template-unique-id="${template.uniqueId}" aria-label="<c:out value="${template.name}"/>">
          <c:choose>
            <c:when test="${!empty template.imagePath}">
              <img src="${ctx}/images/templates/${url:encodeUri(template.imagePath)}" alt="">
            </c:when>
            <c:otherwise>
              <img src="${ctx}/images/templates/Blank.png" alt="">
            </c:otherwise>
          </c:choose>
          <div class="card-section">
            <p>
              <small><c:out value="${template.name}"/></small>
            </p>
          </div>
        </button>
      </div>
      <c:set var="currentCategory" scope="request" value="${template.category}"/>
    </c:forEach>
    <c:if test="${categoryOpen eq 'true'}">
      </div>
    </c:if>
  </form>
  <div class="button-container">
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
</div>
<script nonce="${cspNonce}">
  function mySubmit(templateId, templateUniqueId) {
    <%-- Post to the url --%>
    document.getElementById("templateId").value = templateId;
    document.getElementById("templateUniqueId").value = templateUniqueId;
    document.templateForm${widgetContext.uniqueId}.submit();
  }
  document.querySelectorAll(".js-mySubmit").forEach(function (el) {
    el.addEventListener("click", function () {
      mySubmit(el.dataset.templateId, el.dataset.templateUniqueId);
    });
  });
</script>
