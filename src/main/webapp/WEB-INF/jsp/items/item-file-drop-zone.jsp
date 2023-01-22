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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.items.ItemFolder" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dropzone-5.9.3/dropzone.min.css" />
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty folder.name}">
<h4>
  <i class="fa fa-folder-open-o"></i> <c:out value="${folder.name}" />
</h4>
</c:if>
<%-- Identical to: --%>
<%--<form action="" method="post" enctype="multipart/form-data">--%>
  <%--<input type="file" name="file" />--%>
<%--</form>--%>
<script src="${ctx}/javascript/dropzone-5.9.3/dropzone.min.js"></script>
<script>
  Dropzone.options.myDropzone = {
    autoProcessQueue: false,
    parallelUploads: 2,
    maxFilesize: 100,
    dictDefaultMessage: 'Drag and Drop files from your desktop here (max 100MB)<br/><br/>(Click for file chooser)',
    init: function() {
      var submitButton = document.querySelector("#submit-all");
      myDropzone = this; // closure
      submitButton.addEventListener("click", function() {
        myDropzone.processQueue();
      });
      this.on("addedfile", function() {
        if (submitButton.classList.contains('primary')) {
          submitButton.classList.add('success');
          submitButton.classList.remove('primary');
        }
      });
      var _this = this;
      document.querySelector("#clear-dropzone").addEventListener("click", function() {
        _this.removeAllFiles(true);
        if (submitButton.classList.contains('success')) {
          submitButton.classList.add('primary');
          submitButton.classList.remove('success');
        }
      });
    },
    success: function() {
      myDropzone = this; // closure
      myDropzone.processQueue();
    },
    queuecomplete: function() {
      window.location.href= '' + window.location.href;
    }
  };
</script>
<p>Add files to upload, then choose to submit all files...</p>
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
