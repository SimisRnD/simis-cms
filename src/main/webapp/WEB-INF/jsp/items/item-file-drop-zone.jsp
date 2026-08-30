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
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.items.ItemFolder" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dropzone-5.9.3/dropzone.min.css" />
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty folder.name}">
<h3 class="h4">
  <i class="fa fa-folder-open-o"></i> <c:out value="${folder.name}" />
</h3>
</c:if>
<script src="${ctx}/javascript/dropzone-5.9.3/dropzone.min.js"></script>
<script src="${ctx}/javascript/dropzone-setup.js"></script>
<script nonce="${cspNonce}">
  initializeDropzone('myDropzone', 100);
</script>

<p>Drag files here or use "Browse Files" to select them, then click "Upload All Files". Use "Reset" to clear your selections.</p>
<form action="${widgetContext.uri}?widget=${widgetContext.uniqueId}" class="dropzone" id="my-dropzone">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Fallback --%>
  <div class="fallback">
    <input name="file" type="file" multiple />
  </div>
</form>
<%-- Kept in the DOM (empty) so aria-live announces into it; the callout/alert styling is added by
     dropzone-setup.js only while a message is present, or it paints an empty red box on every load --%>
<div id="upload-errors" role="alert" aria-live="assertive"></div>
<div id="upload-status" role="status" aria-live="polite"></div>
<button type="button" class="button secondary no-gap" id="dz-browse">Browse Files</button>
<button class="button primary no-gap" id="submit-all" disabled>Upload All Files</button>
<button class="button secondary no-gap" id="clear-dropzone">Reset</button>
