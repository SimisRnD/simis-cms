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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collectionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="fieldMappingsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="fieldOptionsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="sampleRow" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="columnConfiguration" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<form method="post" onsubmit="return checkForm()">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Form --%>
  <table class="unstriped">
    <thead>
      <tr>
        <th width="240">Field Name</th>
        <th width="240">Type</th>
        <th>Options</th>
      </tr>
    </thead>
    <tbody>
      <c:forEach items="${dataset.fieldTitlesList}" var="column" varStatus="status">
      <tr>
        <td>
          <c:out value="${column}" /><br />
          <small class="subheader"><c:out value="${text:trim(sampleRow[status.index], 30, true)}" /></small>
        </td>
        <td>
          <select name="columnMapping${status.index}">
            <option value=""></option>
            <option value="name"<c:if test="${fieldMappingsList[status.index] eq 'name'}"> selected</c:if>>Name</option>
            <option value="category"<c:if test="${fieldMappingsList[status.index] eq 'category'}"> selected</c:if>>Category</option>
            <option value="summary"<c:if test="${fieldMappingsList[status.index] eq 'summary'}"> selected</c:if>>Summary</option>
            <option value="description"<c:if test="${fieldMappingsList[status.index] eq 'description'}"> selected</c:if>>HTML Description</option>
            <option value="geopoint"<c:if test="${fieldMappingsList[status.index] eq 'geopoint'}"> selected</c:if>>Geo Point</option>
            <option value="latitude"<c:if test="${fieldMappingsList[status.index] eq 'latitude'}"> selected</c:if>>Latitude</option>
            <option value="longitude"<c:if test="${fieldMappingsList[status.index] eq 'longitude'}"> selected</c:if>>Longitude</option>
            <option value="location"<c:if test="${fieldMappingsList[status.index] eq 'location'}"> selected</c:if>>Location Name</option>
            <option value="street"<c:if test="${fieldMappingsList[status.index] eq 'street'}"> selected</c:if>>Street Address</option>
            <option value="addressLine2"<c:if test="${fieldMappingsList[status.index] eq 'addressLine2'}"> selected</c:if>>Street Address Line 2</option>
            <option value="addressLine3"<c:if test="${fieldMappingsList[status.index] eq 'addressLine3'}"> selected</c:if>>Street Address Line 3</option>
            <option value="city"<c:if test="${fieldMappingsList[status.index] eq 'city'}"> selected</c:if>>City</option>
            <option value="state"<c:if test="${fieldMappingsList[status.index] eq 'state'}"> selected</c:if>>State</option>
            <option value="postalCode"<c:if test="${fieldMappingsList[status.index] eq 'postalCode'}"> selected</c:if>>Postal Code</option>
            <option value="country"<c:if test="${fieldMappingsList[status.index] eq 'country'}"> selected</c:if>>Country</option>
            <option value="county"<c:if test="${fieldMappingsList[status.index] eq 'county'}"> selected</c:if>>County</option>
            <option value="phoneNumber"<c:if test="${fieldMappingsList[status.index] eq 'phoneNumber'}"> selected</c:if>>Phone Number</option>
            <option value="email"<c:if test="${fieldMappingsList[status.index] eq 'email'}"> selected</c:if>>Email Address</option>
            <option value="cost"<c:if test="${fieldMappingsList[status.index] eq 'cost'}"> selected</c:if>>Cost</option>
            <option value="startDate"<c:if test="${fieldMappingsList[status.index] eq 'startDate'}"> selected</c:if>>Start Date</option>
            <option value="endDate"<c:if test="${fieldMappingsList[status.index] eq 'endDate'}"> selected</c:if>>End Date</option>
            <option value="expectedDate"<c:if test="${fieldMappingsList[status.index] eq 'expectedDate'}"> selected</c:if>>Expected Date</option>
            <option value="expirationDate"<c:if test="${fieldMappingsList[status.index] eq 'expirationDate'}"> selected</c:if>>Expiration Date</option>
            <option value="url"<c:if test="${fieldMappingsList[status.index] eq 'url'}"> selected</c:if>>URL</option>
            <option value="imageUrl"<c:if test="${fieldMappingsList[status.index] eq 'imageUrl'}"> selected</c:if>>Image URL</option>
            <option value="barcode"<c:if test="${fieldMappingsList[status.index] eq 'barcode'}"> selected</c:if>>Barcode</option>
            <option value="assignedTo"<c:if test="${fieldMappingsList[status.index] eq 'assignedTo'}"> selected</c:if>>Assigned To</option>
            <option value="privacyType"<c:if test="${fieldMappingsList[status.index] eq 'privacyType'}"> selected</c:if>>Privacy Type</option>
            <option value="custom"<c:if test="${fieldMappingsList[status.index] eq 'custom'}"> selected</c:if>>&lt;${column}&gt;</option>
          </select>
        </td>
        <td>
          <input type="text" name="columnOptions${status.index}" placeholder="Options" value="<c:out value="${fieldOptionsList[status.index]}" />" autocomplete="off" />
        </td>
      </tr>
      </c:forEach>
      <%--<c:if test="${empty columnList}">--%>
        <%--<tr>--%>
          <%--<td colspan="3">No fields were found</td>--%>
        <%--</tr>--%>
      <%--</c:if>--%>
    </tbody>
  </table>
  <div class="grid-x grid-padding-x">
    <fieldset class="small-12 cell">
      <legend>Import Options</legend>
      <label>Selected Directory
        <select name="collectionUniqueId">
          <option value=""></option>
          <c:forEach items="${collectionList}" var="collection">
            <option value="<c:out value="${collection.uniqueId}" />"<c:if test="${collection.uniqueId eq dataset.collectionUniqueId}"> selected</c:if>><c:out value="${collection.name}" /></option>
          </c:forEach>
          <option value="NEW-<c:out value="${dataset.name}" />"<c:if test="${fn:startsWith(dataset.collectionUniqueId, 'NEW-')}"> selected</c:if>>&lt;${dataset.name}&gt;</option>
        </select>
      </label>
      <input type="checkbox" id="skipDuplicateNames" name="skipDuplicateNames" value="true" checked="checked" /><label for="skipDuplicateNames">Skip duplicate records
      <c:choose>
        <c:when test="${dataset.fileType eq 'application/rss+xml'}">(by URL)</c:when>
        <c:otherwise>(by Name)</c:otherwise>
      </c:choose>
      </label>
      <br />
      <input type="checkbox" id="doProcess" name="doProcess" value="true" /><label for="doProcess">Import the records into a directory after saving</label>
    </fieldset>
  </div>
  <p>
    <input type="submit" class="button radius success" name="process" value="Save"/>
    <a class="button radius secondary" href="${ctx}/admin/datasets">Cancel</a>
  </p>
</form>
<div class="reveal medium" id="processReveal" data-reveal data-animation-in="slide-in-down fast">
  <h3>Validating Data...</h3>
  <%--<p><a class="button small radius primary" href="${ctx}/admin/datasets">Continue to datasets list <i class="fa fa-caret-right"></i></a></p>--%>
</div>
<script>
  function checkForm() {
    var doProcessCheckbox = document.getElementById("doProcess");
    if (doProcessCheckbox.checked) {
      var elem = new Foundation.Reveal($('#processReveal'));
      elem.open();
    }
    return true;
  }
</script>
