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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="folderCategory" uri="/WEB-INF/tlds/folder-category-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="subFolderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="folderCategoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="fileList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="canEdit" class="java.lang.String" scope="request"/>
<jsp:useBean id="canDelete" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/clipboard-2.0.11/clipboard.min.js"></script>
<%@include file="../page_messages.jspf" %>
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
  <a href="${ctx}/admin/file-form?subFolderId=${subFolder.id}&folderId=${folder.id}&returnPage=${widgetContext.uri}%3FsubFolderId=${subFolder.id}%26folderId=${folder.id}" class="button small primary radius float-left"><i class="fa fa-plus"></i> Add File Link</a>
</c:if>
<c:if test="${!empty fileList}">
  <a href="${ctx}/admin/folder-zip-download?folderId=${folder.id}&subFolderId=${subFolder.id}" class="button small secondary radius float-left" title="Download all listed files as a zip"><i class="fa fa-file-archive-o"></i> Download Zip</a>
</c:if>
<div style="clear: both;"></div>
<c:if test="${canDelete eq 'true' && !empty fileList}">
  <div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
    <span id="bulkSelectedCount"></span>
    <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete Selected</button>
  </div>
</c:if>
<table class="unstriped">
  <thead>
    <tr>
      <c:if test="${canDelete eq 'true' && !empty fileList}">
        <th width="30"><input type="checkbox" id="selectAllFiles" aria-label="Select all files"></th>
      </c:if>
      <th>
        Filename
      </th>
      <th width="110">action</th>
      <th width="50" class="text-center">size</th>
      <th width="60" class="text-center">uploaded</th>
      <th width="70" class="text-center">downloads</th>
    </tr>
  </thead>
  <tbody>
    <c:if test="${empty fileList}">
      <tr>
        <td colspan="6">No files were found</td>
      </tr>
    </c:if>
    <c:forEach items="${fileList}" var="file">
    <tr>
      <c:if test="${canDelete eq 'true'}">
        <td><input type="checkbox" class="fileRowCheckbox" value="${file.id}" data-filename="${fn:escapeXml(file.title)}" aria-label="Select <c:out value="${file.title}"/>"></td>
      </c:if>
      <td>
        <c:if test="${fn:toLowerCase(file.fileType) eq 'image'}">
          <img class="image-left" width="200" src="${ctx}/assets/view/${file.url}" />
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
              <small><a target="_blank" href="<c:out value="${file.filename}" />"><c:out value="${text:trim(file.filename, 50, true)}" /></a></small>
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
          <c:when test="${fn:toLowerCase(file.fileType) eq 'url'}">
            <a class="clipboard" title="Copy view link to clipboard" data-clipboard-text="${ctx}/assets/view/${file.baseUrl}?ref=${url:encodeUri(file.filename)}"><i class="fa fa-clipboard"></i></a>
          </c:when>
          <c:when test="${fn:toLowerCase(file.fileType) eq 'video'}">
            <a class="clipboard" title="Copy view link to clipboard" data-clipboard-text="${ctx}/assets/view/${file.url}"><i class="fa fa-clipboard"></i></a>
          </c:when>
          <c:otherwise>
            <a class="clipboard" title="Copy download link to clipboard" data-clipboard-text="${ctx}/assets/file/${file.url}"><i class="fa fa-clipboard"></i></a>
          </c:otherwise>
        </c:choose>
        <c:if test="${fn:toLowerCase(file.fileType) eq 'pdf' || fn:toLowerCase(file.fileType) eq 'image' || fn:toLowerCase(file.fileType) eq 'video'}">
          <a target="_blank" title="Open in new tab" href="${ctx}/assets/view/${file.url}"><i class="fa fa-desktop"></i></a>
        </c:if>
        <c:if test="${fn:toLowerCase(file.fileType) ne 'url'}">
          <a title="Download file" href="${ctx}/assets/file/${file.url}"><i class="fa fa-download"></i></a>
        </c:if>
        <c:if test="${canDelete eq 'true'}">
          <a title="Delete file" href="#" onclick="return confirmPostAction('Are you sure you want to delete <c:out value="${js:escape(file.filename)}" />?', '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&fileId=${file.id}');"><i class="fa fa-remove"></i></a>
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
<script nonce="${cspNonce}">
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

  // Bulk select + delete (mirrors image-browser.jsp's bulk delete, PR #834)
  (function () {
    var $selectAll = document.getElementById('selectAllFiles');
    var rowCheckboxes = document.querySelectorAll('.fileRowCheckbox');
    var $bar = document.getElementById('bulkActionsBar');
    var $count = document.getElementById('bulkSelectedCount');

    function selected() {
      return Array.prototype.filter.call(rowCheckboxes, function (cb) {
        return cb.checked;
      });
    }

    function refresh() {
      var n = selected().length;
      if ($count) {
        $count.textContent = n + (n === 1 ? ' file selected  ' : ' files selected  ');
      }
      if ($bar) {
        $bar.style.display = n > 0 ? '' : 'none';
      }
      if ($selectAll) {
        $selectAll.indeterminate = n > 0 && n < rowCheckboxes.length;
        $selectAll.checked = n > 0 && n === rowCheckboxes.length;
      }
    }

    if ($selectAll) {
      $selectAll.addEventListener('change', function () {
        rowCheckboxes.forEach(function (cb) {
          cb.checked = $selectAll.checked;
        });
        refresh();
      });
    }
    rowCheckboxes.forEach(function (cb) {
      cb.addEventListener('change', refresh);
    });

    var bulkDeleteBtn = document.getElementById('bulkDeleteBtn');
    if (bulkDeleteBtn) {
      bulkDeleteBtn.addEventListener('click', function () {
        var checked = selected();
        var $reveal = $('#bulkDeleteReveal');
        var $form = $reveal.find('form');
        var $list = $('#bulkDeleteList');
        $form.find('input[name="fileId"]').remove();
        $list.empty();
        checked.forEach(function (cb) {
          $form.append($('<input type="hidden" name="fileId">').val(cb.value));
          $list.append($('<li>').text(cb.getAttribute('data-filename')));
        });
        $('#bulkDeleteCount').text(checked.length);
        $reveal.foundation('open');
      });
    }

    refresh();
  })();
</script>
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
  <div class="reveal small" id="fileFormReveal" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-in="slide-in-down fast" role="dialog" aria-modal="true" aria-labelledby="formTitle">
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
      <div class="button-container">
        <input type="submit" class="button radius success expanded" value="Save" />
      </div>
    </form>
  </div>
</c:if>
<c:if test="${canDelete eq 'true' && !empty fileList}">
  <%-- Bulk delete confirmation -- the list below is populated at open time (see the JS) from the
       checked rows' data-filename attributes, so the admin sees exactly what will be deleted. --%>
  <div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
       data-reveal data-close-on-click="true">
    <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> File(s)</h4>
    <ul id="bulkDeleteList"></ul>
    <form method="post" action="${widgetContext.uri}">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="command" value="bulkDelete"/>
      <input type="hidden" name="currentFolderId" value="${folder.id}"/>
      <input type="hidden" name="currentSubFolderId" value="${subFolder.id}"/>
      <input type="submit" class="button alert radius" value="Delete Files"/>
      <button class="button secondary radius" type="button" data-close>Cancel</button>
    </form>
    <button class="close-button" data-close aria-label="Close reveal" type="button">
      <span aria-hidden="true">&times;</span>
    </button>
  </div>
</c:if>