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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<form method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${dataset.name}"/>" required>
  </label>
  <label>Description
    <textarea name="sourceInfo"><c:out value="${dataset.sourceInfo}"/></textarea>
  </label>
  <label>Download data from a URL
    <input type="text" placeholder="http(s)://" name="sourceUrl" value="<c:out value="${dataset.sourceUrl}"/>">
  </label>
  <%--<label for="file" class="button radius">Choose File...</label>--%>
  <label>Upload a file
    <input type="file" id="file" name="file" accept="text/csv,.csv,application/json,application/vnd.geo+json,.json,.geojson,text/tab-separated-values,.tsv">
  </label>
  <p class="help-text">File must be a .csv, .tsv, .json, or .geojson</p>
  <label>Dataset Type <span class="required">*</span>
    <select name="fileType">
      <option value="application/json"<c:if test="${dataset.fileType eq 'application/json'}"> selected</c:if>>JSON</option>
      <option value="application/vnd.api+json"<c:if test="${dataset.fileType eq 'application/vnd.api+json'}"> selected</c:if>>JSON API</option>
      <option value="application/vnd.geo+json"<c:if test="${dataset.fileType eq 'application/vnd.geo+json'}"> selected</c:if>>GeoJSON</option>
      <option value="text/csv"<c:if test="${dataset.fileType eq 'text/csv'}"> selected</c:if>>CSV</option>
      <option value="text/tab-separated-values"<c:if test="${dataset.fileType eq 'text/tab-separated-values'}"> selected</c:if>>TSV</option>
      <option value="text/plain"<c:if test="${dataset.fileType eq 'text/plain'}"> selected</c:if>>Plain Text List</option>
      <option value="application/rss+xml"<c:if test="${dataset.fileType eq 'application/rss+xml'}"> selected</c:if>>RSS+XML</option>
    </select>
  </label>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Add Dataset"/>
    <a class="button radius secondary" href="${ctx}/admin/datasets">Cancel</a>
  </div>
</form>