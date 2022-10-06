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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="codeContent" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/tinymce-6.1.2/tinymce.min.js"></script>
<script>
tinymce.init({
  selector: 'textarea',
  branding: false,
  width: '100%',
  height: 500,
  menubar: false,
  plugins: 'searchreplace code fullscreen code wordcount',
  toolbar: 'insert | undo redo | removeformat',
  insert_button_items: ''
});
</script>
<%@include file="../page_messages.jspf" %>
<form class="content-editor" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form Content --%>
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <p>
    <textarea name="content"><c:out value="${codeContent}" /></textarea>
  </p>
  <div class="button-container">
    <%--<input type="submit" class="button radius success" value="Save" />--%>
    <a href="${returnPage}" class="button radius secondary">Cancel</a>
  </div>
</form>