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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="fileItem" class="com.simisinc.platform.domain.model.cms.FileItem" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="folderCategoryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h4>
  <i class="fa fa-folder-open-o"></i> <c:out value="${folder.name}" />
  <c:if test="${!empty subFolder.name}">
    <br />
    <i class="fa fa-folder-open-o"></i> <c:out value="${subFolder.name}" />
  </c:if>
</h4>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${fileItem.id}"/>
  <input type="hidden" name="folderId" value="${folder.id}"/>
  <input type="hidden" name="subFolderId" value="${subFolder.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  </c:if>
  <c:if test="${!empty folderCategoryList}">
    <div class="grid-x grid-margin-x">
      <div class="small-6 cell">
        <label>Category
          <select name="categoryId" id="categoryId">
            <option value="-1"></option>
            <c:forEach items="${folderCategoryList}" var="category" varStatus="status">
              <option value="${category.id}"><c:out value="${category.name}" /></option>
            </c:forEach>
          </select>
        </label>
      </div>
    </div>
  </c:if>
  <label>Display Name <span class="required">*</span>
    <input type="text" placeholder="Name" name="title" id="title" value="" required>
  </label>
  <label>URL <span class="required">*</span>
    <input type="text" placeholder="URL" name="filename" id="filename" value="" required>
  </label>
  <label>Summary
    <input type="text" placeholder="Summary" name="summary" id="summary" value="">
  </label>
  <c:choose>
    <c:when test="${!empty returnPage}">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><input type="submit" class="button radius success expanded" value="Save"/></p>
    </c:otherwise>
  </c:choose>
</form>
