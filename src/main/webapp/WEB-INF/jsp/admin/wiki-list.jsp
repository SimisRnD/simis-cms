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
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="wikiList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="wikiPageCount" class="java.util.HashMap" scope="request"/>
<c:if test="${userSession.hasRole('admin')}">
<script>
  function deleteWiki(wikiId) {
    if (!confirm("Are you sure you want to delete this wiki and all of its pages?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=' + wikiId;
  }
</script>
</c:if>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<a class="button small radius primary" href="${ctx}/admin/wiki?returnPage=/admin/wikis">Add a Wiki <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th width="75%">Name</th>
      <th width="25%">Unique Id</th>
      <th width="100" class="text-center"># of pages</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${wikiList}" var="wiki">
      <tr>
        <td>
          <c:out value="${wiki.name}" />
          <c:if test="${!wiki.enabled}"><span class="label warning">offline</span></c:if>
          <c:if test="${!empty wiki.description}">
            <br /><small class="subheader"><c:out value="${wiki.description}" /></small>
          </c:if>
        </td>
        <td>
          <small><c:out value="${wiki.uniqueId}" /></small>
        </td>
        <td class="text-center">
          <fmt:formatNumber value="${wikiPageCount[wiki.id]}" />
        </td>
        <td class="text-center">
          <a href="${ctx}/admin/wiki?wikiId=${wiki.id}&returnPage=/admin/wikis"><i class="fa fa-edit"></i></a>
          <c:if test="${userSession.hasRole('admin')}">
            <a href="javascript:deleteWiki(${wiki.id});"><i class="fa fa-remove"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty wikiList}">
      <tr>
        <td colspan="4">No wikis were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
