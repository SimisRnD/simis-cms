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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="canEdit" class="java.lang.String" scope="request"/>
<jsp:useBean id="canDelete" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/clipboard-2.0.4/clipboard.min.js"></script>
<button class="button small primary radius float-left" data-open="formReveal${widgetContext.uniqueId}"><i class="fa fa-plus"></i> Add Sub-Folder</button>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Sub-Folder Name
      </th>
      <th width="110" class="text-center">action</th>
      <th width="110" class="text-center">start date</th>
      <th width="70" class="text-center">files</th>
    </tr>
  </thead>
  <tbody>
    <c:if test="${empty subFolderList}">
      <tr>
        <td colspan="4">No sub-folders were found</td>
      </tr>
    </c:if>
    <c:forEach items="${subFolderList}" var="subFolder">
    <tr>
      <td>
        <small>
          <i class="${font:fal()} fa-folder"></i>
          <a href="/admin/sub-folder-details?subFolderId=${subFolder.id}&folderId=${folder.id}"><c:out value="${subFolder.name}" /></a>
          <c:choose>
            <c:when test="${date:relative(subFolder.created) eq 'just now'}">
              <span class="label small round success">new</span>
            </c:when>
            <c:when test="${date:relative(subFolder.modified) eq 'just now'}">
              <span class="label small round primary">updated</span>
            </c:when>
          </c:choose>
        </small>
        <c:if test="${!empty subFolder.summary}">
          <br /><small><c:out value="${subFolder.summary}" /></small>
        </c:if>
      </td>
      <td>

      </td>
      <td class="text-center">
        <span data-tooltip class="top" title="Modified by <c:out value="${user:name(subFolder.modifiedBy)}" />">
          <small><fmt:formatDate pattern="yyyy-MM-dd" value="${subFolder.startDate}" /></small>
        </span>
      </td>
      <td class="text-center">
        <small><c:out value="${number:suffix(subFolder.fileCount)}"/></small>
      </td>
    </tr>
    </c:forEach>
  </tbody>
</table>
<%-- @todo add paging --%>

<%-- form --%>
<div class="reveal small" id="formReveal${widgetContext.uniqueId}" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4>New Sub-Folder</h4>
  <form id="subFolderForm${widgetContext.uniqueId}" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- Form --%>
    <input type="hidden" name="id" value="-1"/>
    <input type="hidden" name="currentFolderId" value="${folder.id}"/>
    <div class="grid-x grid-margin-x">
      <fieldset class="small-12 cell">
        <label>Sub-Folder Name <span class="required">*</span>
          <input type="text" placeholder="Give it a name..." name="name" value="" required>
        </label>
      </fieldset>
      <fieldset class="small-12 cell">
        <label>Summary
          <input type="text" placeholder="Describe it..." name="summary" value="">
        </label>
      </fieldset>
    </div>
    <label>Start Date
      <div class="input-group">
        <span class="input-group-label"><i class="fa fa-calendar"></i></span>
        <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="startDate${widgetContext.uniqueId}" name="startDate" value="">
      </div>
    </label>
    <script>
      $(function(){
        $('#startDate${widgetContext.uniqueId}').fdatepicker({
          format: 'mm-dd-yyyy hh:ii',
          disableDblClickSelection: true,
          pickTime: true
        });
      });
    </script>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Save" />
    </div>
  </form>
</div>
