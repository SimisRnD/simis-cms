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
<jsp:useBean id="content" class="com.simisinc.platform.domain.model.cms.Content" scope="request"/>
<jsp:useBean id="versionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="userMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">
  Prior published versions of <code><c:out value="${content.uniqueId}" /></code>. Restoring a version
  loads it into the draft slot for review -- it will not go live until it is submitted, approved, and
  published again.
</p>
<c:if test="${fn:length(versionList) ge 2}">
  <form method="get" class="margin-bottom-10">
    <input type="hidden" name="uniqueId" value="<c:out value="${content.uniqueId}"/>" />
    <div class="grid-x grid-margin-x align-bottom">
      <div class="cell medium-4">
        <label>Compare version
          <select name="compareFrom">
            <c:forEach items="${versionList}" var="version">
              <option value="${version.id}"<c:if test="${compareFromId eq version.id}"> selected</c:if>>
                <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${version.publishedAt}" />
              </option>
            </c:forEach>
          </select>
        </label>
      </div>
      <div class="cell medium-4">
        <label>...against version
          <select name="compareTo">
            <c:forEach items="${versionList}" var="version">
              <option value="${version.id}"<c:if test="${compareToId eq version.id}"> selected</c:if>>
                <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${version.publishedAt}" />
              </option>
            </c:forEach>
          </select>
        </label>
      </div>
      <div class="cell medium-4">
        <input type="submit" class="button radius secondary" value="Compare" />
      </div>
    </div>
  </form>
</c:if>
<c:if test="${!empty diffResult}">
  <div class="callout">
    <h6>Word-level diff</h6>
    <c:choose>
      <c:when test="${diffResult.truncated}">
        <p>This comparison is too large to diff word-by-word and was not shown.</p>
      </c:when>
      <c:otherwise>
        <p class="content-version-diff">${diffResult.html}</p>
      </c:otherwise>
    </c:choose>
  </div>
</c:if>
<table class="unstriped">
  <thead>
    <tr>
      <th>Published</th>
      <th>Approved By</th>
      <th>Release Reference</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${versionList}" var="version">
      <c:set var="approver" value="${userMap[version.approvedBy]}" />
      <tr>
        <td><fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${version.publishedAt}" /></td>
        <td>
          <c:choose>
            <c:when test="${!empty approver}"><c:out value="${approver.fullName}" /></c:when>
            <c:otherwise>&mdash;</c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${!empty version.releaseReference}"><c:out value="${version.releaseReference}" /></c:when>
            <c:otherwise>&mdash;</c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <form method="post" action="${widgetContext.uri}" onsubmit="return confirm('Restore this version to the draft slot? It will need to be reviewed and published again to go live.');">
            <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
            <input type="hidden" name="token" value="${userSession.formToken}" />
            <input type="hidden" name="action" value="restore" />
            <input type="hidden" name="uniqueId" value="<c:out value="${content.uniqueId}"/>" />
            <input type="hidden" name="contentVersionId" value="${version.id}" />
            <button type="submit" class="button tiny radius secondary" title="Restore to draft"><i class="fa fa-undo"></i></button>
          </form>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty versionList}">
      <tr>
        <td colspan="4">No prior versions yet -- one is recorded each time this content is published over an existing draft.</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
