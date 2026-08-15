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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dropzone-5.9.3/dropzone.min.css" />
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h4>
  <i class="fa fa-folder-open-o"></i> <c:out value="${folder.name}" />
  <c:if test="${!empty subFolder.name}">
    <br />
    <i class="fa fa-folder-open-o"></i> <c:out value="${subFolder.name}" />
  </c:if>
</h4>
<script src="${ctx}/javascript/dropzone-5.9.3/dropzone.min.js"></script>
<script src="${ctx}/javascript/dropzone-setup.js"></script>
<script nonce="${cspNonce}">
  initializeDropzone('myDropzone', 55);

  // Mirror the visible checkbox into the hidden field inside the form. Dropzone only serializes
  // inputs found within its own form element, and the checkbox reads better next to the upload
  // buttons it applies to than inside the drop target. Bound here rather than with an onchange=
  // attribute (issue #1188): PageServlet sends script-src 'self' 'nonce-...' with no
  // 'unsafe-inline', and a nonce does not cover inline event handler attributes.
  document.addEventListener('DOMContentLoaded', function () {
    var libraryCheckbox = document.getElementById('add-to-image-library');
    var libraryValue = document.getElementById('add-to-image-library-value');
    if (libraryCheckbox && libraryValue) {
      libraryCheckbox.addEventListener('change', function () {
        libraryValue.value = libraryCheckbox.checked ? 'true' : 'false';
      });
    }
  });
</script>
<p>Drag files here or use "Browse Files" to select them, then click "Upload All Files". Use "Reset" to clear your selections.</p>
<form action="${widgetContext.uri}?widget=${widgetContext.uniqueId}" class="dropzone" id="my-dropzone">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="folderId" value="${folder.id}"/>
  <input type="hidden" name="subFolderId" value="${subFolder.id}"/>
  <%-- Set from the checkbox below; folders and the Image Library are separate stores (issue #1197) --%>
  <input type="hidden" name="addToImageLibrary" id="add-to-image-library-value" value="false"/>
  <%-- Fallback --%>
  <div class="fallback">
    <input name="file" type="file" multiple />
  </div>
</form>
<div id="upload-errors" role="alert" aria-live="assertive" class="callout alert"></div>
<div id="upload-status" role="status" aria-live="polite"></div>
<label for="add-to-image-library">
  <input type="checkbox" id="add-to-image-library" aria-describedby="addToImageLibraryHelpText"/>
  Also add images to the Image Library
</label>
<p class="help-text" id="addToImageLibraryHelpText">Files uploaded to a folder are not site images. Tick this to also put an uploaded image in the Image Library, which is the only place the image picker on Site Settings and Theme Settings looks &mdash; so it can be chosen as the site logo or a page image. Non-image files are unaffected.</p>
<button type="button" class="button secondary no-gap" id="dz-browse">Browse Files</button>
<button class="button primary no-gap" id="submit-all" disabled>Upload All Files</button>
<button class="button secondary no-gap" id="clear-dropzone">Reset</button>
