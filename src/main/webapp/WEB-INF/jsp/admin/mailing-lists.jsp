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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="mailingLists" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="service" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<table>
  <thead>
    <tr>
      <th>Name</th>
      <th width="100" class="text-center">Members</th>
      <th width="150" class="text-center">Last Sent</th>
      <th width="100" class="text-center">Visible?</th>
      <th width="100" class="text-center">Sync?</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${mailingLists}" var="mailingList">
    <tr>
      <td>
        <a href="${ctx}/admin/mailing-list-members?mailingListId=${mailingList.id}"><c:out value="${mailingList.title}" /></a>
        <%-- The id a page's Email Subscription widget points at with mailingListUniqueId (issue
             #1724). Shown here so it can be copied without opening the list's edit form -- it is
             fixed when the list is created and unaffected by any later rename. --%>
        <br /><small class="subheader"><code><c:out value="${mailingList.uniqueId}" /></code></small>
        <%--
        <c:if test="${!empty mailingList.description}">
          <br /><small class="subheader"><c:out value="${mailingList.description}" /></small>
        </c:if>
        --%>
      </td>
      <td class="text-center"><fmt:formatNumber value="${mailingList.memberCount}" /></td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${mailingList.lastEmailed}" /></td>
      <td class="text-center">
        <c:choose>
          <c:when test="${mailingList.showOnline}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label warning">No</span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${empty service}">
            <span class="label secondary">Not configured</span>
          </c:when>
          <c:otherwise>
            <span class="label primary"><c:out value="${service}" /></span>
          </c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <a href="${ctx}/admin/mailing-list?mailingListId=${mailingList.id}&returnPage=/admin/mailing-lists"><i class="${font:fas()} fa-edit"></i></a>
        <%-- Issue #1730: name the list the way the row above does (title) -- naming the internal
             key here meant an irreversible delete was confirmed against a name the admin may not
             recognise after a rename --%>
        <c:if test="${mailingList.memberCount lt 11}">
          <a href="#" data-confirm-post="Are you sure you want to delete <c:out value="${not empty mailingList.title ? mailingList.title : mailingList.name}" />?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&mailingListId=${mailingList.id}"><i class="fa fa-remove"></i></a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty mailingLists}">
      <tr>
        <td colspan="6">No mailing lists were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
