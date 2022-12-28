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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="fileItemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useViewer" class="java.lang.String" scope="request"/>
<jsp:useBean id="showLinks" class="java.lang.String" scope="request"/>
<jsp:useBean id="canEdit" class="java.lang.String" scope="request"/>
<jsp:useBean id="canDelete" class="java.lang.String" scope="request"/>
<jsp:useBean id="emptyMessage" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<c:if test="${empty fileItemList}">
  <c:out value="${emptyMessage}" />
</c:if>
<c:if test="${!empty fileItemList}">
  <ul>
  <c:forEach items="${fileItemList}" var="file" varStatus="status">
    <li>
      <c:choose>
        <c:when test="${showLinks eq 'false'}">
          <c:out value="${file.title}" />
        </c:when>
        <c:when test="${fn:toLowerCase(file.fileType) eq 'url'}">
          <a target="_blank" href="${ctx}/show/${item.uniqueId}/assets/view/${file.url}"><c:out value="${file.title}" /></a>
        </c:when>
        <c:when test="${fn:toLowerCase(file.fileType) eq 'video'}">
          <a target="_blank" href="${ctx}/show/${item.uniqueId}/assets/view/${file.url}"><c:out value="${file.title}" /></a>
        </c:when>
        <c:when test="${useViewer eq 'true' && fn:toLowerCase(file.fileType) eq 'pdf'}">
          <a target="_blank" href="${ctx}/show/${item.uniqueId}/assets/view/${file.url}"><c:out value="${file.title}" /></a>
          <small class="subheader">.pdf</small>
        </c:when>
        <c:otherwise>
          <a href="${ctx}/show/${item.uniqueId}/assets/file/${file.url}"><c:out value="${file.title}" /></a>
        </c:otherwise>
      </c:choose>
      <c:if test="${file.fileLength gt 0}">
        <small class="subheader"><c:out value="${number:suffix(file.fileLength)}"/></small>
      </c:if>
      <c:choose>
        <c:when test="${date:relative(file.created) eq 'just now'}">
          <span class="label small round success">new</span>
        </c:when>
        <c:when test="${date:relative(file.modified) eq 'just now'}">
          <span class="label small round primary">updated</span>
        </c:when>
      </c:choose>
      <c:if test="${canDelete eq 'true'}">
        <a title="Delete file" href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&fileId=${file.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(file.filename)}" />?');"><i class="fa fa-remove"></i></a>
      </c:if>
    </li>
  </c:forEach>
  </ul>
</c:if>
