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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="versionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="userMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">
  Prior published versions of <a href="${ctx}${webPage.link}"><c:out value="${webPage.link}" /></a>.
  Restoring a version loads it into the draft slot for review -- it will not go live until you publish it again.
</p>
<table class="unstriped">
  <thead>
    <tr>
      <th>Published</th>
      <th>Author</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${versionList}" var="version">
      <c:set var="author" value="${userMap[version.publishedBy]}" />
      <tr>
        <td><fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${version.publishedAt}" /></td>
        <td>
          <c:choose>
            <c:when test="${!empty author}"><c:out value="${author.fullName}" /></c:when>
            <c:otherwise>&mdash;</c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <form method="post" action="${widgetContext.uri}" onsubmit="return confirm('Restore this version to the draft slot? You will need to publish it to make it live.');">
            <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
            <input type="hidden" name="token" value="${userSession.formToken}" />
            <input type="hidden" name="action" value="restore" />
            <input type="hidden" name="webPageId" value="${webPage.id}" />
            <input type="hidden" name="webPageVersionId" value="${version.id}" />
            <button type="submit" class="button tiny radius secondary" title="Restore to draft"><i class="fa fa-undo"></i></button>
          </form>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty versionList}">
      <tr>
        <td colspan="3">No prior versions yet -- one is recorded each time this page is published over an existing draft.</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
