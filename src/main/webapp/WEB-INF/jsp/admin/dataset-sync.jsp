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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="dataset" class="com.simisinc.platform.domain.model.datasets.Dataset" scope="request"/>
<jsp:useBean id="fieldMappingsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="fieldOptionsList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="sampleRow" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="columnConfiguration" class="java.lang.String" scope="request"/>
<script nonce="${cspNonce}">
  function checkForm() {
    return true;
  }
  function confirmDelete() {
    return confirm('This will delete all items that were created by this dataset, are you sure?')
  }
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <p>This tab controls two independent things: whether the source file is <strong>re-downloaded on a schedule</strong>, and whether a successful download is <strong>synced into the mapped Collection</strong> as Items. Turning on "Download on a schedule?" by itself only refreshes the source file periodically -- it does not write or update any Items unless "Automatically sync to a collection?" is also turned on. Before relying on the schedule, click <strong>Save &amp; Sync</strong> once here to confirm the mapping actually produces the Items you expect.</p>
  <p style="margin-bottom:0">After every sync, check <strong>Last Sync</strong> and its message below -- that's the primary place errors surface. It's a single status string, not a per-row log, so a partial failure (some rows succeeded, others didn't) may need manual reconciliation rather than give a full picture of what went wrong.</p>
</div>
<div class="callout warning radius">
  <p style="margin-bottom:0">If scheduled downloads keep failing, retries back off from every 5 minutes up to once a day. After 30 consecutive failed attempts the dataset is marked permanently failed and the schedule stops retrying on its own -- there's no email or other proactive notice, only the Schedule Status badge on the <a href="${ctx}/admin/datasets">Datasets list</a> turning to "Failed". Check that page on a regular cadence, or set up external monitoring against it, if a dataset's freshness matters.</p>
</div>
<form method="post" onsubmit="return checkForm()">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${dataset.id}"/>
  <%-- Details --%>
  <c:choose>
    <c:when test="${!empty dataset.syncDate}">
      <p>
        Last Sync: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${dataset.syncDate}" />
        <span class="label primary"><c:out value="${date:relative(dataset.syncDate)}" /></span>
      </p>
      <c:if test="${!empty dataset.syncMessage}">
        <p>
          <c:out value="${dataset.syncMessage}" />
        </p>
      </c:if>
    </c:when>
    <c:otherwise>
      <p>
        <span class="label primary">No previous sync</span>
      </p>
    </c:otherwise>
  </c:choose>
  <c:if test="${mappedCollectionMissing}">
  <div class="callout warning radius">
    This dataset is mapped to a Collection that no longer exists (it was likely deleted). Sync
    cannot run until you pick a different Collection to sync into, or recreate the missing one.
  </div>
  </c:if>
  <p>
    Mapped Collection:
    <c:if test="${!empty collection.name}"><span class="label"><c:out value="${collection.name}" /></span></c:if>
    <c:if test="${fn:startsWith(dataset.collectionUniqueId, 'NEW-')}"><span class="label">&lt;<c:out value="${dataset.name}"/>&gt;</span></c:if>
    <br />
    Unique Column for Merge <small class="subheader">(set on the Map Fields tab)</small>
    <c:choose>
      <c:when test="${!empty dataset.uniqueColumnName}">
        <span class="label"><c:out value="${dataset.uniqueColumnName}" /></span>
      </c:when>
      <c:otherwise>
        <span class="label alert">&lt;UNSET&gt;</span>
      </c:otherwise>
    </c:choose>
    <br />
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
    <br />
    Sync'd Record Count: <span class="label round success" id="rowCount"><fmt:formatNumber value="${syncCount}" /></span>
    <div class="button-container">
      <input type="submit" class="button radius alert" name="removeAll" value="Remove All Sync'd Records" onclick="return confirmDelete();"/>
    </div>
  </p>
  <%-- Form --%>  
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
          <%--<option value="<c:out value="${dataset.scheduleFrequency}" />" selected><c:out value="${dataset.scheduleFrequency}" /></option>--%>
        </select>
      </div>
    </div>

    <legend>Automatically sync to a collection?</legend>
    <div class="grid-x grid-margin-x">
      <div class="shrink cell">
        <div class="switch large">
          <input class="switch-input" id="syncEnabled-yes-no" type="checkbox" name="syncEnabled" value="true"<c:if test="${dataset.syncEnabled}"> checked</c:if>>
          <label class="switch-paddle" for="syncEnabled-yes-no">
          <span class="switch-active" aria-hidden="true">Yes</span>
          <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </div>
      <div class="medium-4 cell">
        <select name="syncMergeType">
          <option value="">Choose</option>
          <option value="sync"<c:if test="${dataset.syncMergeType eq 'sync'}"> selected</c:if>>Full Merge: Add, Update, and Delete records</option>
        </select>
      </div>
    </div>
    <p class="help-text">"Sync Merge Type" is intended to control which operations (add/update/delete) a sync performs; only one option is offered here today.</p>
  </fieldset>

  <div class="button-container">
    <input type="submit" class="button radius success" name="save" value="Save"/>
    <input type="submit" class="button radius success" name="process" value="Save & Sync"/>
  </div>
</form>
<div class="reveal medium" id="processReveal" data-reveal data-animation-in="slide-in-down fast" role="dialog" aria-modal="true" aria-label="Validating Data">
  <h3>Validating Data...</h3>
  <%--<p><a class="button small radius primary" href="${ctx}/admin/datasets">Continue to datasets list <i class="fa fa-caret-right"></i></a></p>--%>
</div>