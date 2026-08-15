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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dropzone-5.9.3/dropzone.min.css" />
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Every handler here is bound by dropzone-setup.js with addEventListener, and this block runs
     under the page's nonce. Do not reintroduce onclick=/onchange= attributes: PageServlet sends
     script-src 'self' 'nonce-...' with no 'unsafe-inline', so inline handler attributes fall under
     script-src-attr, which a nonce does not cover -- the browser refuses to run them and the
     control silently does nothing (issue #1188). --%>
<script src="${ctx}/javascript/dropzone-5.9.3/dropzone.min.js"></script>
<script src="${ctx}/javascript/dropzone-setup.js"></script>
<script nonce="${cspNonce}">
  initializeDropzone('myDropzone', ${maxUploadSize}, 'image/*');
</script>
<p>Drag images here or use "Browse Files" to select them, then click "Upload All Files". Use "Reset" to clear your selections.</p>
<form action="${widgetContext.uri}?widget=${widgetContext.uniqueId}" class="dropzone" id="my-dropzone">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Fallback --%>
  <div class="fallback">
    <input name="file" type="file" accept="image/*" multiple />
  </div>
</form>
<div id="upload-errors" role="alert" aria-live="assertive" class="callout alert"></div>
<div id="upload-status" role="status" aria-live="polite"></div>
<button type="button" class="button secondary no-gap" id="dz-browse">Browse Files</button>
<button class="button primary no-gap" id="submit-all" disabled>Upload All Files</button>
<button class="button secondary no-gap" id="clear-dropzone">Reset</button>
