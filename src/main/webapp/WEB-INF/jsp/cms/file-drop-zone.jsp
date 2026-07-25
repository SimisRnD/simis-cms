<%--
  ~ Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dropzone-5.9.3/dropzone.min.css" />
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h4>
  <i class="fa fa-folder-open-o"></i> <c:out value="${folder.name}" />
</h4>
<div id="upload-status-region" role="status" aria-live="polite"></div>
<div id="upload-error-region" role="alert" hidden></div>
<script src="${ctx}/javascript/dropzone-5.9.3/dropzone.min.js"></script>
<script>
  (function() {
    var n = sessionStorage.getItem('dz-uploaded');
    if (n) {
      sessionStorage.removeItem('dz-uploaded');
      var el = document.getElementById('upload-status-region');
      if (el) el.textContent = n + ' file' + (n == 1 ? '' : 's') + ' uploaded successfully.';
    }
  })();
  var uploadErrorCount = 0;
  Dropzone.options.myDropzone = {
    autoProcessQueue: false,
    parallelUploads: 2,
    maxFilesize: 20,
    clickable: ['#browse-files', '#my-dropzone'],
    dictDefaultMessage: 'Drag and drop files here, or click to browse (max 20 MB)',
    init: function() {
      var submitButton = document.querySelector("#submit-all");
      var errorRegion = document.querySelector("#upload-error-region");
      myDropzone = this;
      submitButton.addEventListener("click", function() {
        uploadErrorCount = 0;
        errorRegion.textContent = '';
        errorRegion.hidden = true;
        myDropzone.processQueue();
      });
      this.on("addedfile", function() {
        if (submitButton.classList.contains('primary')) {
          submitButton.classList.add('success');
          submitButton.classList.remove('primary');
        }
      });
      this.on("error", function(file, message) {
        uploadErrorCount++;
        var msg = (typeof message === 'string') ? message : (message && message.error ? message.error : 'Upload failed');
        errorRegion.textContent = 'Upload error: ' + msg;
        errorRegion.hidden = false;
      });
      var _this = this;
      document.querySelector("#clear-dropzone").addEventListener("click", function() {
        _this.removeAllFiles(true);
        uploadErrorCount = 0;
        errorRegion.textContent = '';
        errorRegion.hidden = true;
        if (submitButton.classList.contains('success')) {
          submitButton.classList.add('primary');
          submitButton.classList.remove('success');
        }
      });
    },
    success: function() {
      myDropzone = this;
      myDropzone.processQueue();
    },
    queuecomplete: function() {
      if (uploadErrorCount === 0) {
        var count = myDropzone.getFilesWithStatus(Dropzone.SUCCESS).length;
        try { sessionStorage.setItem('dz-uploaded', count); } catch(e) {}
        window.location.href = '' + window.location.href;
      }
    }
  };
</script>
<p>Add files to upload, then click Upload All Files.</p>
<button id="browse-files" class="button secondary hollow no-gap" type="button">Browse files</button>
<form action="${widgetContext.uri}?widget=${widgetContext.uniqueId}" class="dropzone" id="my-dropzone">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Fallback --%>
  <div class="fallback">
    <input name="file" type="file" multiple />
  </div>
</form>
<button class="button primary no-gap" id="submit-all">Upload All Files</button>
<button class="button secondary no-gap" id="clear-dropzone">Reset</button>
