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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="webPageTemplateList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:out value="${title}" /></h4>
</c:if>
<div class="callout primary radius">
  <p style="margin-bottom:0">
    Every template available when creating a new web page, to browse and plan ahead of time --
    these aren't clickable here. To actually use one, start a new page from
    <a href="${ctx}/admin/web-pages">Web Pages</a>; the template step there offers the same list.
  </p>
</div>
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
    <div class="cell card">
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
    </div>
  </div>
  <c:set var="currentCategory" scope="request" value="${template.category}"/>
</c:forEach>
<c:if test="${categoryOpen eq 'true'}">
  </div>
</c:if>
