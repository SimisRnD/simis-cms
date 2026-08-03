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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="file" class="com.simisinc.platform.domain.model.cms.FileItem" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="versionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="canRestore" class="java.lang.String" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">
  Prior uploaded versions of <strong><c:out value="${file.title}" /></strong>. Each version's file is kept on the
  server, so restoring makes that version's content the current file again -- a new version entry is recorded for
  the restore itself.
</p>
<table class="unstriped">
  <thead>
    <tr>
      <th>Version</th>
      <th width="60" class="text-center">size</th>
      <th width="90" class="text-center">uploaded</th>
      <th>uploaded by</th>
      <th width="90" class="text-center">action</th>
    </tr>
  </thead>
  <tbody>
    <c:if test="${empty versionList}">
      <tr>
        <td colspan="5">No versions were found</td>
      </tr>
    </c:if>
    <c:forEach items="${versionList}" var="version">
      <c:set var="isCurrent" value="${version.fileServerPath eq file.fileServerPath}" />
      <tr>
        <td>
          <c:out value="${version.filename}" />
          <c:if test="${!empty version.version}"> (<c:out value="${version.version}" />)</c:if>
          <c:if test="${isCurrent}"> <span class="label small round success">current</span></c:if>
        </td>
        <td class="text-center" nowrap>
          <small><c:out value="${number:suffix(version.fileLength)}"/></small>
        </td>
        <td class="text-center" nowrap>
          <small><fmt:formatDate pattern="yyyy-MM-dd" value="${version.created}" /></small>
        </td>
        <td>
          <small><c:out value="${user:name(version.createdBy)}" /></small>
        </td>
        <td class="text-center" nowrap>
          <c:choose>
            <c:when test="${isCurrent}">
              &mdash;
            </c:when>
            <c:when test="${canRestore eq 'true'}">
              <a title="Restore this version" href="#" onclick="return confirmPostAction('Restore this version? It will become the current file. <c:out value="${js:escape(version.filename)}" /> will be used going forward.', '${widgetContext.uri}?widget=${widgetContext.uniqueId}&token=${userSession.formToken}&action=restore&fileId=${file.id}&fileVersionId=${version.id}');"><i class="fa fa-undo"></i></a>
            </c:when>
            <c:otherwise>
              &mdash;
            </c:otherwise>
          </c:choose>
        </td>
      </tr>
    </c:forEach>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
