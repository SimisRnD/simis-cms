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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="socialMediaLinkList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped stack">
  <thead>
    <tr>
      <th width="30"></th>
      <th>Platform</th>
      <th>URL</th>
      <th width="60">Order</th>
      <th width="60"></th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${socialMediaLinkList}" var="record">
    <tr>
      <td><i class="fa ${fn:escapeXml(record.iconClass)}"></i></td>
      <td><c:out value="${record.platformName}" /></td>
      <td><a href="<c:out value="${record.url}"/>" target="_blank" rel="noopener noreferrer"><c:out value="${record.url}" /></a></td>
      <td><c:out value="${record.linkOrder}" /></td>
      <td nowrap="true">
        <a href="${ctx}/admin/social-media-settings?socialMediaLinkId=${record.id}"><i class="fa fa-pencil"></i></a>
        <a href="#" data-confirm-post="Are you sure you want to remove <c:out value="${record.platformName}" />?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&socialMediaLinkId=${record.id}"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty socialMediaLinkList}">
      <tr>
        <td colspan="5">No social media links have been added yet</td>
      </tr>
    </c:if>
  </tbody>
</table>
<p class="help-text page-help">These links show as icons in the site footer, in the order set above; the icon for each is picked automatically from the platform name.</p>
