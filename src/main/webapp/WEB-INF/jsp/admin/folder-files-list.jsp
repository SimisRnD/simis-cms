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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<%@ taglib prefix="folderCategory" uri="/WEB-INF/folder-category-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="folderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="subFolderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="folderCategoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="fileList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="canEdit" class="java.lang.String" scope="request"/>
<jsp:useBean id="canDelete" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/clipboard-2.0.4/clipboard.min.js"></script>
<%@include file="../page_messages.jspf" %>
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
  <a href="${ctx}/admin/file-form?subFolderId=${subFolder.id}&folderId=${folder.id}&returnPage=${widgetContext.uri}%3FsubFolderId=${subFolder.id}%26folderId=${folder.id}" class="button small primary radius float-left"><i class="fa fa-plus"></i> Add File Link</a>
</c:if>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Filename
      </th>
      <th width="110" class="text-center">action</th>
      <th width="50" class="text-center">size</th>
      <th width="60" class="text-center">uploaded</th>
      <th width="70" class="text-center">downloads</th>
    </tr>
  </thead>
  <tbody>
    <c:if test="${empty fileList}">
      <tr>
        <td colspan="5">No files were found</td>
      </tr>
    </c:if>
    <c:forEach items="${fileList}" var="file">
    <tr>
      <td>
        <c:if test="${fn:toLowerCase(file.fileType) eq 'image'}">
          <img class="image-left" width="200" src="${ctx}/assets/view/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}" />
        </c:if>
        <small>
          <a href="javascript:selectFile(${file.id});"><c:out value="${file.title}" /></a>
          <c:choose>
            <c:when test="${date:relative(file.created) eq 'just now'}">
              <span class="label small round success">new</span>
            </c:when>
            <c:when test="${date:relative(file.modified) eq 'just now'}">
              <span class="label small round primary">updated</span>
            </c:when>
          </c:choose>
          <c:if test="${file.version ne '1.0'}">(<c:out value="${file.version}" />)</c:if>
        </small>
        <c:if test="${file.categoryId gt 0}">
          <span class="label"><c:out value="${folderCategory:name(file.categoryId)}" /></span>
        </c:if>
        <c:if test="${file.summary ne file.filename}">
          <br />
          <c:choose>
            <c:when test="${fn:toLowerCase(file.fileType) eq 'url'}">
              <small><a target="_blank" href="<c:out value="${file.filename}" />"><c:out value="${file.filename}" /></a></small>
            </c:when>
            <c:otherwise>
              <small><c:out value="${file.filename}" /></small>
            </c:otherwise>
          </c:choose>
        </c:if>
        <c:if test="${!empty file.summary}">
          <br /><small><c:out value="${file.summary}" /></small>
        </c:if>
      </td>
      <td nowrap>
        <c:choose>
          <c:when test="${fn:toLowerCase(file.fileType) eq 'video'}">
            <a class="clipboard" title="Copy view link to clipboard" data-clipboard-text="${ctx}/assets/view/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><i class="fa fa-clipboard"></i></a>
          </c:when>
          <c:otherwise>
            <a class="clipboard" title="Copy download link to clipboard" data-clipboard-text="${ctx}/assets/file/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><i class="fa fa-clipboard"></i></a>
          </c:otherwise>
        </c:choose>
        <c:if test="${fn:toLowerCase(file.fileType) eq 'pdf' || fn:toLowerCase(file.fileType) eq 'image' || fn:toLowerCase(file.fileType) eq 'video'}">
          <a target="_blank" title="Open in new tab" href="${ctx}/assets/view/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><i class="fa fa-desktop"></i></a>
        </c:if>
        <a title="Download file" href="${ctx}/assets/file/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><i class="fa fa-download"></i></a>
        <c:if test="${canDelete eq 'true'}">
          <a title="Delete file" href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&fileId=${file.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(file.filename)}" />?');"><i class="fa fa-remove"></i></a>
        </c:if>
      </td>
      <td class="text-center" nowrap>
        <small><c:out value="${number:suffix(file.fileLength)}"/></small>
        <%--<small><c:out value="${file.fileType}" /></small><br />--%>
      </td>
      <td class="text-center" nowrap>
        <%--<small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${file.created}" /></small>--%>
        <span data-tooltip class="top" title="Uploaded by <c:out value="${user:name(file.modifiedBy)}" />">
          <small><fmt:formatDate pattern="yyyy-MM-dd" value="${file.modified}" /></small>
        </span>
      </td>
      <td class="text-center" nowrap>
        <small><c:out value="${number:suffix(file.downloadCount)}"/></small>
      </td>
    </tr>
    </c:forEach>
  </tbody>
</table>
<script>
  // ClipboardJS.isSupported()
  var clipboard = new ClipboardJS('.clipboard');
  clipboard.on('success', function(e) {
    console.info('Action:', e.action);
    console.info('Text:', e.text);
    console.info('Trigger:', e.trigger);
    e.clearSelection();
    alert("Link copied: " + e.text);
  });

  // Load the file details to edit
  function selectFile(fileId) {
    // Reset form
    document.getElementById("fileForm").reset();
    // Get the data and populate the form
    $.getJSON("${ctx}/json/file?id=" + fileId, function( data ) {
      if (data.id === undefined) {
        alert('You do not have access to modify this item');
        return;
      }

      document.getElementById('formTitle').innerHTML = "Update File";
      document.getElementById('id').value = data.id;
      if ($('#folderId').is('input, select')) {
        $("#folderId").val(data.folderId);
      } else {
        document.getElementById('folderId').value = data.folderId;
      }
      document.getElementById('currentSubFolderId').value = data.subFolderId;
      if ($('#subFolderId').is('input, select')) {
        $("#subFolderId").val(data.subFolderId);
      } else {
        document.getElementById('subFolderId').value = data.subFolderId;
      }
      if ($('#categoryId').is('input, select')) {
        $("#categoryId").val(data.categoryId);
      } else {
        var categoryIdEl = document.getElementById('categoryId');
        if (categoryIdEl) {
          categoryIdEl.value = data.categoryId;
        }
      }
      if (data.hasOwnProperty('version')) {
        document.getElementById('version').value = data.version;
      }
      if (data.hasOwnProperty('summary')) {
        document.getElementById('summary').value = data.summary;
      }
      if (data.hasOwnProperty('filename')) {
        document.getElementById('filename').value = data.filename;
      }
      document.getElementById('title').value = data.title;

      // Show the form
      var $modal = $('#fileFormReveal');
      $modal.foundation('open');
    });
  }
</script>
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
  <div class="reveal small" id="fileFormReveal" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-in="slide-in-down fast">
    <button class="close-button" data-close aria-label="Close modal" type="button">
      <span aria-hidden="true">&times;</span>
    </button>
    <h4 id="formTitle">Modify File</h4>
    <form id="fileForm" method="post" action="${widgetContext.uri}"
      autocomplete="off" enctype="multipart/form-data">
      <%-- Required by controller --%>
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <%-- Form --%>
      <input type="hidden" name="id" id="id" value="-1"/>
      <input type="hidden" name="currentFolderId" id="currentFolderId" value="${folder.id}"/>
      <input type="hidden" name="currentSubFolderId" id="currentSubFolderId" value="${subFolder.id}"/>
      <c:if test="${subFolderList.size() gt 1 || folderList.size() gt 1 || !empty folderCategoryList}">
        <div class="grid-x grid-margin-x">
      </c:if>
      <c:choose>
        <c:when test="${subFolderList.size() gt 0}">
          <div class="small-6 cell">
            <input type="hidden" name="folderId" id="folderId" value="${folderList[0].id}" />
            <label>Sub-Folder
              <select name="subFolderId" id="subFolderId">
                <option value="-1"></option>
                <c:forEach items="${subFolderList}" var="subFolder" varStatus="status">
                  <option value="${subFolder.id}"><c:out value="${subFolder.name}" /></option>
                </c:forEach>
              </select>
            </label>
          </div>
        </c:when>
        <c:when test="${folderList.size() eq 1}">
          <input type="hidden" name="folderId" id="folderId" value="${folderList[0].id}" />
          <input type="hidden" name="subFolderId" id="subFolderId" value="${subFolder.id}" />
        </c:when>
        <c:otherwise>
          <div class="small-6 cell">
            <input type="hidden" name="subFolderId" id="subFolderId" value="${subFolder.id}" />
            <label>Folder
              <select name="folderId" id="folderId">
                <c:forEach items="${folderList}" var="folder" varStatus="status">
                  <option value="${folder.id}"><c:out value="${folder.name}" /></option>
                </c:forEach>
              </select>
            </label>
          </div>
        </c:otherwise>
      </c:choose>
      <c:if test="${!empty folderCategoryList}">
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
      </c:if>
      <c:if test="${subFolderList.size() gt 1 || folderList.size() gt 1 || !empty folderCategoryList}">
        </div>
      </c:if>
      <label>Display Name <span class="required">*</span>
        <input type="text" placeholder="Name" name="title" id="title" value="" required>
      </label>
      <label>Summary
        <input type="text" placeholder="File Summary" name="summary" id="summary" value="">
      </label>
      <label>URL/Filename
        <input type="text" placeholder="Filename" name="filename" id="filename" value="">
      </label>
      <label>Add a file version
        <input type="file" name="file" id="file">
      </label>
      <label>Version
        <input type="text" placeholder="Version" name="version" id="version" value="">
      </label>
      <p>
        <input type="submit" class="button radius success expanded" value="Save" />
      </p>
    </form>
  </div>
</c:if>