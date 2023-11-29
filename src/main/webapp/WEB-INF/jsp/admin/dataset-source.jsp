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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="columnConfiguration" class="java.lang.String" scope="request"/>
<script>
  $(document).ready(function() {
    $('textarea').keypress(function(event) {
      if (event.keyCode === 13) {
        event.preventDefault();
      }
    });
  });
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<form method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="command" value="save" />
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Details --%>
  <c:if test="${!empty dataset.lastDownload}">
    <p>
      Last Download: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${dataset.lastDownload}" />
      <span class="label primary"><c:out value="${date:relative(dataset.lastDownload)}" /></span>
    </p>
  </c:if>
  <p>
    File Type: <c:out value="${dataset.fileType}" /><br />
    File Length: <c:out value="${number:suffix(dataset.fileLength)}"/><br />
    Column Count: <c:out value="${number:suffix(dataset.columnCount)}"/><br />
    Row Count:
    <c:choose>
      <c:when test="${dataset.rowCount eq -1}">
         No rows detected, check the configuration
      </c:when>
      <c:otherwise>
        <c:out value="${number:suffix(dataset.rowCount)}"/>
      </c:otherwise>
    </c:choose>
  </p>
  <c:choose>
    <c:when test="${!empty dataset.sourceUrl}">
      <label>Source URL
        <textarea name="sourceUrl" placeholder="http(s)://" rows="3"><c:out value="${dataset.sourceUrl}"/></textarea>
      </label>
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

<%--      
      <fieldset>
        <legend>Download on a schedule?</legend>
        <div class="grid-x grid-margin-x">
          <div class="shrink cell">
              <div class="switch large">
                <input class="switch-input" id="scheduleEnabled-yes-no" type="checkbox" name="scheduleEnabled" value="true"<c:if test="${dataset.scheduleEnabled}"> checked</c:if>>
                <label class="switch-paddle" for="scheduleEnabled-yes-no">
                  <span class="switch-active" aria-hidden="true">Yes</span>
                  <span class="switch-inactive" aria-hidden="true">No</span>
                </label>
              </div>
          </div>
          <div class="medium-4 cell">
            <select name="scheduleFrequency">
              <option value="">Choose</option>
              <c:forEach items="${scheduleOptionsMap}" var="option">
                <option value="<c:out value="${option.key}" />"<c:if test="${dataset.scheduleFrequency eq option.key}"> selected</c:if>><c:out value="${option.value}" /></option>
              </c:forEach>
              <option value="<c:out value="${dataset.scheduleFrequency}" />" selected><c:out value="${dataset.scheduleFrequency}" /></option>
            </select>
          </div>
        </div>
      </fieldset>
--%>

      <div class="button-container">
        <input type="submit" class="button radius" name="doSave" value="Save"/>
        <input type="submit" class="button radius" name="doDownload" value="Save & Download Remote File"/>
      </div>
    </c:when>
    <c:otherwise>
      <p>
        Created: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${dataset.created}" /><br />
        <c:out value="${date:relative(dataset.created)}" />
      </p>
    </c:otherwise>
  </c:choose>
</form>
<hr />
<form method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="command" value="uploadFile" />
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Form --%>
  <fieldset class="small-12 cell margin-top-20">
    <label for="file" class="button radius"><i class="fa fa-upload"></i> Upload Replacement File</label>
    <input type="file" id="file" name="file" class="show-for-sr">
  </fieldset>
</form>
