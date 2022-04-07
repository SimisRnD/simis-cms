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
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="columnConfiguration" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<form method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Form --%>
  <label>Dataset Name
    <input type="text" name="name" value="<c:out value="${dataset.name}"/>">
  </label>
  <label>Dataset Source Information
    <textarea name="sourceInfo"><c:out value="${dataset.sourceInfo}"/></textarea>
  </label>
  <label>Source URL
    <input type="text" placeholder="http(s)://" name="sourceUrl" value="<c:out value="${dataset.sourceUrl}"/>">
    <input type="checkbox" id="doDownload" name="doDownload" value="true" /><label for="doDownload">Download the file from the source url and replace this dataset</label><br />
  </label>
  <c:if test="${dataset.fileType eq 'application/json'}">
    <label>JSON Records Path
      <input type="text" placeholder="/" name="recordsPath" value="<c:out value="${dataset.recordsPath}"/>">
    </label>
    <label>JSON Columns Configuration
      <textarea name="columnConfiguration" rows="10"><c:out value="${columnConfiguration}"/></textarea>
    </label>
  </c:if>
  <label for="file" class="button small secondary radius"><i class="fa fa-upload"></i> Upload Replacement File</label>
  <input type="file" id="file" name="file" class="show-for-sr">
  <p>
    <input type="submit" class="button radius success" name="process" value="Save"/>
    <a class="button radius secondary" href="${ctx}/admin/datasets">Cancel</a>
  </p>
</form>
