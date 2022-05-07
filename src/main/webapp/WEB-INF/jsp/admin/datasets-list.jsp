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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="datasetList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<a class="button small radius primary" href="${ctx}/admin/datasets/new"><i class="fa fa-cloud-upload"></i> Add a Dataset</a>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th>File</th>
      <th class="text-center">Size</th>
      <th width="180" class="text-center">Date</th>
      <th width="200" class="text-center">Status</th>
      <th width="80">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${datasetList}" var="dataset">
    <tr>
      <td>
        <a href="${ctx}/admin/dataset-preview?datasetId=${dataset.id}"><c:out value="${dataset.name}" /></a>
        <br />
        <small><fmt:formatNumber value="${dataset.rowCount}" /> record<c:if test="${dataset.rowCount ne 1}">s</c:if></small>
        <%--
        <c:choose>
          <c:when test="${dataset.rowsProcessed lt dataset.rowCount && dataset.rowsProcessed gt 0}">
            <span class="label round warning" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
          </c:when>
          <c:when test="${!empty dataset.processed}">
            <span class="label round success" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
          </c:when>
          <c:otherwise>
            <span class="label round" id="rowCount"><fmt:formatNumber value="${dataset.rowCount}" /></span>
          </c:otherwise>
        </c:choose>
        --%>
      </td>
      <td>
        <small><c:out value="${dataset.filename}" /></small>
      </td>
      <td class="text-center"><small><c:out value="${number:suffix(dataset.fileLength)}"/></small></td>
      <td class="text-center">
        <small>
        <c:choose>
          <c:when test="${!empty dataset.lastDownload}">
            <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${dataset.lastDownload}" /><br />
            <c:out value="${date:relative(dataset.lastDownload)}" />
          </c:when>
          <c:otherwise>
            <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${dataset.created}" /><br />
            <c:out value="${date:relative(dataset.created)}" />
          </c:otherwise>
        </c:choose>
        </small>
      </td>
      <td class="text-center">
        <c:choose>
          <c:when test="${dataset.rowsProcessed lt dataset.rowCount && dataset.rowsProcessed gt 0}">
            <span class="label round warning"><i class="fa fa-spinner fa-spin fa-fw"></i> Processing</span><br />
            <fmt:formatNumber value="${dataset.rowsProcessed}" /> / <fmt:formatNumber value="${dataset.rowCount}" />
          </c:when>
          <c:when test="${!empty dataset.processed}">
            <span class="label round success"><i class="fa fa-check"></i> Processed</span><br />
            <small class="subheader"><fmt:formatNumber value="${dataset.totalProcessTime}" /> ms</small>
          </c:when>
          <c:otherwise>
            <span class="label round">Ready</span>
          </c:otherwise>
        </c:choose>
      </td>
      <td>
        <a title="Modify dataset" href="${ctx}/admin/dataset-mapper?datasetId=${dataset.id}"><small><i class="${font:fas()} fa-edit"></i></small></a>
        <a href="${ctx}/assets/dataset/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${dataset.created}" />-${dataset.id}/${url:encodeUri(dataset.filename)}"><i class="fa fa-download"></i></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&datasetId=${dataset.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(dataset.name)}" />?');"><i class="fa fa-remove"></i></a>
        <%--<a href="${ctx}/admin/dataset?datasetId=${dataset.id}"><i class="fas fa-edit"></i></a>--%>
      </td>
    </tr>
<%--
    <c:if test="${!empty dataset.columnNames}">
      <tr>
        <td colspan="7">
          <small class="subheader">
            <c:forEach items="${dataset.columnNames}" var="column" varStatus="status">
              <c:out value="${column}" /><c:if test="${!status.last}">,</c:if>
            </c:forEach>
          </small>
        </td>
      </tr>
    </c:if>
--%>
    </c:forEach>
    <c:if test="${empty datasetList}">
      <tr>
        <td colspan="6">No datasets were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
