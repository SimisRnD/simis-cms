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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="formDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<script nonce="${cspNonce}">
  function markFormAsProcessed(dataId) {
    if (!confirm("Mark as processed?")) {
      return;
    }
    postAction('${widgetContext.uri}?action=markAsProcessed&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId);
  }
  function claimForm(dataId) {
    if (!confirm("Add this record to your list?")) {
      return;
    }
    postAction('${widgetContext.uri}?action=claim&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId);
  }
  function archiveForm(dataId) {
    if (!confirm("Are you sure you want to archive this record and hide it?")) {
      return;
    }
    postAction('${widgetContext.uri}?action=archive&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&dataId=' + dataId);
  }
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Form
        <input type="text" name="formUniqueId" placeholder="e.g. contact-us" value="<c:out value='${formUniqueId}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Status
        <select name="status">
          <option value="awaiting"<c:if test="${status eq 'awaiting'}"> selected</c:if>>Awaiting review</option>
          <option value="claimed"<c:if test="${status eq 'claimed'}"> selected</c:if>>Claimed</option>
          <option value="processed"<c:if test="${status eq 'processed'}"> selected</c:if>>Processed</option>
          <option value="dismissed"<c:if test="${status eq 'dismissed'}"> selected</c:if>>Dismissed</option>
        </select>
      </label>
    </div>
    <div class="cell medium-3">
      <label>From date
        <input type="date" name="fromDate" value="<c:out value='${fromDate}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>To date
        <input type="date" name="toDate" value="<c:out value='${toDate}'/>">
      </label>
    </div>
    <div class="cell medium-12">
      <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
      <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
    </div>
  </div>
</form>
<c:if test="${!empty formDataList}">
  <p><small>Forms found: <fmt:formatNumber value="${recordPaging.totalRecordCount}" /></small></p>
</c:if>
<form method="post" action="${widgetContext.uri}">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile"/>
  <button type="submit" class="button small secondary radius"><i class="fa fa-download"></i> Download CSV</button>
</form>
<table>
  <thead>
  <tr>
    <th colspan="2">Form Values</th>
    <th>IP Address</th>
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
            <small>GeoIP Location</small>
          </div>
          <div class="small-8 cell">
            <small><c:out value="${geoip:location(formData.ipAddress, '--')}"/></small>
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
      <td nowrap valign="top"><c:out value="${formData.ipAddress}"/></td>
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
<%@include file="../paging_control.jspf" %>
