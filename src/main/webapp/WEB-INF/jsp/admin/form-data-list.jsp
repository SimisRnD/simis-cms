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
<%@ taglib prefix="geoip" uri="/WEB-INF/geoip-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="formDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<script>
  function markFormAsProcessed(dataId) {
    if (!confirm("Mark as processed?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=markAsProcessed&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId;
  }
  function claimForm(dataId) {
    if (!confirm("Add this record to your list?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=claim&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId;
  }
  function archiveForm(dataId) {
    if (!confirm("Are you sure you want to archive this record and hide it?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=archive&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId;
  }
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty formDataList}">
  <p><small>Forms found: <fmt:formatNumber value="${recordPaging.totalRecordCount}" /></small></p>
</c:if>
<table>
  <thead>
  <tr>
    <th colspan="2">Form Values</th>
    <th>Form</th>
    <th>Action</th>
  </tr>
  </thead>
  <tbody>
  <c:if test="${empty formDataList}">
    <tr>
      <td colspan="5">
        No form records were found
      </td>
    </tr>
  </c:if>
  <c:forEach items="${formDataList}" var="formData">
    <tr>
      <td>
        <div class="grid-x grid-padding-x">
          <div class="small-4 text-right cell">
            <small>Submitted</small>
          </div>
          <div class="small-8 cell">
            <small>
              <c:out value="${date:relative(formData.created)}" /> &bull;
              <fmt:formatDate pattern="EEE" value="${formData.created}"/>
              <fmt:formatDate pattern="M/d/yyyy" value="${formData.created}"/>
              <fmt:formatDate pattern="hh:mm a" value="${formData.created}"/>
            </small>
            <c:if test="${formData.flaggedAsSpam}"><span class="alert label">spam likely</span></c:if>
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 text-right cell">
            <small>GeoIP</small>
          </div>
          <div class="small-8 cell">
            <small><c:out value="${geoip:location(formData.ipAddress, formData.ipAddress)}"/></small>
          </div>
        </div>
        <c:forEach items="${formData.formFieldList}" var="formField" varStatus="formFieldStatus">
          <c:if test="${!empty formField.userValue}">
            <div class="grid-x grid-padding-x">
              <div class="small-4 text-right cell">
                <small><c:out value="${formField.label}"/></small>
              </div>
              <div class="small-8 cell">
                <c:choose>
                  <c:when test="${formField.type eq 'email'}"><a href="mailto:<c:out value="${formField.userValue}"/>"><c:out value="${text:trim(formField.userValue, 512, true)}"/></a></c:when>
                  <c:otherwise><c:out value="${formField.userValue}"/></c:otherwise>
                </c:choose>
              </div>
            </div>
          </c:if>
        </c:forEach>
        <c:if test="${!empty formData.queryParameters}">
          <div class="grid-x grid-padding-x">
            <div class="small-4 text-right cell">
              <small>Data</small>
            </div>
            <div class="small-8 cell">
              <small>
                <c:out value="${formData.queryParameters}" />
              </small>
            </div>
          </div>
        </c:if>
        <c:if test="${!empty formData.url}">
          <div class="grid-x grid-padding-x">
            <div class="small-4 text-right cell">
              <small>Url</small>
            </div>
            <div class="small-8 cell">
              <small>
                <c:out value="${formData.url}" />
              </small>
            </div>
          </div>
        </c:if>
      </td>
      <td valign="top">
        <c:choose>
          <c:when test="${!empty formData.claimed}">
            <span id="${formData.id}" class="success round label"><i class="fa fa-check-square-o"></i></span>
          </c:when>
          <c:otherwise>
            &nbsp;
            <%--<span id="${formData.id}" class="secondary round label"><i class="fa fa-square-o"></i></span>--%>
          </c:otherwise>
        </c:choose>
      </td>
      <td nowrap valign="top"><c:out value="${formData.formUniqueId}"/></td>
      <td nowrap valign="top">
        <a class="button radius small primary" href="javascript:claimForm(${formData.id});">Claim</a>
        <a class="button radius small primary" href="javascript:markFormAsProcessed(${formData.id});">Mark as Processed</a>
        <a class="button radius small alert" href="javascript:archiveForm(${formData.id});">Remove</a>
      </td>
    </tr>
  </c:forEach>
  </tbody>
</table>
<%-- Paging Control --%>
<c:if test="${category.id gt 0}">
  <c:set var="recordPagingParams" scope="request" value="categoryId=${category.id}"/>
</c:if>
<%@include file="../paging_control.jspf" %>
