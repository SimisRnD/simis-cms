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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>This page controls only <strong>session visitor PII</strong> (IP address, city, coordinates) -- the retention window below and the "Purge PII now" button affect session records exclusively. It does not control audit log, form data, or funnel event retention: those each have their own site property (<code>audit.retentionDays</code>, <code>formData.retentionDays</code>/<code>formData.failureRetentionDays</code>, <code>funnel.retentionDays</code>), but none of them currently has an admin editor -- changing any of them today requires a direct database update.</p>
  <p>The retention window shown below is set on the <a href="${ctx}/admin/configure-analytics">Analytics Settings</a> page, not here.</p>
</div>
<table>
  <tr>
    <th>Metric</th>
    <th class="text-right">Value</th>
  </tr>
  <tr>
    <td>Sessions with retained visitor PII (IP address, city, coordinates)</td>
    <td class="text-right"><fmt:formatNumber value="${sessionsWithPii}"/></td>
  </tr>
  <tr>
    <td>Retention window</td>
    <td class="text-right"><c:out value="${retentionDays}"/> days</td>
  </tr>
</table>
<p class="subheader">The nightly job scrubs PII from session records older than the retention window, and also prunes page-view history of the same age. Set the retention window on <a href="${ctx}/admin/configure-analytics">Analytics Settings</a> (Analytics data retention (days)) -- there's no editable field for it on this page. Use the button below to run the session scrub immediately.</p>
<form method="post" data-confirm-submit="Are you sure you want to purge PII now? This immediately scrubs visitor PII from session records older than the retention window and cannot be undone.">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="submit" class="button radius alert" value="Purge PII now" data-disable-on-submit="Purging..."/>
</form>
<%@include file="../confirm_submit.jspf" %>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>Sessions with retained visitor PII stays surprisingly high after lowering the retention window.</strong> The nightly job only catches up gradually as sessions age past the new, shorter window -- a lowered setting isn't retroactive on its own. Use <strong>Purge PII now</strong> to apply the new window immediately instead of waiting for the next 24 hours' worth of aging to catch up.</p>
  <p><strong>You need to change the audit log, form data, or funnel event retention window.</strong> As noted above, none of those have an admin editor yet -- that requires a direct database update to the relevant site property, not anything on this page.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>The nightly PII scrub (04:45) is distributed-lock protected, so on a multi-instance Azure App Service deployment exactly one instance runs it each night -- it will not run redundantly, or scrub more aggressively than intended, just because more instances are running.</p>
</div>
