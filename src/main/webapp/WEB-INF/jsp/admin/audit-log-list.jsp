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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="auditLogList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Prominent, hard-to-miss warning: the nightly tamper-evidence chain check (AuditLogIntegrityJob) found the
     chain broken. Deliberately rendered only on an actual failure -- see AuditLogListWidget#execute. --%>
<c:if test="${integrityCheckFailed}">
  <div class="callout alert radius" role="alert" tabindex="-1" style="border:3px solid #cc4b37">
    <h5><i class="fa fa-triangle-exclamation"></i> Audit log tamper-evidence check FAILED</h5>
    <p>
      The automated check that verifies the audit log's tamper-evident hash chain detected a problem
      <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${integrityCheckFailedAt}' />"><c:out value="${date:relative(integrityCheckFailedAt)}" /></span>.
      This means one or more records below may have been altered, deleted, reordered, or inserted outside of
      the normal application. If the chain has genuinely been compromised, an attacker capable of rewriting
      the database could also have made this page's own data look consistent -- so this UI alone cannot be
      fully trusted to show what actually happened.
    </p>
    <p>
      <strong>What to do:</strong> Do not rely solely on this page. Cross-check the independent, out-of-band
      copy of these events in your SIEM / structured JSON audit log stream (every event is shipped there
      specifically so a rewritten database can be detected against it) and escalate to your security team to
      investigate before trusting audit records from around or after the time above.
    </p>
    <c:if test="${!empty integrityCheckFailedDetails}">
      <p><small>Check details: <c:out value="${integrityCheckFailedDetails}" /></small></p>
    </c:if>
  </div>
</c:if>
<p class="text-right">
  <small class="subheader">Events older than <c:out value="${retentionDays}"/> days are purged automatically.</small>
  <a href="#" data-confirm-post="Run the audit log tamper-evidence check now? This walks the entire chain and may take a few seconds on a large log." data-post-url="${widgetContext.uri}?runIntegrityCheck=true&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" class="button tiny">
    <i class="fa fa-shield-halved"></i> Run Integrity Check Now
  </a>
</p>
<div class="callout radius">
  <h6>What this page shows</h6>
  <p>Every event recorded here is written to two independent places at once: this database table (a tamper-evident hash chain, each row's hash covering the previous row's), and a separate structured JSON log line on the <code>AUDIT</code> logger. Both are populated by the same call, so under normal operation they always agree -- the JSON stream exists specifically so there's an out-of-band copy to check the database against if it's ever suspected of having been altered directly, bypassing the application.</p>
</div>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>The integrity check banner above appears.</strong> Follow its instructions -- cross-check against the JSON stream, don't rely on this page alone until it's resolved.</p>
  <p><strong>You expect an event that isn't here.</strong> Check <strong>Events older than N days are purged automatically</strong> above first -- it may simply have aged out. Beyond that, confirm the action you expected to be audited actually has a recording call in its code path; not every state change in the platform is wired to write an audit event, so "nothing shows up" can mean "correctly, nothing was recorded here" rather than a bug.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>The nightly tamper-evidence check (04:30) and the analytics PII scrub (04:45, see <a href="${ctx}/admin/analytics-retention">Analytics Retention</a>) are both distributed-lock protected, so on a multi-instance Azure App Service deployment exactly one instance runs each of them per night -- they will not run redundantly on every instance.</p>
  <p>The JSON <code>AUDIT</code> stream this page's cross-check advice depends on is written via the application's normal logger and reaches Log Analytics the same way as any other container stdout log (see <code>infra/modules/loganalytics.bicep</code>). If log routing or a filter is scoped to a specific logger name rather than everything, confirm it explicitly includes <code>AUDIT</code> -- otherwise the out-of-band copy this page's own integrity-failure guidance relies on may not actually be reaching your SIEM.</p>
</div>
<%-- Quick range presets: finer-grained (hour precision) than the date-only filter below. Picking one
     clears the explicit date range server-side (see AuditLogListWidget#buildSpecification). --%>
<div class="button-group tiny">
  <a href="${widgetContext.uri}?range=1h" class="button ${range eq '1h' ? 'primary' : 'secondary'}">Last hour</a>
  <a href="${widgetContext.uri}?range=24h" class="button ${range eq '24h' ? 'primary' : 'secondary'}">Last 24 hours</a>
  <a href="${widgetContext.uri}?range=7d" class="button ${range eq '7d' ? 'primary' : 'secondary'}">Last 7 days</a>
  <a href="${widgetContext.uri}?range=30d" class="button ${range eq '30d' ? 'primary' : 'secondary'}">Last 30 days</a>
  <c:if test="${!empty range}"><a href="${widgetContext.uri}" class="button secondary">Clear</a></c:if>
</div>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Category
        <select name="category">
          <option value="">Any category</option>
          <c:forEach items="${categoryList}" var="cat">
            <option value="<c:out value='${cat}'/>"<c:if test="${category eq cat}"> selected</c:if>><c:out value="${cat}"/></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-3">
      <label>Event type
        <input type="text" name="eventType" placeholder="e.g. user.disable" value="<c:out value='${eventType}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Outcome
        <select name="outcome">
          <option value="">Any outcome</option>
          <option value="success"<c:if test="${outcome eq 'success'}"> selected</c:if>>Success</option>
          <option value="failure"<c:if test="${outcome eq 'failure'}"> selected</c:if>>Failure</option>
        </select>
      </label>
    </div>
    <div class="cell medium-4">
      <label>Actor (email contains)
        <input type="text" name="actor" placeholder="username or email" value="<c:out value='${actor}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Source IP
        <input type="text" name="sourceIp" placeholder="e.g. 203.0.113.4" value="<c:out value='${sourceIp}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Target type
        <input type="text" name="targetType" placeholder="e.g. user" value="<c:out value='${targetType}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Target label
        <input type="text" name="targetLabel" placeholder="e.g. an IP address" value="<c:out value='${targetLabel}'/>">
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
    <div class="cell medium-6">
      <label>&nbsp;</label>
      <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
      <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
    </div>
  </div>
</form>
<%-- Export: same filter criteria as the results below (mirrored as hidden fields since export is a POST) --%>
<form method="post" autocomplete="off" class="margin-bottom-10">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="category" value="<c:out value='${category}'/>"/>
  <input type="hidden" name="eventType" value="<c:out value='${eventType}'/>"/>
  <input type="hidden" name="outcome" value="<c:out value='${outcome}'/>"/>
  <input type="hidden" name="actor" value="<c:out value='${actor}'/>"/>
  <input type="hidden" name="sourceIp" value="<c:out value='${sourceIp}'/>"/>
  <input type="hidden" name="targetType" value="<c:out value='${targetType}'/>"/>
  <input type="hidden" name="targetLabel" value="<c:out value='${targetLabel}'/>"/>
  <input type="hidden" name="range" value="<c:out value='${range}'/>"/>
  <input type="hidden" name="fromDate" value="<c:out value='${fromDate}'/>"/>
  <input type="hidden" name="toDate" value="<c:out value='${toDate}'/>"/>
  <button type="submit" name="command" value="downloadCSVFile" class="button small secondary radius"><i class="fa fa-download"></i> Export CSV</button>
  <button type="submit" name="command" value="downloadJSONFile" class="button small secondary radius"><i class="fa fa-download"></i> Export JSON</button>
</form>
<table class="unstriped hover">
  <thead>
    <tr>
      <th width="150">When</th>
      <th>Event</th>
      <th width="70">Outcome</th>
      <th>Actor</th>
      <th width="120">Source IP</th>
      <th>Target</th>
      <th width="90">Session</th>
      <th>Details</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${auditLogList}" var="record">
    <tr>
      <td nowrap>
        <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${record.occurred}' />"><c:out value="${date:relative(record.occurred)}" /></span>
      </td>
      <td>
        <c:out value="${record.eventType}" /><br />
        <small class="subheader"><c:out value="${record.eventCategory}" /></small>
      </td>
      <td nowrap>
        <c:choose>
          <c:when test="${record.outcome eq 'success'}"><span class="label success radius">success</span></c:when>
          <c:when test="${record.outcome eq 'failure'}"><span class="label alert radius">failure</span></c:when>
          <c:otherwise><span class="label secondary radius"><c:out value="${record.outcome}"/></span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty record.actorUsername}"><c:out value="${record.actorUsername}" /></c:when>
          <c:otherwise><em class="subheader">(unknown)</em></c:otherwise>
        </c:choose>
      </td>
      <td nowrap><c:out value="${record.sourceIp}" /></td>
      <td>
        <c:if test="${!empty record.targetLabel or !empty record.targetType or !empty record.targetId}">
          <c:out value="${record.targetLabel}" />
          <c:if test="${!empty record.targetType}">
            <br /><small class="subheader">
              <c:out value="${record.targetType}" /><c:if test="${!empty record.targetId}"> #<c:out value="${record.targetId}" /></c:if>
            </small>
          </c:if>
        </c:if>
      </td>
      <td class="break-word"><small><c:out value="${record.sessionId}" /></small></td>
      <td class="break-word">
        <c:if test="${!empty record.details}">
          <a href="#" class="audit-details-trigger" data-details="<c:out value='${record.details}' />" title="View full details">
            <c:choose>
              <c:when test="${fn:length(record.details) > 80}">
                <small><c:out value="${fn:substring(record.details, 0, 80)}" />&#8230;</small>
              </c:when>
              <c:otherwise>
                <small><c:out value="${record.details}" /></small>
              </c:otherwise>
            </c:choose>
          </a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty auditLogList}">
      <tr>
        <td colspan="8">No audit records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
<div class="reveal" id="auditDetailsReveal" data-reveal role="dialog" aria-modal="true" aria-labelledby="auditDetailsTitle">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4 id="auditDetailsTitle">Event Details</h4>
  <pre id="auditDetailsContent" class="break-word" style="white-space:pre-wrap"></pre>
</div>
<script nonce="${cspNonce}">
  $(function() {
    // The trigger's data-details is read back already HTML-decoded by jQuery's .data(); using .text()
    // (never .html()) to place it back into the DOM re-escapes it, so this is safe regardless of content.
    $('.audit-details-trigger').on('click', function(e) {
      e.preventDefault();
      $('#auditDetailsContent').text($(this).data('details'));
      $('#auditDetailsReveal').foundation('open');
    });
  });
</script>
